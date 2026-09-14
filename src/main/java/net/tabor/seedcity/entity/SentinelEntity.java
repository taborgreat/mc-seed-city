package net.tabor.seedcity.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.tabor.seedcity.cell.Placement;
import net.tabor.seedcity.core.CityManager;
import net.tabor.seedcity.core.CityState;

import java.util.Optional;

/**
 * The Sentinel (design doc 6, 24): bound to one port of one cell. Reads it every tick into an
 * alertness of 0-15. At 0 it sleeps; at 15 it hunts players; in between it wanders its post and
 * stares. A living gauge of a wire. Not replaced if killed.
 */
public final class SentinelEntity extends PathfinderMob {
	public static final int HOSTILE_AT = 15;
	/** Synced so the client can light the eyes by alertness. */
	private static final EntityDataAccessor<Integer> ALERTNESS = SynchedEntityData.defineId(SentinelEntity.class, EntityDataSerializers.INT);

	private BlockPos cityPos;
	private CityState.SlotKey slot;
	private String port = "out";
	private BlockPos post;

	public SentinelEntity(EntityType<? extends SentinelEntity> type, Level level) {
		super(type, level);
		setPersistenceRequired();
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(ALERTNESS, 0);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 30.0)
				.add(Attributes.MOVEMENT_SPEED, 0.3)
				.add(Attributes.ATTACK_DAMAGE, 4.0)
				.add(Attributes.FOLLOW_RANGE, 24.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.5);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.1, true) {
			@Override
			public boolean canUse() {
				return hostile() && super.canUse();
			}

			@Override
			public boolean canContinueToUse() {
				return hostile() && super.canContinueToUse();
			}
		});
		goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6) {
			@Override
			public boolean canUse() {
				return awake() && !hostile() && super.canUse();
			}
		});
		goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12.0F) {
			@Override
			public boolean canUse() {
				return awake() && super.canUse();
			}
		});
		goalSelector.addGoal(7, new RandomLookAroundGoal(this) {
			@Override
			public boolean canUse() {
				return awake() && super.canUse();
			}
		});
		targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true) {
			@Override
			public boolean canUse() {
				return hostile() && super.canUse();
			}

			@Override
			public boolean canContinueToUse() {
				return hostile() && super.canContinueToUse();
			}
		});
	}

	public void bind(BlockPos city, CityState.SlotKey slot, String port, BlockPos post) {
		this.cityPos = city;
		this.slot = slot;
		this.port = port;
		this.post = post;
		setHomeTo(post, 6);
	}

	public BlockPos cityPos() {
		return cityPos;
	}

	public int alertness() {
		return entityData.get(ALERTNESS);
	}

	public boolean awake() {
		return alertness() > 0;
	}

	public boolean hostile() {
		return alertness() >= HOSTILE_AT;
	}

	public String status() {
		return "sentinel@" + (slot == null ? "?" : slot.toString()) + "." + port + " alertness=" + alertness() + (hostile() ? " HOSTILE" : awake() ? " awake" : " asleep");
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		int read = readPort(level);
		if (read >= 0) {
			entityData.set(ALERTNESS, Math.clamp(read, 0, 15));
		}
		if (!hostile() && getTarget() != null) {
			setTarget(null);
		}
		// hostile: jogs (the model reads sprinting); otherwise walks
		setSprinting(hostile());
		// asleep: crouch and stare at the floor; awake: stand
		setShiftKeyDown(!awake());
		if (!awake()) {
			getNavigation().stop();
			setXRot(45.0F);
		}
	}

	private int readPort(ServerLevel level) {
		if (cityPos == null || slot == null) {
			return -1;
		}
		Optional<CityState> city = CityManager.get(level).city(cityPos);
		if (city.isEmpty()) {
			return -1;
		}
		Optional<Placement> p = city.get().placement(slot);
		if (p.isEmpty()) {
			return -1;
		}
		for (Placement.WorldPort wp : p.get().ports()) {
			if (wp.port().name().equals(port)) {
				return level.getSignal(wp.pos(), wp.face().getOpposite());
			}
		}
		return -1;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		if (cityPos != null) {
			output.store("City", BlockPos.CODEC, cityPos);
		}
		if (slot != null) {
			output.putInt("SlotX", slot.x());
			output.putInt("SlotZ", slot.z());
		}
		output.putString("Port", port);
		output.putInt("Alertness", alertness());
		if (post != null) {
			output.store("Post", BlockPos.CODEC, post);
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		cityPos = input.read("City", BlockPos.CODEC).orElse(null);
		if (input.getIntOr("SlotX", Integer.MIN_VALUE) != Integer.MIN_VALUE) {
			slot = new CityState.SlotKey(input.getIntOr("SlotX", 0), input.getIntOr("SlotZ", 0));
		}
		port = input.getStringOr("Port", "out");
		entityData.set(ALERTNESS, Math.clamp(input.getIntOr("Alertness", 0), 0, 15));
		post = input.read("Post", BlockPos.CODEC).orElse(null);
		if (post != null) {
			setHomeTo(post, 6);
		}
	}
}
