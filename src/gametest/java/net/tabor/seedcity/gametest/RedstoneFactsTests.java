package net.tabor.seedcity.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ComparatorMode;
import net.tabor.seedcity.SeedCityBlocks;
import net.tabor.seedcity.verify.ProbeBlock;

/**
 * The redstone facts the bus cells lean on, checked against the real engine so the cell designs
 * rest on measured behaviour, not recollection. Each test builds a tiny rig from a probe block
 * (our directional signal source) and reads the result with the same call the verifier uses.
 */
public final class RedstoneFactsTests {
	private static final int Y = 2;

	private static void floor(GameTestHelper h) {
		for (int x = 0; x < 12; x++) {
			for (int z = 0; z < 10; z++) {
				h.setBlock(new BlockPos(x, Y - 1, z), Blocks.SMOOTH_STONE);
			}
		}
	}

	private static void probe(GameTestHelper h, BlockPos p, Direction emit, int power) {
		h.setBlock(p, SeedCityBlocks.PROBE.defaultBlockState().setValue(ProbeBlock.POWER, power).setValue(ProbeBlock.FACING, emit));
	}

	/** A comparator whose output goes toward {@code out}. */
	private static void comparator(GameTestHelper h, BlockPos p, Direction out, boolean subtract) {
		h.setBlock(p, Blocks.COMPARATOR.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, out.getOpposite())
				.setValue(BlockStateProperties.MODE_COMPARATOR, subtract ? ComparatorMode.SUBTRACT : ComparatorMode.COMPARE));
	}

	/** What a component at p outputs in direction {@code out}, read the way the verifier reads ports. */
	private static int signalFrom(GameTestHelper h, BlockPos p, Direction out) {
		return h.getLevel().getSignal(h.absolutePos(p), out.getOpposite());
	}

	/** Dust on a strongly powered block carries the block's level; a comparator reading that block reads the level. */
	@GameTest(structure = "seedcity:arena", maxTicks = 60)
	public void dustOnPoweredBlockAndComparatorRead(GameTestHelper h) {
		floor(h);
		// probe(7) -> comparator east -> block B; dust on B; comparator east of B reads B
		probe(h, new BlockPos(1, Y, 3), Direction.EAST, 7);
		comparator(h, new BlockPos(2, Y, 3), Direction.EAST, false);
		h.setBlock(new BlockPos(3, Y, 3), Blocks.STONE_BRICKS);
		h.setBlock(new BlockPos(3, Y + 1, 3), Blocks.REDSTONE_WIRE);
		comparator(h, new BlockPos(4, Y, 3), Direction.EAST, false);
		h.runAfterDelay(20, () -> {
			int dust = h.getBlockState(new BlockPos(3, Y + 1, 3)).getValue(BlockStateProperties.POWER);
			h.assertValueEqual(dust, 7, "dust on a block strongly powered to 7");
			h.assertValueEqual(signalFrom(h, new BlockPos(4, Y, 3), Direction.EAST), 7, "comparator reading the powered block");
			h.succeed();
		});
	}

	/** Dust on top of a block powers the block strongly enough for a comparator behind it to read the dust's level. */
	@GameTest(structure = "seedcity:arena", maxTicks = 60)
	public void comparatorReadsBlockUnderDust(GameTestHelper h) {
		floor(h);
		// probe(9) -> comparator east into block A (strong, 9); dust on A; dust continues on a plain block C (8); comparator reads C
		probe(h, new BlockPos(1, Y, 3), Direction.EAST, 9);
		comparator(h, new BlockPos(2, Y, 3), Direction.EAST, false);
		h.setBlock(new BlockPos(3, Y, 3), Blocks.STONE_BRICKS);
		h.setBlock(new BlockPos(3, Y + 1, 3), Blocks.REDSTONE_WIRE);
		h.setBlock(new BlockPos(3, Y, 4), Blocks.STONE_BRICKS);
		h.setBlock(new BlockPos(3, Y + 1, 4), Blocks.REDSTONE_WIRE);
		comparator(h, new BlockPos(3, Y, 5), Direction.SOUTH, false);
		h.runAfterDelay(20, () -> {
			int dust = h.getBlockState(new BlockPos(3, Y + 1, 4)).getValue(BlockStateProperties.POWER);
			h.assertValueEqual(dust, 8, "dust one block on: 8");
			h.assertValueEqual(signalFrom(h, new BlockPos(3, Y, 5), Direction.SOUTH), 8, "comparator reading a block that only has dust on it");
			h.succeed();
		});
	}

	/** A subtract comparator with dust of strength 1 on its side takes one off. */
	@GameTest(structure = "seedcity:arena", maxTicks = 60)
	public void subtractOneViaSideDust(GameTestHelper h) {
		floor(h);
		probe(h, new BlockPos(1, Y, 3), Direction.EAST, 5);
		comparator(h, new BlockPos(2, Y, 3), Direction.EAST, false);
		h.setBlock(new BlockPos(3, Y, 3), Blocks.STONE_BRICKS);
		comparator(h, new BlockPos(4, Y, 3), Direction.EAST, true);   // rear = block(5), side = dust(1) from the south
		h.setBlock(new BlockPos(4, Y, 4), Blocks.REDSTONE_WIRE);
		probe(h, new BlockPos(4, Y, 5), Direction.NORTH, 1);           // a 1 into the dust from the south
		h.runAfterDelay(20, () -> {
			h.assertValueEqual(signalFrom(h, new BlockPos(4, Y, 3), Direction.EAST), 4, "5 minus a side of 1");
			h.succeed();
		});
	}

	/** Dust beneath a strongly powered block carries the level too: the way a signal comes back down a floor. */
	@GameTest(structure = "seedcity:arena", maxTicks = 60)
	public void dustUnderPoweredBlock(GameTestHelper h) {
		floor(h);
		// on the upper floor: probe(6) -> comparator east -> block P at y+1; dust at y under P, on the floor; comparator reads that dust
		h.setBlock(new BlockPos(1, Y, 3), Blocks.STONE_BRICKS);
		h.setBlock(new BlockPos(2, Y, 3), Blocks.STONE_BRICKS);
		probe(h, new BlockPos(1, Y + 1, 3), Direction.EAST, 6);
		comparator(h, new BlockPos(2, Y + 1, 3), Direction.EAST, false);
		h.setBlock(new BlockPos(3, Y + 1, 3), Blocks.STONE_BRICKS);
		h.setBlock(new BlockPos(3, Y, 3), Blocks.REDSTONE_WIRE);
		comparator(h, new BlockPos(3, Y, 4), Direction.SOUTH, false);
		h.runAfterDelay(20, () -> {
			int dust = h.getBlockState(new BlockPos(3, Y, 3)).getValue(BlockStateProperties.POWER);
			h.assertValueEqual(dust, 6, "dust under a block strongly powered to 6");
			h.assertValueEqual(signalFrom(h, new BlockPos(3, Y, 4), Direction.SOUTH), 6, "comparator reading that dust");
			h.succeed();
		});
	}

	/** Dust climbs one block onto a step, losing one; a repeater after it restores a 15. */
	@GameTest(structure = "seedcity:arena", maxTicks = 60)
	public void dustStepsUpAndRepeaterRestores(GameTestHelper h) {
		floor(h);
		probe(h, new BlockPos(1, Y, 3), Direction.EAST, 15);
		comparator(h, new BlockPos(2, Y, 3), Direction.EAST, false);
		h.setBlock(new BlockPos(3, Y, 3), Blocks.STONE_BRICKS);
		h.setBlock(new BlockPos(3, Y + 1, 3), Blocks.REDSTONE_WIRE);          // 15 on the powered block
		h.setBlock(new BlockPos(4, Y, 3), Blocks.STONE_BRICKS);
		h.setBlock(new BlockPos(4, Y + 1, 3), Blocks.STONE_BRICKS);            // the step
		h.setBlock(new BlockPos(4, Y + 2, 3), Blocks.REDSTONE_WIRE);          // 14 on the step
		h.setBlock(new BlockPos(5, Y + 1, 3), Blocks.STONE_BRICKS);
		h.setBlock(new BlockPos(5, Y + 2, 3), Blocks.REPEATER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST));
		h.runAfterDelay(20, () -> {
			h.assertValueEqual(h.getBlockState(new BlockPos(4, Y + 2, 3)).getValue(BlockStateProperties.POWER), 14, "dust on the step");
			h.assertValueEqual(signalFrom(h, new BlockPos(5, Y + 2, 3), Direction.EAST), 15, "repeater after the step");
			h.succeed();
		});
	}

	/** A repeater beside a strongly powered block, pointing away from it, outputs 15: a level-to-bit converter. */
	@GameTest(structure = "seedcity:arena", maxTicks = 60)
	public void repeaterReadsPoweredBlockAsBit(GameTestHelper h) {
		floor(h);
		probe(h, new BlockPos(1, Y, 3), Direction.EAST, 2);
		comparator(h, new BlockPos(2, Y, 3), Direction.EAST, false);
		h.setBlock(new BlockPos(3, Y, 3), Blocks.STONE_BRICKS);
		h.setBlock(new BlockPos(3, Y, 4), Blocks.REPEATER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
		h.runAfterDelay(20, () -> {
			h.assertValueEqual(signalFrom(h, new BlockPos(3, Y, 4), Direction.SOUTH), 15, "repeater reading a block powered to 2");
			h.succeed();
		});
	}

	/** Two sources into one block: the block takes the stronger. The return lane merge. */
	@GameTest(structure = "seedcity:arena", maxTicks = 60)
	public void blockTakesTheStrongerOfTwoInputs(GameTestHelper h) {
		floor(h);
		probe(h, new BlockPos(1, Y, 3), Direction.EAST, 3);
		comparator(h, new BlockPos(2, Y, 3), Direction.EAST, false);
		h.setBlock(new BlockPos(3, Y, 3), Blocks.STONE_BRICKS);
		probe(h, new BlockPos(3, Y, 1), Direction.SOUTH, 11);
		comparator(h, new BlockPos(3, Y, 2), Direction.SOUTH, false);
		comparator(h, new BlockPos(4, Y, 3), Direction.EAST, false);
		h.runAfterDelay(20, () -> {
			h.assertValueEqual(signalFrom(h, new BlockPos(4, Y, 3), Direction.EAST), 11, "max(3, 11)");
			h.succeed();
		});
	}
}
