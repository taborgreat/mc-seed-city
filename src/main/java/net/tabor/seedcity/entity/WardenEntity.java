package net.tabor.seedcity.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.tabor.seedcity.SeedCity;
import net.tabor.seedcity.build.BuildTask;
import net.tabor.seedcity.cell.Placement;
import net.tabor.seedcity.config.SeedCityConfig;
import net.tabor.seedcity.core.CityManager;
import net.tabor.seedcity.core.CityState;
import net.tabor.seedcity.verify.Integrity;

import java.util.List;
import java.util.Optional;

/**
 * The Warden (design doc 6, 24): assigned to a district, patrols its cells, sweeps them against
 * their blueprints every few seconds, and rebuilds whatever differs, which reverts player edits.
 * Repairs go through the same BuildTask as construction. Kill it and the district stays broken
 * until the Core sends another, five minutes later.
 */
public final class WardenEntity extends PathfinderMob {
	public static final double WALK_SPEED = 0.6;
	private BlockPos cityPos;
	private static final EntityDataAccessor<Boolean> REPAIRING = SynchedEntityData.defineId(WardenEntity.class, EntityDataSerializers.BOOLEAN);
	private enum Phase { PATROL, TO_SITE, REPAIR }

	private String district = "core";
	private Phase phase = Phase.PATROL;
	private BuildTask task;
	private Vec3 target;
	private int timer;
	private int sweepTimer;
	private int travelTicks;

	public WardenEntity(EntityType<? extends WardenEntity> type, Level level) {
		super(type, level);
		setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		// Vanilla iron-golem movement attribute, without its class or protection goals.
		return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 40.0)
				.add(Attributes.MOVEMENT_SPEED, 0.25).add(Attributes.FOLLOW_RANGE, 48.0);
	}

	@Override protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, WALK_SPEED) {
			@Override public boolean canUse() { return cityPos == null && super.canUse(); }
			@Override public boolean canContinueToUse() { return cityPos == null && super.canContinueToUse(); }
		});
		goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8));
		goalSelector.addGoal(8, new RandomLookAroundGoal(this));
	}

	public BlockPos cityPos() { return cityPos; }
	private Optional<CityState> city(ServerLevel level) {
		return cityPos == null ? Optional.empty() : CityManager.get(level).city(cityPos);
	}
	@Override public boolean removeWhenFarAway(double distance) { return false; }

	/** City-spawned repair workers need floor space and clearance for their full height. */
	public static Optional<BlockPos> groundSpawn(ServerLevel level, BlockPos seed) {
		// Start outside the seven-block Core; its decorative pillars can trap a wide mob.
		for (int radius=4; radius<=8; radius++) {
			for (int x=-radius; x<=radius; x++) for (int z=-radius; z<=radius; z++) {
				if (Math.max(Math.abs(x),Math.abs(z)) != radius) continue;
				for (int y=1; y>=-2; y--) {
					BlockPos feet=seed.offset(x,y,z);
					if (!level.hasChunkAt(feet) || !level.getBlockState(feet.below()).isFaceSturdy(level,feet.below(),Direction.UP)) continue;
					var box=new net.minecraft.world.phys.AABB(feet.getX()-.2,feet.getY(),feet.getZ()-.2,
							feet.getX()+1.2,feet.getY()+2.95,feet.getZ()+1.2);
					if (level.getFluidState(feet).isEmpty() && level.noCollision(box)) return Optional.of(feet);
				}
			}
		}
		return Optional.empty();
	}

	/** Walk to a supported, reachable spot near the work, never an aerial waypoint. */
	private boolean walkToward(Vec3 destination, double reach) {
		if (position().distanceTo(destination) <= reach) {
			getNavigation().stop();
			return true;
		}
		if (tickCount % 20 != 0) return false;
		var candidates = new java.util.ArrayList<Vec3>();
		BlockPos center = BlockPos.containing(destination);
		for (int x=-4; x<=4; x++) for (int z=-4; z<=4; z++) for (int y=-5; y<=2; y++) {
			BlockPos feet = center.offset(x,y,z);
			if (!level().getBlockState(feet.below()).isFaceSturdy(level(), feet.below(), Direction.UP)) continue;
			Vec3 spot = Vec3.atBottomCenterOf(feet);
			if (spot.distanceTo(destination) > reach) continue;
			if (!level().getFluidState(feet).isEmpty() || !level().noCollision(this, getBoundingBox().move(spot.subtract(position())))) continue;
			candidates.add(spot);
		}
		candidates.sort(java.util.Comparator.comparingDouble(position()::distanceToSqr));
		for (Vec3 spot : candidates) {
			var path = getNavigation().createPath(BlockPos.containing(spot), 0);
			if (path != null && path.canReach()) {
				getNavigation().moveTo(path, WALK_SPEED);
				break;
			}
		}
		return false;
	}

	public void assign(BlockPos city, String district) {
		this.cityPos = city;
		this.district = district;
	}

	public String district() {
		return district;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(REPAIRING, false);
	}

	public boolean isRepairing() { return entityData.get(REPAIRING); }

	public String status() {
		return district + " " + phase + (task == null ? "" : " " + task.placement() + " " + (int) (task.progress() * 100) + "%");
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		if (cityPos != null) output.store("City", BlockPos.CODEC, cityPos);
		output.putString("District", district);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		cityPos = input.read("City", BlockPos.CODEC).orElse(null);
		// Migrate already-saved flying Rectifiers, including the first showcase save.
		setNoGravity(false);
		setDeltaMovement(Vec3.ZERO);
		getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.25);
		getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(48.0);
		district = input.getStringOr("District", "core");
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (level() instanceof ServerLevel server) {
			city(server).ifPresent(c -> c.noteWardenDeath(district, server.getGameTime()));
		}
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		try {
			work(level);
		} catch (Exception e) {
			SeedCity.LOGGER.error("Warden {} ({}) failed while {}; resuming patrol", getUUID(), district, phase, e);
			task = null;
			phase = Phase.PATROL;
		}
		entityData.set(REPAIRING, task != null && phase == Phase.REPAIR && city(level).isPresent());
	}

	private void work(ServerLevel level) {
		Optional<CityState> city = city(level);
		if (city.isEmpty()) {
			return;
		}
		CityState c = city.get();
		SeedCityConfig cfg = c.cfg();
		switch (phase) {
			case PATROL -> {
				if (++sweepTimer >= cfg.wardenSweepTicks) {
					sweepTimer = 0;
					if (sweep(level, c)) {
						return;
					}
				}
				if (target == null || --timer <= 0 || position().distanceTo(target) < 2.5) {
					List<CityState.Slot> cells = c.districtSlots(district);
					if (cells.isEmpty()) {
						target = Vec3.atCenterOf(c.seedPos());
					} else {
						CityState.Slot pick = cells.get(random.nextInt(cells.size()));
						target = Vec3.atCenterOf(c.slotOrigin(pick.key).offset(3, 0, 3));
					}
					timer = 200 + random.nextInt(200);
				}
				walkToward(target, 2.5);
			}
			case TO_SITE -> {
				travelTicks++;
				if (walkToward(target, 4.0)) {
					phase = Phase.REPAIR;
					timer = 0;
					travelTicks = 0;
				} else if (travelTicks > cfg.abandonSeconds * 20) {
					task = null;
					phase = Phase.PATROL;
				}
			}
			case REPAIR -> repair(level, c, cfg);
		}
	}

	/** Looks for the first damaged cell in the district and takes it on. True when a repair began. */
	private boolean sweep(ServerLevel level, CityState c) {
		for (CityState.Slot s : c.districtSlots(district)) {
			Optional<Placement> p = c.placement(s);
			if (p.isEmpty()) {
				continue;
			}
			List<BlockPos> damaged = Integrity.damaged(level, p.get());
			if (damaged.isEmpty()) {
				continue;
			}
			SeedCity.LOGGER.info("Warden ({}) found {} damaged block(s) in {} at {}", district, damaged.size(), s.cell, s.key);
			task = BuildTask.repair(p.get(), s.key);
			target = Vec3.atCenterOf(damaged.getFirst());
			travelTicks = 0;
			phase = Phase.TO_SITE;
			return true;
		}
		return false;
	}

	private void repair(ServerLevel level, CityState c, SeedCityConfig cfg) {
		BlockPos next = task.nextPos();
		if (!walkToward(Vec3.atCenterOf(next), 4.0)) {
			if (++travelTicks > cfg.abandonSeconds * 20) {
				task = null;
				phase = Phase.PATROL;
			}
			return;
		}
		travelTicks = 0;
		getLookControl().setLookAt(next.getX() + 0.5, next.getY() + 0.5, next.getZ() + 0.5);
		if (++timer < cfg.ticksPerBlock()) {
			return;
		}
		timer = 0;
		if (task.step(level)) {
			BuildTask done = task;
			task = null;
			c.onRepaired(done);
			CityManager.get(level).touch();
			phase = Phase.PATROL;
			sweepTimer = cfg.wardenSweepTicks / 2;
		}
	}
}
