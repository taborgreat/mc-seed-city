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
import net.tabor.seedcity.cell.CellLibrary;
import net.tabor.seedcity.cell.Placement;
import net.tabor.seedcity.config.SeedCityConfig;
import net.tabor.seedcity.core.CityManager;
import net.tabor.seedcity.core.CityState;
import net.tabor.seedcity.entity.BuilderEntity;
import net.tabor.seedcity.entity.SeedCityEntities;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Phase 4 acceptance (design doc 25, read with docs/city-as-computer.md): a program that needs
 * hardware makes the builders construct it in the Forge and goes live only after verification;
 * a Builder handed a blueprint builds it where the player stands; a Courier carries a value
 * between districts; a vault opens only on a computed state; districts are zoned from the seed.
 */
public final class ComputerTests {
	private static final String BOAT = "seedcity:boat";
	private static final int PLATFORM = 42;
	private static final BlockPos SEED = new BlockPos(24, 2, 24);
	private static final CityState.SlotKey BRIDGE = new CityState.SlotKey(1, 2);

	private static SeedCityConfig quietConfig() {
		SeedCityConfig c = new SeedCityConfig();
		c.dreamAtStart = false;
		c.blocksPerSecond = 40;
		c.maxBuilders = 1;
		c.maxRadiusSlots = 0;
		c.faultsPerCity = 0;
		c.sentinelsOnRegisters = false;
		c.citySeedOverride = 0x5EEDC17DL;
		return c;
	}

	private static SeedCityConfig growingConfig() {
		SeedCityConfig c = quietConfig();
		c.maxBuilders = 3;
		c.cellsPerBuilder = 2;
		c.builderSpeed = 1.6;
		c.maxRadiusSlots = 3;
		return c;
	}

	private static void platform(GameTestHelper helper) {
		int start = SEED.getX() - PLATFORM / 2;
		for (int x = start; x < start + PLATFORM; x++) {
			for (int z = start; z < start + PLATFORM; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.SMOOTH_STONE);
				helper.setBlock(new BlockPos(x, 1, z), Blocks.SMOOTH_STONE);
			}
		}
		helper.setBlock(SEED, SeedCityBlocks.SEED);
	}

	private static CityState rootCity(GameTestHelper helper, SeedCityConfig cfg) {
		platform(helper);
		ServerLevel level = helper.getLevel();
		CityState c = CityManager.get(level).activate(level, helper.absolutePos(SEED), cfg).orElseThrow();
		c.adopt(level, new CityState.SlotKey(0, 0), CityState.CORE_CELL, Rotation.NONE, false);
		c.adopt(level, new CityState.SlotKey(0, 1), CityState.CLOCK_CELL, Rotation.NONE, false);
		c.adopt(level, new CityState.SlotKey(0, 2), CityState.JUNCTION_CELL, Rotation.NONE, false);
		return c;
	}

	private static int inputOf(CityState c, CityState.SlotKey k, ServerLevel level) {
		Placement p = c.placement(k).orElseThrow();
		Placement.WorldPort in = p.ports().stream().filter(wp -> wp.port().name().equals("in")).findFirst().orElseThrow();
		return level.getSignal(in.pos(), in.face());
	}

	@GameTest(structure = "seedcity:arena", maxTicks = 40)
	public void districtsAreZonedFromTheSeed(GameTestHelper helper) {
		SeedCityConfig cfg = quietConfig();
		CityState a = CityState.create(0, BlockPos.ZERO, cfg);
		CityState b = CityState.create(0, BlockPos.ZERO, cfg);
		a.overrideConfig(cfg);
		b.overrideConfig(cfg);
		Set<String> seen = new HashSet<>();
		for (int x = -3; x <= 3; x++) {
			for (int z = -3; z <= 3; z++) {
				CityState.SlotKey k = new CityState.SlotKey(x, z);
				helper.assertValueEqual(a.district(k), b.district(k), "district of " + k + " across two cities with one seed");
				seen.add(a.district(k));
			}
		}
		helper.assertValueEqual(a.district(new CityState.SlotKey(0, 0)), "core", "the seed's own slot");
		helper.assertTrue(seen.containsAll(List.of("core", "forge", "ram", "storage")), "all functional districts present within radius 3: " + seen);
		cfg.citySeedOverride = 0xBEEFL;
		CityState other = CityState.create(0, BlockPos.ZERO, cfg);
		other.overrideConfig(cfg);
		boolean differs = false;
		for (int x = -3; x <= 3 && !differs; x++) {
			for (int z = -3; z <= 3; z++) {
				CityState.SlotKey k = new CityState.SlotKey(x, z);
				if (!a.district(k).equals(other.district(k))) {
					differs = true;
					break;
				}
			}
		}
		helper.assertTrue(differs, "a different seed zones the city differently");
		helper.succeed();
	}

	@GameTest(structure = BOAT, maxTicks = 9000)
	public void programNeedsGrowHardwareInTheForge(GameTestHelper helper) {
		CityState c = rootCity(helper, growingConfig());
		ServerLevel level = helper.getLevel();
		CityState.CardResult r = c.insertCard(level, "SET R0 3\nloop:\nADD R0 4\nOUT drawbridge.*.in R0\nJMP loop\n", ItemStack.EMPTY);
		helper.assertTrue(r.accepted() && !c.programLive(), "card should be a plan at first: " + r.message());
		helper.onEachTick(() -> {
			if (helper.getTick() % 400 == 0) {
				SeedCity.LOGGER.info("forge test tick {}: {}", helper.getTick(), c.summary());
			}
			if (!c.programLive()) {
				return;
			}
			for (String type : List.of("alu_sub", "alu_not", "register_block")) {
				List<CityState.Slot> built = c.builtOfType(type);
				helper.assertFalse(built.isEmpty(), type + " should be built before the program goes live");
			}
			for (String type : List.of("alu_sub", "alu_not")) {
				for (CityState.Slot s : c.builtOfType(type)) {
					helper.assertValueEqual(c.district(s.key), "forge", "district of " + type + " at " + s.key);
				}
			}
			for (CityState.Slot s : c.builtOfType("register_block")) {
				String d = c.district(s.key);
				helper.assertTrue(d.equals("ram") || d.equals("core"), "register at " + s.key + " should be in RAM or next to the core, was " + d);
			}
			SeedCity.LOGGER.info("forge test live at tick {}: {}", helper.getTick(), c.summary());
			helper.succeed();
		});
	}

	@GameTest(structure = BOAT, maxTicks = 2400)
	public void courierCarriesValueBetweenDistricts(GameTestHelper helper) {
		CityState c = rootCity(helper, quietConfig());
		ServerLevel level = helper.getLevel();
		c.adopt(level, BRIDGE, SeedCity.id("drawbridge"), Rotation.COUNTERCLOCKWISE_90, false);
		helper.assertTrue(c.needsCourier(BRIDGE), "the bridge at " + BRIDGE + " should be outside the core district (" + c.district(BRIDGE) + ")");
		helper.assertTrue(c.insertCard(level, "loop:\nOUT drawbridge.1.in 15\nJMP loop\n", ItemStack.EMPTY).accepted(), "card");
		boolean[] sawCourier = {false};
		helper.onEachTick(() -> {
			if (!c.couriers(level).isEmpty()) {
				sawCourier[0] = true;
			}
			if (helper.getTick() > 200 && inputOf(c, BRIDGE, level) == 15) {
				helper.assertTrue(sawCourier[0], "a Courier should have carried the value");
				helper.succeed();
			}
			if (helper.getTick() > 2200) {
				helper.fail("value never arrived: " + c.summary() + " couriers=" + c.couriers(level).size() + " mail=" + c.pendingMail());
			}
		});
	}

	@GameTest(structure = BOAT, maxTicks = 3000)
	public void blueprintBuildsWhereThePlayerStands(GameTestHelper helper) {
		CityState c = rootCity(helper, quietConfig());
		ServerLevel level = helper.getLevel();
		BlockPos feet = helper.absolutePos(new BlockPos(9, 2, 9));
		Placement p = new Placement(CellLibrary.get(SeedCity.id("register_block")).orElseThrow(), feet.offset(-3, -1, -3), Rotation.NONE);
		helper.assertTrue(c.buildableAt(level, p), "the platform corner should be buildable");
		helper.onEachTick(() -> {
			if (helper.getTick() == 40) {
				List<BuilderEntity> builders = c.builders(level);
				helper.assertFalse(builders.isEmpty(), "a builder should exist");
				String refused = builders.getFirst().acceptBlueprint(level, p);
				helper.assertTrue(refused == null, "builder refused the blueprint: " + refused);
			}
			if (helper.getTick() > 40 && c.lastBlueprintResult().contains("PASS")) {
				BlockPos b1 = p.origin().offset(3, 1, 3);
				helper.assertTrue(level.getBlockState(b1).is(Blocks.STONE_BRICKS), "the vault's ring block should stand where the player stood");
				helper.succeed();
			}
			if (helper.getTick() > 2800) {
				helper.fail("blueprint not built and verified in time: " + c.lastBlueprintResult());
			}
		});
	}

	@GameTest(structure = BOAT, maxTicks = 1400)
	public void vaultOpensOnlyOnTheComputedState(GameTestHelper helper) {
		CityState c = rootCity(helper, quietConfig());
		ServerLevel level = helper.getLevel();
		CityState.SlotKey vaultKey = new CityState.SlotKey(-1, 0);
		c.adopt(level, vaultKey, SeedCity.id("vault"), Rotation.NONE, false);
		c.adopt(level, new CityState.SlotKey(1, 0), SeedCity.id("register_block"), Rotation.NONE, false);
		Placement v = c.placement(vaultKey).orElseThrow();
		BlockPos door = v.origin().offset(new BlockPos(3, 1, 2).rotate(v.rotation()));
		helper.assertTrue(level.getBlockState(door).hasProperty(BlockStateProperties.OPEN), "iron door at " + door);
		CityState.CardResult r = c.insertCard(level, "SET R0 14\nOUT vault.1.in R0\nWAIT 3\nSET R0 15\nOUT vault.1.in R0\nend:\nJMP end\n", ItemStack.EMPTY);
		helper.assertTrue(r.accepted() && c.programLive(), "card: " + r.message());
		helper.onEachTick(() -> {
			long t = helper.getTick();
			if (t == 320) {
				helper.assertValueEqual(inputOf(c, vaultKey, level) > 0, true, "the lock is being driven");
				helper.assertFalse(level.getBlockState(door).getValue(BlockStateProperties.OPEN), "14 must not open the vault");
			}
			if (t == 1100) {
				helper.assertTrue(level.getBlockState(door).getValue(BlockStateProperties.OPEN), "15 opens the vault");
				helper.succeed();
			}
		});
	}
}
