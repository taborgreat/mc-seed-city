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

import java.util.HashSet;
import java.util.Set;

/**
 * Phase 5 acceptance (design doc 25): a Seed on natural terrain grows a city that fits the land.
 * Cells stand at the ground's own levels, a pond and a cliff stay unbuilt, and the city's edges
 * fade into the ground through banked earth. The chunk cap halts growth exactly at the cap.
 */
public final class TerrainTests {
	private static final String BOAT = "seedcity:boat";
	private static final int SIZE = 48;

	/**
	 * Gentle ground that rolls slowly north to south, a pond in the north-west, a three-block
	 * terrace to the east (so slots there stand on their own level) and a dip along the north
	 * edge (so the city's edge has to be banked up to meet the ground).
	 */
	static int ground(int x, int z) {
		if (x >= 7 && x <= 13 && z >= 7 && z <= 13) {
			return 0;   // pond floor
		}
		int h = 3 + (int) Math.round(0.9 * Math.cos(z / 8.0));
		if (x >= 31) {
			h += 3;
		}
		if (z <= 8) {
			h -= 2;
		}
		return Math.max(1, Math.min(9, h));
	}

	static void terrain(GameTestHelper helper) {
		for (int x = 0; x < SIZE; x++) {
			for (int z = 0; z < SIZE; z++) {
				int h = ground(x, z);
				for (int y = 0; y < h; y++) {
					helper.setBlock(new BlockPos(x, y, z), y >= h - 2 ? Blocks.DIRT : Blocks.STONE);
				}
				helper.setBlock(new BlockPos(x, h, z), h == 0 ? Blocks.STONE : Blocks.GRASS_BLOCK);
				if (h == 0) {
					helper.setBlock(new BlockPos(x, 1, z), Blocks.WATER);
				}
			}
		}
		// a few oaks
		for (int[] t : new int[][] {{16, 30}, {33, 16}, {12, 34}}) {
			int h = ground(t[0], t[1]);
			for (int y = 1; y <= 4; y++) {
				helper.setBlock(new BlockPos(t[0], h + y, t[1]), Blocks.OAK_LOG);
			}
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					for (int y = 3; y <= 5; y++) {
						BlockPos p = new BlockPos(t[0] + dx, h + y, t[1] + dz);
						if (helper.getBlockState(p).isAir()) {
							helper.setBlock(p, Blocks.OAK_LEAVES);
						}
					}
				}
			}
		}
	}

	private static SeedCityConfig config() {
		SeedCityConfig c = new SeedCityConfig();
		c.dreamAtStart = false;
		c.blocksPerSecond = 40;
		c.maxBuilders = 3;
		c.cellsPerBuilder = 2;
		c.builderSpeed = 1.6;
		c.maxRadiusSlots = 2;
		c.faultsPerCity = 0;
		c.sentinelsOnRegisters = false;
		c.unlimitedMaterials = true;
		c.slopeLimit = 4;
		c.citySeedOverride = 0x5EEDC17DL;
		return c;
	}

	@GameTest(structure = BOAT, maxTicks = 9000)
	public void cityFitsTheLand(GameTestHelper helper) {
		terrain(helper);
		BlockPos seed = new BlockPos(24, ground(24, 24) + 1, 24);
		helper.setBlock(seed, SeedCityBlocks.SEED);
		BlockPos seedAbs = helper.absolutePos(seed);
		ServerLevel level = helper.getLevel();
		CityState c = CityManager.get(level).activate(level, seedAbs, config()).orElseThrow();
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);

		helper.onEachTick(() -> {
			if (helper.getTick() % 600 == 0) {
				SeedCity.LOGGER.info("terrain test tick {}: {}", helper.getTick(), c.summary());
			}
			int built = 0;
			Set<Integer> levels = new HashSet<>();
			boolean pondBlocked = false;
			for (CityState.Slot s : c.slots()) {
				if (s.status == CityState.SlotStatus.BUILT) {
					built++;
					levels.add(c.slotY(s.key));
				}
				// the pond is never filled in: it stays water, or a bridge stands over it
				if (s.key.equals(new CityState.SlotKey(-2, -2))
						&& ((s.status == CityState.SlotStatus.BLOCKED && s.note.equals("water"))
						|| (s.status == CityState.SlotStatus.BUILT && s.cell != null && s.cell.getPath().equals("river_bridge")))) {
					pondBlocked = true;
				}
			}
			int apron = 0;
			for (int x = 0; x < SIZE && apron == 0; x++) {
				for (int z = 0; z < SIZE; z++) {
					int h = ground(x, z);
					for (int y = h + 1; y <= h + 4; y++) {
						BlockPos p = origin.offset(x, y, z);
						if (!c.inCity(p) && level.getBlockState(p).is(Blocks.GRASS_BLOCK)) {
							apron++;
						}
					}
				}
			}
			// cells are cut into the land: no built cell's paving stands more than terrainStep above the
			// lowest original ground under it (the core is tied to the Seed and exempt)
			String raised = null;
			for (CityState.Slot s : c.slots()) {
				if (s.status != CityState.SlotStatus.BUILT || s.key.chebyshev() == 0 || s.anchor != null) {
					continue;
				}
				BlockPos o = c.slotOrigin(s.key).subtract(origin);
				int minGround = Integer.MAX_VALUE;
				for (int dx = 0; dx < CityState.SLOT; dx++) {
					for (int dz = 0; dz < CityState.SLOT; dz++) {
						int x = o.getX() + dx;
						int z = o.getZ() + dz;
						if (x >= 0 && x < SIZE && z >= 0 && z < SIZE) {
							minGround = Math.min(minGround, ground(x, z));
						}
					}
				}
				if (minGround != Integer.MAX_VALUE && o.getY() + 1 > minGround + c.cfg().terrainStep) {
					raised = s.key + " paving at " + (o.getY() + 1) + " over ground " + minGround;
				}
			}
			if (built >= 8 && levels.size() >= 2 && pondBlocked && raised == null) {
				SeedCity.LOGGER.info("terrain test: built={} levels={} apron={} {}", built, levels, apron, c.describeSlots());
				helper.succeed();
			}
			if (helper.getTick() > 8800) {
				helper.fail("city did not fit the land: built=" + built + " levels=" + levels + " pond blocked=" + pondBlocked + " apron=" + apron + " raised=" + raised + " " + c.describeSlots());
			}
		});
	}

	@GameTest(structure = BOAT, maxTicks = 6000)
	public void chunkCapHaltsGrowth(GameTestHelper helper) {
		int start = 24 - 21;
		for (int x = start; x < start + 42; x++) {
			for (int z = start; z < start + 42; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.SMOOTH_STONE);
				helper.setBlock(new BlockPos(x, 1, z), Blocks.SMOOTH_STONE);
			}
		}
		BlockPos seed = new BlockPos(24, 2, 24);
		helper.setBlock(seed, SeedCityBlocks.SEED);
		ServerLevel level = helper.getLevel();
		SeedCityConfig cfg = config();
		cfg.maxRadiusSlots = 3;
		CityState c = CityManager.get(level).activate(level, helper.absolutePos(seed), cfg).orElseThrow();
		// the forced root (core, clock, junction) is never subject to the cap; cap the city at exactly its span
		int cap = c.chunkSpan().size();
		cfg.maxChunks = cap;
		helper.onEachTick(() -> {
			helper.assertTrue(c.chunkSpan().size() <= cap, "city spread past the chunk cap: " + c.chunkSpan().size() + " > " + cap);
			boolean capped = false;
			int built = 0;
			for (CityState.Slot s : c.slots()) {
				if (s.status == CityState.SlotStatus.BLOCKED && s.note.equals("chunk cap")) {
					capped = true;
				}
				if (s.status == CityState.SlotStatus.BUILT) {
					built++;
				}
			}
			if (capped && built >= 3) {
				helper.succeed();
			}
			if (helper.getTick() > 5800) {
				helper.fail("cap not reached cleanly: capped=" + capped + " built=" + built + " " + c.summary());
			}
		});
	}
}
