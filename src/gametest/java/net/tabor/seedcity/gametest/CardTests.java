package net.tabor.seedcity.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.tabor.seedcity.SeedCity;
import net.tabor.seedcity.SeedCityBlocks;
import net.tabor.seedcity.cell.Placement;
import net.tabor.seedcity.config.SeedCityConfig;
import net.tabor.seedcity.core.CityManager;
import net.tabor.seedcity.core.CityState;

import java.util.HashSet;
import java.util.Set;

/**
 * Phase 3 acceptance (design doc 25): a valid L1 card changes an actuator within one Clock Tower
 * cycle; a card with a syntax error shows its line on the Reader wall and leaves the previous
 * program running; removing the card returns the city to its default. Plus: a card whose
 * hardware is missing is held as a plan (docs/city-as-computer.md), and arithmetic runs through
 * the ALU cells.
 */
public final class CardTests {
	private static final String BOAT = "seedcity:boat";
	private static final int PLATFORM = 42;
	private static final BlockPos SEED = new BlockPos(24, 2, 24);
	private static final CityState.SlotKey BRIDGE = new CityState.SlotKey(1, 2);
	private static final CityState.SlotKey VAULT = new CityState.SlotKey(-1, 0);
	/** One clock beat is 68 ticks; "within one cycle" plus write latency comfortably fits in 200. */
	private static final int SETTLED = 420;

	private static SeedCityConfig quietConfig() {
		SeedCityConfig c = new SeedCityConfig();
		c.blocksPerSecond = 40;
		c.maxBuilders = 1;
		c.maxRadiusSlots = 0;
		c.faultsPerCity = 0;
		c.sentinelsOnRegisters = false;
		c.citySeedOverride = 0x5EEDC17DL;
		return c;
	}

	/** Platform, Seed, core, clock, the forced junction, a drawbridge on its east output, and a register vault. */
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
		CityState c = CityManager.get(level).activate(level, helper.absolutePos(SEED), quietConfig()).orElseThrow();
		c.adopt(level, new CityState.SlotKey(0, 0), CityState.CORE_CELL, Rotation.NONE, false);
		c.adopt(level, new CityState.SlotKey(0, 1), CityState.CLOCK_CELL, Rotation.NONE, false);
		c.adopt(level, new CityState.SlotKey(0, 2), CityState.JUNCTION_CELL, Rotation.NONE, false);
		c.adopt(level, BRIDGE, SeedCity.id("drawbridge"), Rotation.COUNTERCLOCKWISE_90, false);
		c.adopt(level, VAULT, SeedCity.id("register_block"), Rotation.NONE, false);
		return c;
	}

	/** What the bridge's input repeater passes into the cell: its output, read from the inside. */
	private static int bridgeInput(CityState c, ServerLevel level) {
		Placement p = c.placement(BRIDGE).orElseThrow();
		Placement.WorldPort in = p.ports().stream().filter(wp -> wp.port().name().equals("in")).findFirst().orElseThrow();
		return level.getSignal(in.pos(), in.face());
	}

	private static boolean bridgeUp(CityState c, ServerLevel level) {
		Placement p = c.placement(BRIDGE).orElseThrow();
		BlockPos piston = p.origin().offset(new BlockPos(3, 1, 4).rotate(p.rotation()));
		return level.getBlockState(piston).hasProperty(BlockStateProperties.EXTENDED) && level.getBlockState(piston).getValue(BlockStateProperties.EXTENDED);
	}

	@GameTest(structure = BOAT, maxTicks = 1200)
	public void cardOverridesActuatorWithinOneCycle(GameTestHelper helper) {
		CityState c = rootCity(helper);
		ServerLevel level = helper.getLevel();
		CityState.CardResult r = c.insertCard(level, "SET R0 15\nloop:\nOUT drawbridge.1.in R0\nJMP loop\n", ItemStack.EMPTY);
		helper.assertTrue(r.accepted(), "card should be accepted: " + r.message());
		helper.assertTrue(c.programLive(), "card should be live, was: " + c.programSummary());
		int[] lows = {0};
		helper.onEachTick(() -> {
			long t = helper.getTick();
			if (t > SETTLED && t <= SETTLED + 400) {
				if (bridgeInput(c, level) != 15) {
					lows[0]++;
				}
			}
			if (t == SETTLED + 400) {
				helper.assertValueEqual(lows[0], 0, "ticks the bridge input dropped below 15 while the card held it");
				helper.assertTrue(bridgeUp(c, level), "bridge should be up under the card");
				helper.assertValueEqual(c.readPort(VAULT, "out"), 15, "R0 as read from its vault");
				helper.succeed();
			}
		});
	}

	@GameTest(structure = BOAT, maxTicks = 600)
	public void badCardShowsLineAndKeepsPreviousProgram(GameTestHelper helper) {
		CityState c = rootCity(helper);
		ServerLevel level = helper.getLevel();
		helper.assertTrue(c.insertCard(level, "SET R0 15\nloop:\nOUT drawbridge.1.in R0\nJMP loop\n", ItemStack.EMPTY).accepted(), "first card");
		CityState.CardResult bad = c.insertCard(level, "SET R0 15\nFROBNICATE R0\nJMP loop\n", ItemStack.EMPTY);
		helper.assertFalse(bad.accepted(), "bad card must be rejected");
		helper.assertTrue(bad.message().contains("line 2"), "rejection names the line: " + bad.message());
		helper.assertTrue(c.programLive(), "previous program keeps running: " + c.programSummary());
		boolean onWall = c.readerLines().stream().anyMatch(l -> l.contains("line 2"));
		helper.assertTrue(onWall, "reader wall shows the line: " + c.readerLines());
		CityState.CardResult unknown = c.insertCard(level, "OUT drawbridge.1.raise 15\n", ItemStack.EMPTY);
		helper.assertFalse(unknown.accepted(), "unknown port must be rejected");
		helper.assertTrue(unknown.message().contains("line 1"), "port error names the line: " + unknown.message());
		helper.succeed();
	}

	@GameTest(structure = BOAT, maxTicks = 1600)
	public void ejectingReturnsToDefault(GameTestHelper helper) {
		CityState c = rootCity(helper);
		ServerLevel level = helper.getLevel();
		c.insertCard(level, "SET R0 15\nloop:\nOUT drawbridge.1.in R0\nJMP loop\n", ItemStack.EMPTY);
		Set<Integer> seen = new HashSet<>();
		helper.onEachTick(() -> {
			long t = helper.getTick();
			if (t == SETTLED) {
				helper.assertValueEqual(bridgeInput(c, level), 15, "bridge input under the card");
				c.ejectCard(level);
				helper.assertFalse(c.programLive(), "no program after eject");
			}
			if (t > SETTLED + 40 && t < SETTLED + 400) {
				seen.add(bridgeInput(c, level) > 0 ? 1 : 0);
			}
			if (t == SETTLED + 400) {
				helper.assertTrue(seen.contains(0) && seen.contains(1), "after eject the clock should cycle the bridge again; saw " + seen);
				helper.succeed();
			}
		});
	}

	@GameTest(structure = BOAT, maxTicks = 200)
	public void cardWithoutHardwareIsAPlan(GameTestHelper helper) {
		CityState c = rootCity(helper);
		ServerLevel level = helper.getLevel();
		CityState.CardResult r = c.insertCard(level, "loop:\nADD R0 1\nOUT drawbridge.1.in R0\nJMP loop\n", ItemStack.EMPTY);
		helper.assertTrue(r.accepted(), "a card needing hardware is accepted as a plan: " + r.message());
		helper.assertFalse(c.programLive(), "but it must not run yet");
		helper.assertTrue(c.wantedCells().containsKey(SeedCity.id("alu_sub")) && c.wantedCells().containsKey(SeedCity.id("alu_not")),
				"the plan wants the ALU cells: " + c.wantedCells());
		helper.assertTrue(c.programSummary().contains("plan"), "summary says plan: " + c.programSummary());
		helper.succeed();
	}

	/** SUB through the subtractor cell: 15 - 6 = 9, then saturating at 0. */
	@GameTest(structure = BOAT, maxTicks = 2400)
	public void subtractionRunsThroughTheAlu(GameTestHelper helper) {
		CityState c = rootCity(helper);
		ServerLevel level = helper.getLevel();
		c.adopt(level, new CityState.SlotKey(1, 0), SeedCity.id("alu_sub"), Rotation.NONE, false);
		CityState.CardResult r = c.insertCard(level, "SET R0 15\nSUB R0 6\nSUB R0 9\nSUB R0 3\nend:\nJMP end\n", ItemStack.EMPTY);
		helper.assertTrue(r.accepted() && c.programLive(), "card should run: " + r.message());
		int[] expected = {15, 9, 0, 0};
		Set<Integer> observed = new HashSet<>();
		helper.onEachTick(() -> {
			long t = helper.getTick();
			if (t > 60) {
				observed.add(c.readPort(VAULT, "out"));
			}
			if (t == 1800) {
				helper.assertTrue(observed.contains(15) && observed.contains(9) && observed.contains(0),
						"R0 should pass through 15, 9, 0; saw " + observed);
				helper.assertValueEqual(c.readPort(VAULT, "out"), expected[3], "R0 at the end");
				helper.succeed();
			}
		});
	}
}
