package net.tabor.seedcity.verify;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.tabor.seedcity.SeedCityBlocks;
import net.tabor.seedcity.cell.Cell;
import net.tabor.seedcity.cell.Placement;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Blueprint integrity: does a placed cell still have the blocks its structure says it has?
 * Cheap and non-invasive, unlike a port verification, so Wardens can sweep a district every few
 * seconds. Compares block identity and the static properties that define a circuit (facing,
 * delay, comparator mode, hanging), never runtime state (power, lit, extended, locked).
 * Blocks a piston moves are excluded, as are verifier probes.
 */
public final class Integrity {
	private static final List<Property<?>> STATIC = List.of(
			BlockStateProperties.FACING,
			BlockStateProperties.HORIZONTAL_FACING,
			BlockStateProperties.DELAY,
			BlockStateProperties.MODE_COMPARATOR,
			BlockStateProperties.HANGING
	);

	private Integrity() {
	}

	/** True when the two states are the same circuit element, ignoring runtime state. */
	public static boolean equivalent(BlockState expected, BlockState actual) {
		if (expected.getBlock() != actual.getBlock()) {
			return false;
		}
		for (Property<?> p : STATIC) {
			if (expected.hasProperty(p) && actual.hasProperty(p) && !expected.getValue(p).equals(actual.getValue(p))) {
				return false;
			}
		}
		return true;
	}

	/** Cell-local positions a piston in the blueprint may push into. */
	public static Set<BlockPos> dynamicPositions(Cell cell) {
		Set<BlockPos> out = new HashSet<>();
		for (Cell.CellBlock b : cell.blocks()) {
			if (b.state().getBlock() instanceof PistonBaseBlock) {
				Direction f = b.state().getValue(BlockStateProperties.FACING);
				out.add(b.pos().relative(f));
				out.add(b.pos().relative(f, 2));
			}
		}
		return out;
	}

	/** World positions inside the placement whose block no longer matches the blueprint. */
	public static List<BlockPos> damaged(ServerLevel level, Placement p) {
		List<BlockPos> out = new ArrayList<>();
		Set<BlockPos> dynamic = new HashSet<>();
		for (BlockPos d : dynamicPositions(p.cell())) {
			dynamic.add(d.rotate(p.rotation()));
		}
		for (Cell.CellBlock b : p.cell().blocks(p.rotation())) {
			if (dynamic.contains(b.pos())) {
				continue;
			}
			BlockPos world = p.origin().offset(b.pos());
			BlockState actual = level.getBlockState(world);
			if (actual.is(SeedCityBlocks.PROBE) || actual.is(SeedCityBlocks.TERMINAL)
					|| actual.is(Blocks.PISTON_HEAD) || actual.is(Blocks.MOVING_PISTON)) {
				continue;
			}
			if (!equivalent(b.state(), actual)) {
				out.add(world);
			}
		}
		return out;
	}
}
