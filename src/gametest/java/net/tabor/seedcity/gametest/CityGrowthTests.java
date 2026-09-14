package net.tabor.seedcity.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.tabor.seedcity.SeedCity;
import net.tabor.seedcity.SeedCityBlocks;
import net.tabor.seedcity.config.SeedCityConfig;
import net.tabor.seedcity.core.CityManager;
import net.tabor.seedcity.core.CityState;
import net.tabor.seedcity.entity.SeedCityEntities;

import java.util.List;
import java.util.Optional;

/**
 * Phase 1 acceptance (design doc 25): a Seed on a platform spawns a Builder, gets enclosed in a
 * Core, and the platform fills with verified cells; the clock pulses and an actuator is driven;
 * the layout is a function of the seed alone.
 *
 * <p>Rates are raised for the test so it finishes in a minute or two instead of twenty.
 */
public final class CityGrowthTests {
	private static final String BOAT = "seedcity:boat";
	private static final int PLATFORM = 42;
	private static final BlockPos SEED = new BlockPos(24, 2, 24);

	private static SeedCityConfig fastConfig() {
		SeedCityConfig c = new SeedCityConfig();
		c.dreamAtStart = false;
		c.blocksPerSecond = 40;
		c.maxBuilders = 3;
		c.cellsPerBuilder = 2;
		c.builderSpeed = 1.6;
		c.maxRadiusSlots = 3;
		c.citySeedOverride = 0x5EEDC17DL;   // same layout every run
		return c;
	}

	private static void buildPlatform(GameTestHelper helper) {
		int start = SEED.getX() - PLATFORM / 2;
		for (int x = start; x < start + PLATFORM; x++) {
			for (int z = start; z < start + PLATFORM; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.SMOOTH_STONE);
				helper.setBlock(new BlockPos(x, 1, z), Blocks.SMOOTH_STONE);
			}
		}
	}

	@GameTest(structure = BOAT, maxTicks = 9000)
	public void seedGrowsCityBoat(GameTestHelper helper) {
		buildPlatform(helper);
		// an unpowered Seed, rooted through the API so the city gets the test's own config
		helper.setBlock(SEED, SeedCityBlocks.SEED);
		BlockPos seedAbs = helper.absolutePos(SEED);
		CityManager.get(helper.getLevel()).activate(helper.getLevel(), seedAbs, fastConfig());

		helper.onEachTick(() -> {
			Optional<CityState> city = CityManager.get(helper.getLevel()).city(seedAbs);
			if (city.isEmpty()) {
				if (helper.getTick() > 40) {
					helper.fail("powered Seed did not root a city");
				}
				return;
			}
			CityState c = city.get();
			if (helper.getTick() == 60) {
				helper.assertEntityPresent(SeedCityEntities.BUILDER);
			}
			if (helper.getTick() == 2400) {
				boolean coreBuilt = c.slot(new CityState.SlotKey(0, 0)).map(s -> s.status == CityState.SlotStatus.BUILT).orElse(false);
				if (!coreBuilt) {
					helper.fail("core not built within 2 minutes: " + c.summary() + " " + c.describeSlots());
				}
			}
			boolean coreBuilt = c.slot(new CityState.SlotKey(0, 0)).map(s -> s.status == CityState.SlotStatus.BUILT).orElse(false);
			boolean clockBuilt = c.slot(new CityState.SlotKey(0, 1)).map(s -> s.status == CityState.SlotStatus.BUILT).orElse(false);
			if (helper.getTick() % 400 == 0) {
				SeedCity.LOGGER.info("city boat test tick {}: {}", helper.getTick(), c.summary());
			}
			if (helper.getTick() == 8800) {
				SeedCity.LOGGER.warn("city boat test about to time out; clocked slots: {}", c.clockedSlots(true));
				for (String line : c.describeSlots()) {
					SeedCity.LOGGER.warn("  {}", line);
				}
			}
			if (coreBuilt && clockBuilt && c.builtCount() >= 9 && c.hasClockedActuatorBuilt()) {
				SeedCity.LOGGER.info("city boat test done at tick {}: {}", helper.getTick(), c.summary());
				SeedCity.LOGGER.info("  clocked slots: {}", c.clockedSlots(true));
				for (String line : c.describeSlots()) {
					SeedCity.LOGGER.info("  {}", line);
				}
				helper.succeed();
			}
		});
	}

	/** Same city seed, same plan; the layout never depends on who built what when. */
	@GameTest(structure = "seedcity:arena", maxTicks = 40)
	public void planningIsDeterministic(GameTestHelper helper) {
		SeedCityConfig cfg = new SeedCityConfig();
		cfg.dreamAtStart = false;
		List<String> a = CityState.previewPlan(0xC0FFEEL, 30, cfg);
		List<String> b = CityState.previewPlan(0xC0FFEEL, 30, cfg);
		List<String> other = CityState.previewPlan(0xBEEFL, 30, cfg);
		helper.assertTrue(a.size() >= 20, "planner stopped early: " + a);
		helper.assertTrue(a.equals(b), "same seed produced different plans:\n" + a + "\n" + b);
		helper.assertTrue(!a.equals(other), "different seeds produced the same plan");
		helper.succeed();
	}
}
