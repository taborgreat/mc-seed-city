package net.tabor.seedcity.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.tabor.seedcity.SeedCity;
import net.tabor.seedcity.SeedCityBlocks;
import net.tabor.seedcity.card.Dreamer;
import net.tabor.seedcity.card.FragmentLibrary;
import net.tabor.seedcity.config.SeedCityConfig;
import net.tabor.seedcity.core.CityManager;
import net.tabor.seedcity.core.CityState;

import java.util.List;
import java.util.Random;

/**
 * Phase 5 acceptance (design doc 25): with an empty card slot and an idle frontier for long
 * enough, the city writes and runs an L3 card that appears in the Reader as a book; a player's
 * card is never overwritten; the composer is deterministic.
 */
public final class DreamTests {
	private static final String BOAT = "seedcity:boat";
	private static final BlockPos SEED = new BlockPos(24, 2, 24);

	private static SeedCityConfig config() {
		SeedCityConfig c = new SeedCityConfig();
		c.maxRadiusSlots = 0;
		c.maxBuilders = 1;
		c.faultsPerCity = 0;
		c.sentinelsOnRegisters = false;
		c.unlimitedMaterials = true;
		c.dreamAfterSeconds = 3;
		c.dreamLengthSeconds = 600;
		c.citySeedOverride = 0x5EEDC17DL;
		return c;
	}

	private static CityState city(GameTestHelper helper) {
		for (int x = 3; x < 45; x++) {
			for (int z = 3; z < 45; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.SMOOTH_STONE);
				helper.setBlock(new BlockPos(x, 1, z), Blocks.SMOOTH_STONE);
			}
		}
		helper.setBlock(SEED, SeedCityBlocks.SEED);
		ServerLevel level = helper.getLevel();
		CityState c = CityManager.get(level).activate(level, helper.absolutePos(SEED), config()).orElseThrow();
		c.adopt(level, new CityState.SlotKey(0, 0), CityState.CORE_CELL, Rotation.NONE, false);
		c.adopt(level, new CityState.SlotKey(0, 1), CityState.CLOCK_CELL, Rotation.NONE, false);
		c.adopt(level, new CityState.SlotKey(0, 2), CityState.JUNCTION_CELL, Rotation.NONE, false);
		c.adopt(level, new CityState.SlotKey(1, 2), SeedCity.id("drawbridge"), Rotation.CLOCKWISE_90, false);
		c.adopt(level, new CityState.SlotKey(-1, 2), SeedCity.id("drawbridge"), Rotation.COUNTERCLOCKWISE_90, false);
		return c;
	}

	@GameTest(structure = BOAT, maxTicks = 1200)
	public void idleCityDreamsACard(GameTestHelper helper) {
		CityState c = city(helper);
		ServerLevel level = helper.getLevel();
		helper.onEachTick(() -> {
			if (helper.getTick() == 300) {
				helper.assertTrue(c.dreaming(), "the city should be dreaming by now: " + c.summary());
				helper.assertTrue(c.programLive(), "the dream should be live: " + c.summary());
				helper.assertTrue(c.readerLines().stream().anyMatch(l -> l.startsWith("DREAM #1")), "the Reader should show the dream: " + c.readerLines());
				ItemStack book = c.ejectCard(level);
				WrittenBookContent content = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
				helper.assertTrue(content != null && content.title().raw().equals("Dream #1"), "ejecting should hand over the dream as a book: " + book);
				helper.assertFalse(c.dreaming(), "ejected: no longer dreaming");
			}
			if (helper.getTick() == 301) {
				// a player's card holds; the city does not dream over it
				c.insertCard(level, "loop:\nOUT drawbridge.1.in 15\nWAIT 1\nJMP loop", ItemStack.EMPTY);
			}
			if (helper.getTick() == 1000) {
				helper.assertFalse(c.dreaming(), "a player's card must never be replaced by a dream: " + c.summary());
				helper.assertTrue(c.dreamCount() == 1, "exactly one dream so far: " + c.dreamCount());
				helper.succeed();
			}
		});
	}

	@GameTest(structure = "seedcity:arena", maxTicks = 20)
	public void composerIsDeterministicAndHonest(GameTestHelper helper) {
		Dreamer.Hardware hw = new Dreamer.Hardware(List.of("drawbridge.1.in"), List.of(), 0, false);
		var a = Dreamer.compose(FragmentLibrary.all(), hw, new Random(7), 1);
		var b = Dreamer.compose(FragmentLibrary.all(), hw, new Random(7), 1);
		helper.assertTrue(a.isPresent(), "fragments needing one actuator must compose");
		helper.assertTrue(a.get().text().equals(b.get().text()), "same seed, same dream");
		helper.assertFalse(a.get().text().contains("$"), "every placeholder must be filled: " + a.get().text());
		helper.assertFalse(a.get().text().contains(" R"), "no registers without vaults: " + a.get().text());
		var none = Dreamer.compose(FragmentLibrary.all(), new Dreamer.Hardware(List.of(), List.of(), 0, false), new Random(1), 1);
		helper.assertTrue(none.isEmpty(), "a city with no actuators has nothing to dream about");
		helper.succeed();
	}

	/**
	 * Purpose from the first minute: a fresh city with nothing in its reader dreams a card that
	 * asks for hardware it lacks, the builders grow that hardware because the program wants it,
	 * and the dream goes live once it stands.
	 */
	@GameTest(structure = BOAT, maxTicks = 9000)
	public void dreamsGrowTheCity(GameTestHelper helper) {
		for (int x = 3; x < 45; x++) {
			for (int z = 3; z < 45; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.SMOOTH_STONE);
				helper.setBlock(new BlockPos(x, 1, z), Blocks.SMOOTH_STONE);
			}
		}
		helper.setBlock(SEED, SeedCityBlocks.SEED);
		ServerLevel level = helper.getLevel();
		SeedCityConfig cfg = config();
		cfg.maxRadiusSlots = 3;
		cfg.maxBuilders = 3;
		cfg.cellsPerBuilder = 2;
		cfg.blocksPerSecond = 40;
		cfg.builderSpeed = 1.6;
		cfg.dreamLengthSeconds = 20;
		CityState c = CityManager.get(level).activate(level, helper.absolutePos(SEED), cfg).orElseThrow();
		boolean[] sawPlan = {false};
		helper.onEachTick(() -> {
			if (helper.getTick() % 600 == 0) {
				SeedCity.LOGGER.info("dream growth tick {}: {}", helper.getTick(), c.summary());
			}
			if (!c.wantedCells().isEmpty()) {
				sawPlan[0] = true;
			}
			if (sawPlan[0] && c.dreaming() && c.programLive() && !c.builtOfType("drawbridge").isEmpty()) {
				SeedCity.LOGGER.info("dream growth: live after {} dream(s); {}", c.dreamCount(), c.summary());
				helper.succeed();
			}
			if (helper.getTick() > 8800) {
				helper.fail("the city never grew into its own program: sawPlan=" + sawPlan[0] + " " + c.summary() + " " + c.describeSlots());
			}
		});
	}
}
