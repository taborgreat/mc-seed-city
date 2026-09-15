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
 * The RAM vault's address decoder as a rig, swept over every select value. The select arrives
 * as a strongly powered block; a dust snake knocks one off per block; a repeater on a dust
 * gives "at least k" as a 15; two subtract comparators pick out exactly 1 (read me) and
 * exactly 8 (write me). The vault cell is built from exactly this.
 */
public final class BusDecodeTests {
	private static final int Y = 2;

	private static void comparator(GameTestHelper h, BlockPos p, Direction out, boolean subtract) {
		h.setBlock(p, Blocks.COMPARATOR.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, out.getOpposite())
				.setValue(BlockStateProperties.MODE_COMPARATOR, subtract ? ComparatorMode.SUBTRACT : ComparatorMode.COMPARE));
	}

	private static void repeater(GameTestHelper h, BlockPos p, Direction out) {
		h.setBlock(p, Blocks.REPEATER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, out.getOpposite()));
	}

	private static int signalFrom(GameTestHelper h, BlockPos p, Direction out) {
		return h.getLevel().getSignal(h.absolutePos(p), out.getOpposite());
	}

	@GameTest(structure = "seedcity:arena", maxTicks = 700)
	public void selectDecodesReadAndWrite(GameTestHelper h) {
		for (int x = 0; x < 12; x++) {
			for (int z = 0; z < 12; z++) {
				h.setBlock(new BlockPos(x, Y - 1, z), Blocks.SMOOTH_STONE);
			}
		}
		BlockPos probe = new BlockPos(8, Y, 0);
		comparator(h, new BlockPos(8, Y, 1), Direction.SOUTH, false);
		h.setBlock(new BlockPos(8, Y, 2), Blocks.STONE_BRICKS);                              // Bs = sel
		int[][] snake = {{7, 2}, {6, 2}, {5, 2}, {4, 2}, {4, 3}, {4, 4}, {4, 5}, {4, 6}, {4, 7}};   // d0..d8 = sel-0 .. sel-8
		for (int[] d : snake) {
			h.setBlock(new BlockPos(d[0], Y, d[1]), Blocks.REDSTONE_WIRE);
		}
		// read-select R = [sel>=1] minus [sel>=2]
		repeater(h, new BlockPos(7, Y, 3), Direction.SOUTH);                                 // reads d0
		h.setBlock(new BlockPos(7, Y, 4), Blocks.STONE_BRICKS);                              // T1
		comparator(h, new BlockPos(7, Y, 5), Direction.SOUTH, true);                         // R, rear T1
		h.setBlock(new BlockPos(9, Y, 2), Blocks.REDSTONE_WIRE);                             // sel again, east of Bs
		h.setBlock(new BlockPos(9, Y, 3), Blocks.REDSTONE_WIRE);                             // sel-1
		repeater(h, new BlockPos(9, Y, 4), Direction.SOUTH);                                 // [sel>=2]
		h.setBlock(new BlockPos(9, Y, 5), Blocks.STONE_BRICKS);                              // T2
		repeater(h, new BlockPos(8, Y, 5), Direction.WEST);                                  // T2 into R's side
		h.setBlock(new BlockPos(7, Y, 6), Blocks.STONE_BRICKS);                              // R block
		// write-select W = [sel>=8] minus [sel>=9]
		repeater(h, new BlockPos(5, Y, 6), Direction.EAST);                                  // reads d7
		h.setBlock(new BlockPos(6, Y, 6), Blocks.STONE_BRICKS);                              // T8
		comparator(h, new BlockPos(6, Y, 7), Direction.SOUTH, true);                         // W, rear T8
		repeater(h, new BlockPos(5, Y, 7), Direction.EAST);                                  // reads d8, into W's side
		h.setBlock(new BlockPos(6, Y, 8), Blocks.STONE_BRICKS);                              // W block

		int[] sel = {0};
		StringBuilder log = new StringBuilder();
		h.onEachTick(() -> {
			long t = h.getTick();
			if (t % 30 == 0 && sel[0] <= 15) {
				h.setBlock(probe, SeedCityBlocks.PROBE.defaultBlockState().setValue(ProbeBlock.POWER, sel[0]).setValue(ProbeBlock.FACING, Direction.SOUTH));
			}
			if (t % 30 == 29 && sel[0] <= 15) {
				int r = signalFrom(h, new BlockPos(7, Y, 5), Direction.SOUTH);
				int w = signalFrom(h, new BlockPos(6, Y, 7), Direction.SOUTH);
				int d0 = h.getBlockState(new BlockPos(7, Y, 2)).getValue(BlockStateProperties.POWER);
				log.append("sel=").append(sel[0]).append(" d0=").append(d0).append(" R=").append(r).append(" W=").append(w).append("; ");
				boolean ok = (r == 15) == (sel[0] == 1) && (w == 15) == (sel[0] == 8) && (r == 0 || r == 15) && (w == 0 || w == 15);
				if (!ok) {
					h.fail("decoder wrong: " + log);
				}
				sel[0]++;
				if (sel[0] > 15) {
					net.tabor.seedcity.SeedCity.LOGGER.info("bus decode rig: {}", log);
					h.succeed();
				}
			}
		});
	}
}
