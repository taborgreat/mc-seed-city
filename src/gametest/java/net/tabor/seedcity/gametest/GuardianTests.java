package net.tabor.seedcity.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.tabor.seedcity.SeedCity;
import net.tabor.seedcity.SeedCityBlocks;
import net.tabor.seedcity.cell.Placement;
import net.tabor.seedcity.config.SeedCityConfig;
import net.tabor.seedcity.core.CityManager;
import net.tabor.seedcity.core.CityState;
import net.tabor.seedcity.entity.SeedCityEntities;
import net.tabor.seedcity.entity.SentinelEntity;
import net.tabor.seedcity.verify.ProbeBlock;

import java.util.List;
import java.util.Optional;

/**
 * Phase 2 acceptance (design doc 25): a broken cell is repaired by its district's Warden within
 * three minutes; a generated city plants a Fault Cell, whose district has no Warden until a
 * player fixes the break; a Sentinel bound to a bus sleeps at 0 and turns hostile at 15.
 */
public final class GuardianTests {
	private static final String BOAT = "seedcity:boat";
	private static final int PLATFORM = 42;
	private static final BlockPos SEED = new BlockPos(24, 2, 24);
	private static final CityState.SlotKey WIRE = new CityState.SlotKey(1, 2);

	private static SeedCityConfig quietConfig() {
		SeedCityConfig c = new SeedCityConfig();
		c.unlimitedMaterials = true;   // not a supply test
		c.dreamAtStart = false;
		c.blocksPerSecond = 40;
		c.maxBuilders = 1;
		c.maxRadiusSlots = 0;           // no growth: the test lays the city out by hand
		c.faultsPerCity = 0;
		c.sentinelsOnRegisters = false;
		c.wardenSweepTicks = 40;
		c.citySeedOverride = 0x5EEDC17DL;
		return c;
	}

	/** A platform, a Seed rooted with the test's own config, and the three forced root cells adopted instantly. */
	private static CityState rootCity(GameTestHelper helper) {
		int start = SEED.getX() - PLATFORM / 2;
		for (int x = start; x < start + PLATFORM; x++) {
			for (int z = start; z < start + PLATFORM; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.SMOOTH_STONE);
				helper.setBlock(new BlockPos(x, 1, z), Blocks.SMOOTH_STONE);
			}
		}
		helper.setBlock(SEED, SeedCityBlocks.SEED);
		ServerLevel level = helper.getLevel();
		CityState c = CityManager.get(level).activate(level, helper.absolutePos(SEED), quietConfig()).orElseThrow(() -> new IllegalStateException("seed did not root a city"));
		c.adopt(level, new CityState.SlotKey(0, 0), CityState.CORE_CELL, Rotation.NONE, false);
		c.adopt(level, new CityState.SlotKey(0, 1), CityState.CLOCK_CELL, Rotation.NONE, false);
		c.adopt(level, new CityState.SlotKey(0, 2), CityState.JUNCTION_CELL, Rotation.NONE, false);
		return c;
	}

	/** World position of the wire's middle dust, the block a fault leaves out. */
	private static BlockPos wireMiddle(CityState c) {
		Placement p = c.placement(WIRE).orElseThrow();
		return p.origin().offset(new BlockPos(3, 1, 3).rotate(p.rotation()));
	}

	@GameTest(structure = BOAT, maxTicks = 4000)
	public void wardenRepairsBrokenCell(GameTestHelper helper) {
		CityState c = rootCity(helper);
		ServerLevel level = helper.getLevel();
		c.adopt(level, WIRE, SeedCity.id("wire_segment"), Rotation.COUNTERCLOCKWISE_90, false);
		BlockPos dust = wireMiddle(c);
		helper.assertTrue(level.getBlockState(dust).is(Blocks.REDSTONE_WIRE), "expected dust at " + dust + ", got " + level.getBlockState(dust));
		level.setBlock(dust, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);

		helper.onEachTick(() -> {
			if (helper.getTick() == 100) {
				helper.assertEntityPresent(SeedCityEntities.WARDEN);
			}
			if (helper.getTick() > 3600) {
				helper.fail("wire not repaired within 3 minutes: " + c.summary() + " " + c.slot(WIRE).map(Object::toString).orElse("?"));
			}
			boolean restored = level.getBlockState(dust).is(Blocks.REDSTONE_WIRE);
			boolean built = c.slot(WIRE).map(s -> s.status == CityState.SlotStatus.BUILT).orElse(false);
			if (restored && built && helper.getTick() > 20) {
				helper.succeed();
			}
		});
	}

	@GameTest(structure = BOAT, maxTicks = 3000)
	public void faultDarkensDistrictUntilFixed(GameTestHelper helper) {
		CityState c = rootCity(helper);
		ServerLevel level = helper.getLevel();
		CityState.Slot slot = c.adopt(level, WIRE, SeedCity.id("wire_segment"), Rotation.COUNTERCLOCKWISE_90, true);
		helper.assertTrue(slot.status == CityState.SlotStatus.FAULT, "adopted fault should be FAULT, was " + slot.status);
		BlockPos dust = wireMiddle(c);
		helper.assertTrue(level.getBlockState(dust).isAir(), "fault block should be missing at " + dust);
		String district = c.district(WIRE);

		helper.onEachTick(() -> {
			if (helper.getTick() == 100) {
				boolean posted = c.wardens(level).stream().anyMatch(w -> district.equals(w.district()));
				helper.assertFalse(posted, "a Warden was posted to a district with an open fault");
				helper.assertTrue(c.districtHasFault(district), "district should report its fault");
				// the player fixes the break
				level.setBlock(dust, Blocks.REDSTONE_WIRE.defaultBlockState(), Block.UPDATE_ALL);
			}
			if (helper.getTick() > 2800) {
				helper.fail("fixed fault never came back to life: " + c.summary() + " " + c.slot(WIRE).map(Object::toString).orElse("?"));
			}
			if (helper.getTick() > 100) {
				boolean built = c.slot(WIRE).map(s -> s.status == CityState.SlotStatus.BUILT).orElse(false);
				boolean posted = c.wardens(level).stream().anyMatch(w -> district.equals(w.district()));
				if (built && posted) {
					helper.succeed();
				}
			}
		});
	}

	@GameTest(structure = BOAT, maxTicks = 600)
	public void sentinelSleepsAndWakes(GameTestHelper helper) {
		CityState c = rootCity(helper);
		ServerLevel level = helper.getLevel();
		CityState.SlotKey bus = new CityState.SlotKey(0, -1);
		c.adopt(level, bus, SeedCity.id("bus_segment"), Rotation.NONE, false);
		Optional<SentinelEntity> spawned = c.spawnSentinel(level, bus, "out");
		helper.assertTrue(spawned.isPresent(), "sentinel did not spawn");
		SentinelEntity sentinel = spawned.get();
		Placement p = c.placement(bus).orElseThrow();
		Placement.WorldPort in = p.ports().stream().filter(wp -> wp.port().name().equals("in")).findFirst().orElseThrow();
		BlockPos probe = in.outside();
		level.setBlock(probe, SeedCityBlocks.PROBE.defaultBlockState()
				.setValue(ProbeBlock.POWER, 0).setValue(ProbeBlock.FACING, in.face().getOpposite()), Block.UPDATE_ALL);

		helper.onEachTick(() -> {
			if (helper.getTick() == 80) {
				helper.assertValueEqual(sentinel.alertness(), 0, "alertness with bus at 0");
				helper.assertFalse(sentinel.awake(), "sentinel should sleep at 0");
				level.setBlock(probe, level.getBlockState(probe).setValue(ProbeBlock.POWER, 15), Block.UPDATE_ALL);
			}
			if (helper.getTick() == 200) {
				helper.assertValueEqual(sentinel.alertness(), 15, "alertness with bus at 15");
				helper.assertTrue(sentinel.hostile(), "sentinel should be hostile at 15");
				helper.succeed();
			}
		});
	}

	@GameTest(structure = "seedcity:arena", maxTicks = 40)
	public void plannerPlantsAFault(GameTestHelper helper) {
		SeedCityConfig cfg = new SeedCityConfig();
		cfg.unlimitedMaterials = true;   // not a supply test
		cfg.dreamAtStart = false;
		cfg.faultsPerCity = 1;
		cfg.faultAfterLiveCells = 3;
		List<String> plan = CityState.previewPlan(0xC0FFEEL, 30, cfg);
		long faults = plan.stream().filter(s -> s.contains("[fault]")).count();
		helper.assertValueEqual((int) faults, 1, "planted faults in " + plan);
		helper.succeed();
	}
}
