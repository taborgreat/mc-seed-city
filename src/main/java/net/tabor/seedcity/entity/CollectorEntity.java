package net.tabor.seedcity.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.tabor.seedcity.SeedCity;
import net.tabor.seedcity.config.SeedCityConfig;
import net.tabor.seedcity.core.CityManager;
import net.tabor.seedcity.core.CityState;
import net.tabor.seedcity.core.Terrain;

import java.util.Optional;

/**
 * The Collector (design doc 6, 20.1, 24): the only mob that routinely leaves the city. Takes a
 * gather order from the Core (redstone, stone or wood), walks to the nearest permitted source,
 * mines a few blocks of it, hauls the load back to the nearest Storage Cell and credits the
 * ledger. The city visibly consumes its surroundings; starve it by fencing them off.
 */
public final class CollectorEntity extends PathfinderMob {
	private static final EntityDataAccessor<Boolean> MINING = SynchedEntityData.defineId(CollectorEntity.class, EntityDataSerializers.BOOLEAN);
	private enum Phase { IDLE, TO_SOURCE, MINE, RETURN }

	private BlockPos cityPos;
	private Phase phase = Phase.IDLE;
	private Terrain.Kind kind;
	private BlockPos target;
	private Vec3 depot;
	private int carried;
	private int blocksThisTrip;
	private int timer;
	private int travelTicks;

	public CollectorEntity(EntityType<? extends CollectorEntity> type, Level level) {
		super(type, level);
		setPersistenceRequired();
		setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
		setDropChance(EquipmentSlot.MAINHAND, 0);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 30.0)
				.add(Attributes.MOVEMENT_SPEED, 0.28)
				.add(Attributes.FOLLOW_RANGE, 32.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(MINING, false);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0F) {
			@Override
			public boolean canUse() {
				return phase == Phase.IDLE && super.canUse();
			}
		});
		goalSelector.addGoal(7, new RandomLookAroundGoal(this) {
			@Override
			public boolean canUse() {
				return phase == Phase.IDLE && super.canUse();
			}
		});
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}

	public BlockPos cityPos() {
		return cityPos;
	}

	public void setCity(BlockPos pos) {
		this.cityPos = pos;
	}

	public boolean idle() {
		return phase == Phase.IDLE;
	}

	/** Presentation only: the client swings the tool while this is set. */
	public boolean isMining() {
		return entityData.get(MINING);
	}

	public String status() {
		return "collector " + phase + (kind == null ? "" : " " + kind.name) + (target == null ? "" : " @" + target.toShortString()) + " carrying " + carried;
	}

	private Optional<CityState> city(ServerLevel level) {
		return cityPos == null ? Optional.empty() : CityManager.get(level).city(cityPos);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		try {
			work(level);
		} catch (Exception e) {
			SeedCity.LOGGER.error("Collector {} failed while {}", getUUID(), phase, e);
			giveUp(level);
		}
		entityData.set(MINING, phase == Phase.MINE);
	}

	private void work(ServerLevel level) {
		Optional<CityState> city = city(level);
		if (city.isEmpty()) {
			return;
		}
		CityState c = city.get();
		SeedCityConfig cfg = c.cfg();
		switch (phase) {
			case IDLE -> {
				if (++timer < 40) {
					return;
				}
				timer = 0;
				Optional<CityState.GatherOrder> order = c.claimGather(level, getUUID(), blockPosition());
				if (order.isEmpty()) {
					return;
				}
				kind = order.get().kind();
				target = order.get().source();
				blocksThisTrip = 0;
				travelTicks = 0;
				phase = Phase.TO_SOURCE;
			}
			case TO_SOURCE -> {
				if (walkToward(Vec3.atCenterOf(target), 1.0, 3.2)) {
					phase = Phase.MINE;
					timer = 0;
				} else if (++travelTicks > cfg.abandonSeconds * 20) {
					SeedCity.LOGGER.info("Collector {}: cannot reach {} at {}; giving up", getUUID(), kind.name, target.toShortString());
					c.forbidSource(target);
					giveUp(level);
				}
			}
			case MINE -> mine(level, c, cfg);
			case RETURN -> {
				if (walkToward(depot, 1.0, 5.0)) {
					c.deposit(kind, carried);
					level.playSound(null, getX(), getY(), getZ(), net.minecraft.sounds.SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.6F, 0.8F);
					carried = 0;
					c.releaseGather(getUUID());
					phase = Phase.IDLE;
					timer = 20;
				} else if (++travelTicks > cfg.abandonSeconds * 40) {
					// stuck on the way home: keep the load, deposit from wherever we are
					c.deposit(kind, carried);
					carried = 0;
					c.releaseGather(getUUID());
					phase = Phase.IDLE;
				}
			}
		}
	}

	private void mine(ServerLevel level, CityState c, SeedCityConfig cfg) {
		BlockState s = level.getBlockState(target);
		if (!kind.matches(s)) {
			level.destroyBlockProgress(getId(), target, -1);
			nextBlockOrHome(level, c, cfg);
			return;
		}
		if (position().distanceTo(Vec3.atCenterOf(target)) > 4.5) {
			timer = 0;
			travelTicks = 0;
			phase = Phase.TO_SOURCE;
			return;
		}
		getNavigation().stop();
		getLookControl().setLookAt(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
		if (!getMainHandItem().is(kind == Terrain.Kind.WOOD ? Items.IRON_AXE : Items.IRON_PICKAXE)) {
			setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(kind == Terrain.Kind.WOOD ? Items.IRON_AXE : Items.IRON_PICKAXE));
		}
		int per = Math.max(1, cfg.collectorTicksPerBlock);
		if (timer % 6 == 0) {
			swing(InteractionHand.MAIN_HAND);
		}
		level.destroyBlockProgress(getId(), target, Math.min(9, timer * 10 / per));
		if (++timer < per) {
			return;
		}
		timer = 0;
		level.destroyBlockProgress(getId(), target, -1);
		level.destroyBlock(target, false);
		carried += kind.yield;
		blocksThisTrip++;
		nextBlockOrHome(level, c, cfg);
	}

	private void nextBlockOrHome(ServerLevel level, CityState c, SeedCityConfig cfg) {
		if (blocksThisTrip < cfg.collectorLoad) {
			Optional<BlockPos> more = Terrain.nearbySource(level, target, kind, 3, c::sourceForbidden);
			if (more.isPresent()) {
				target = more.get();
				return;
			}
		}
		if (carried == 0) {
			c.forbidSource(target);
			giveUp(level);
			return;
		}
		depot = c.depositPoint(blockPosition());
		travelTicks = 0;
		phase = Phase.RETURN;
	}

	private void giveUp(ServerLevel level) {
		if (target != null) {
			level.destroyBlockProgress(getId(), target, -1);
		}
		city(level).ifPresent(c -> c.releaseGather(getUUID()));
		if (carried > 0 && kind != null) {
			city(level).ifPresent(c -> c.deposit(kind, carried));
			carried = 0;
		}
		getNavigation().stop();
		target = null;
		phase = Phase.IDLE;
		timer = 0;
	}

	/** Re-issues ground navigation toward a point every few ticks; true when within {@code reach}. */
	private boolean walkToward(Vec3 to, double speed, double reach) {
		if (position().distanceTo(to) < reach) {
			getNavigation().stop();
			return true;
		}
		if (tickCount % 10 == 0) {
			getNavigation().moveTo(to.x, to.y, to.z, speed);
		}
		return false;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		if (cityPos != null) {
			output.store("City", BlockPos.CODEC, cityPos);
		}
		output.putInt("Carried", carried);
		if (kind != null) {
			output.putString("Kind", kind.name());
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		cityPos = input.read("City", BlockPos.CODEC).orElse(null);
		carried = input.getIntOr("Carried", 0);
		String k = input.getStringOr("Kind", "");
		kind = k.isEmpty() ? null : Terrain.Kind.valueOf(k);
		// whatever it was doing, a reloaded Collector starts over; a carried load goes home first
		phase = Phase.IDLE;
		if (carried > 0 && kind != null) {
			phase = Phase.RETURN;
		}
	}
}
