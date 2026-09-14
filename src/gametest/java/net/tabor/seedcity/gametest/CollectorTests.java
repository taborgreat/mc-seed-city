package net.tabor.seedcity.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.tabor.seedcity.SeedCity;
import net.tabor.seedcity.SeedCityBlocks;
import net.tabor.seedcity.config.SeedCityConfig;
import net.tabor.seedcity.core.CityManager;
import net.tabor.seedcity.core.CityState;
import net.tabor.seedcity.core.Terrain;
import net.tabor.seedcity.entity.SeedCityEntities;

/**
 * Phase 5 acceptance (design doc 25): Collectors visibly deplete a nearby forest. A city with no
 * wood sends a Collector to a grove; logs disappear and the ledger fills. A grove behind a
 * warding ring is left alone.
 */
public final class CollectorTests {
	private static final String BOAT = "seedcity:boat";
	private static final BlockPos SEED = new BlockPos(24, 2, 24);

	private static SeedCityConfig config() {
		SeedCityConfig c = new SeedCityConfig();
		c.dreamAtStart = false;
		c.blocksPerSecond = 40;
		c.maxBuilders = 1;
		c.maxRadiusSlots = 1;
		c.faultsPerCity = 0;
		c.sentinelsOnRegisters = false;
		c.unlimitedMaterials = false;
		c.initialWood = 0;
		c.initialStone = 20000;
		c.initialRedstone = 4000;
		c.reserveWood = 100;
		c.collectorTicksPerBlock = 5;
		c.collectorLoad = 4;
		c.citySeedOverride = 0x5EEDC17DL;
		return c;
	}

	private static void platform(GameTestHelper helper) {
		for (int x = 2; x < 46; x++) {
			for (int z = 2; z < 46; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.SMOOTH_STONE);
				helper.setBlock(new BlockPos(x, 1, z), Blocks.SMOOTH_STONE);
			}
		}
		helper.setBlock(SEED, SeedCityBlocks.SEED);
	}

	private static int grove(GameTestHelper helper, int x0, int z0) {
		int logs = 0;
		for (int x = x0; x < x0 + 6; x += 2) {
			for (int z = z0; z < z0 + 6; z += 2) {
				for (int y = 2; y <= 4; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.OAK_LOG);
					logs++;
				}
			}
		}
		return logs;
	}

	private static int logsLeft(GameTestHelper helper, int x0, int z0) {
		int n = 0;
		for (int x = x0; x < x0 + 6; x++) {
			for (int z = z0; z < z0 + 6; z++) {
				for (int y = 2; y <= 4; y++) {
					if (helper.getBlockState(new BlockPos(x, y, z)).is(Blocks.OAK_LOG)) {
						n++;
					}
				}
			}
		}
		return n;
	}

	@GameTest(structure = BOAT, maxTicks = 4000)
	public void collectorsDepleteAGrove(GameTestHelper helper) {
		platform(helper);
		int logs = grove(helper, 38, 20);
		ServerLevel level = helper.getLevel();
		CityState c = CityManager.get(level).activate(level, helper.absolutePos(SEED), config()).orElseThrow();
		helper.assertTrue(c.deficit().orElse(null) == Terrain.Kind.WOOD, "a city with no wood should want wood");
		helper.onEachTick(() -> {
			if (helper.getTick() % 400 == 0) {
				SeedCity.LOGGER.info("collector test tick {}: {} logs left {} collectors {}", helper.getTick(), c.summary(), logsLeft(helper, 38, 20),
						c.collectors(level).stream().map(e -> e.status()).toList());
			}
			if (helper.getTick() == 200) {
				helper.assertTrue(!c.collectors(level).isEmpty(), "a Collector should have been sent out: " + c.summary() + " deficit=" + c.deficit());
			}
			int left = logsLeft(helper, 38, 20);
			if (left < logs && c.stock(Terrain.Kind.WOOD) > 0) {
				SeedCity.LOGGER.info("collector test: {} of {} logs taken, wood={}", logs - left, logs, c.stock(Terrain.Kind.WOOD));
				helper.succeed();
			}
			if (helper.getTick() > 3800) {
				helper.fail("no wood gathered: logs left " + left + " of " + logs + ", " + c.summary());
			}
		});
	}

	@GameTest(structure = BOAT, maxTicks = 1200)
	public void wardingKeepsCollectorsOut(GameTestHelper helper) {
		platform(helper);
		grove(helper, 38, 20);
		// a closed lodestone ring around the city: nothing outside it (this grove, or a neighbouring test's trees) may be offered
		for (int i = 10; i <= 38; i++) {
			helper.setBlock(new BlockPos(i, 2, 10), Blocks.LODESTONE);
			helper.setBlock(new BlockPos(i, 2, 38), Blocks.LODESTONE);
			helper.setBlock(new BlockPos(10, 2, i), Blocks.LODESTONE);
			helper.setBlock(new BlockPos(38, 2, i), Blocks.LODESTONE);
		}
		ServerLevel level = helper.getLevel();
		CityState c = CityManager.get(level).activate(level, helper.absolutePos(SEED), config()).orElseThrow();
		helper.runAfterDelay(100, () -> {
			var order = c.claimGather(level, java.util.UUID.randomUUID(), helper.absolutePos(SEED));
			helper.assertTrue(order.isEmpty(), "the warded grove must not be offered: " + order);
			helper.succeed();
		});
	}
}
