package net.tabor.seedcity.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
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
public final class WardenEntity extends FlyingCityMob {
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
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createFlyingAttributes(40.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(REPAIRING, false);
	}

	/** Presentation only: the client swings the hammer while this is set. */
	public boolean isRepairing() {
		return entityData.get(REPAIRING);
	}

	public void assign(BlockPos city, String district) {
		this.cityPos = city;
		this.district = district;
	}

	public String district() {
		return district;
	}

	public String status() {
		return district + " " + phase + (task == null ? "" : " " + task.placement() + " " + (int) (task.progress() * 100) + "%");
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putString("District", district);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
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
		entityData.set(REPAIRING, task != null && phase == Phase.REPAIR);
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
						target = Vec3.atCenterOf(c.seedPos().above(4));
					} else {
						CityState.Slot pick = cells.get(random.nextInt(cells.size()));
						target = Vec3.atCenterOf(c.slotOrigin(pick.key).offset(3, 6, 3));
					}
					timer = 200 + random.nextInt(200);
				}
				flyToward(target, 0.8, 2.5);
			}
			case TO_SITE -> {
				travelTicks++;
				if (flyToward(target, cfg.builderSpeed, 2.5)) {
					phase = Phase.REPAIR;
					timer = 0;
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
			target = Vec3.atCenterOf(damaged.getFirst().above(2));
			travelTicks = 0;
			phase = Phase.TO_SITE;
			return true;
		}
		return false;
	}

	private void repair(ServerLevel level, CityState c, SeedCityConfig cfg) {
		BlockPos next = task.nextPos();
		Vec3 stand = hoverAbove(next, task.placement().footprint().maxY() + 2);
		if (position().distanceTo(stand) > 4.0) {
			flyToward(stand, cfg.builderSpeed, 4.0);
		} else {
			getNavigation().stop();
		}
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
