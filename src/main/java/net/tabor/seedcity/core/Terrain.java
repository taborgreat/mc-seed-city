package net.tabor.seedcity.core;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.tabor.seedcity.SeedCity;
import net.tabor.seedcity.SeedCityBlocks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * The land under a city (design doc 8, 20.1, Phase 5). Reads the ground, decides whether a slot
 * fits it (slope limit, no water, no player builds, no warding), and works out the site work a
 * cell needs: what to dig out above its floor, what to fill in below it, and the earth apron that
 * banks the city's edge into the surrounding surface so plazas fade into ground instead of
 * hard-cutting. Also finds what Collectors may mine. Pure functions over a level; no state.
 */
public final class Terrain {
	/** Blocks in this tag fence the city: Builders will not plan across them, Collectors will not pass them. */
	public static final TagKey<Block> WARDING = TagKey.create(Registries.BLOCK, SeedCity.id("warding"));
	/** Columns scanned below the heightmap looking for ground before giving up. */
	private static final int GROUND_SCAN = 24;

	private Terrain() {
	}

	/** The three currencies of the ledger, and what in the world yields them. */
	public enum Kind {
		WOOD("wood", 4), STONE("stone", 2), REDSTONE("redstone", 8);

		public final String name;
		/** Ledger units one mined block is worth. */
		public final int yield;

		Kind(String name, int yield) {
			this.name = name;
			this.yield = yield;
		}

		public boolean matches(BlockState s) {
			return switch (this) {
				case WOOD -> s.is(BlockTags.LOGS);
				case STONE -> s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(Blocks.COBBLESTONE);
				case REDSTONE -> s.is(Blocks.REDSTONE_ORE) || s.is(Blocks.DEEPSLATE_REDSTONE_ORE);
			};
		}

		/** What a cleared natural block is worth to the ledger, or null when it is worth nothing. */
		public static Kind of(BlockState s) {
			for (Kind k : values()) {
				if (k.matches(s)) {
					return k;
				}
			}
			return null;
		}
	}

	/** A block to place as part of site work. */
	public record Placed(BlockPos pos, BlockState state) {
	}

	/** The ground under a footprint: per-column top ground y, or the reason it will not do. */
	public record Survey(int[] ground, int min, int max, int median, String reason) {
		public boolean ok() {
			return reason == null;
		}

		public static Survey unfit(String reason) {
			return new Survey(new int[0], 0, 0, 0, reason);
		}
	}

	// ---- reading the ground ----------------------------------------------------------------

	/** Ground: a full solid block that is not a tree, a plant, a snow layer or a fluid. */
	public static boolean isGround(ServerLevel level, BlockPos pos, BlockState s) {
		if (s.isAir() || !s.getFluidState().isEmpty() || s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES)
				|| s.getBlock() instanceof VegetationBlock || s.is(BlockTags.REPLACEABLE) || s.is(Blocks.BARRIER)) {
			return false;
		}
		return s.isCollisionShapeFullBlock(level, pos);
	}

	/** The y of the top ground block in a column, or {@link Integer#MIN_VALUE} when there is none within reach. */
	public static int groundY(ServerLevel level, int x, int z) {
		int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, top, z);
		for (int i = 0; i < GROUND_SCAN && p.getY() >= level.getMinY(); i++) {
			if (isGround(level, p, level.getBlockState(p))) {
				return p.getY();
			}
			p.move(Direction.DOWN);
		}
		return Integer.MIN_VALUE;
	}

	public static boolean loaded(ServerLevel level, int x0, int z0, int size) {
		for (int cx = x0 >> 4; cx <= (x0 + size - 1) >> 4; cx++) {
			for (int cz = z0 >> 4; cz <= (z0 + size - 1) >> 4; cz++) {
				if (!level.hasChunk(cx, cz)) {
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * Surveys a square footprint: every column needs ground within reach and no water on it, the
	 * spread of ground heights must stay within {@code slopeLimit}, and nothing in or one block
	 * around the footprint may be a warding block.
	 */
	public static Survey survey(ServerLevel level, int x0, int z0, int size, int slopeLimit) {
		int[] ground = new int[size * size];
		int min = Integer.MAX_VALUE;
		int max = Integer.MIN_VALUE;
		for (int x = 0; x < size; x++) {
			for (int z = 0; z < size; z++) {
				int g = groundY(level, x0 + x, z0 + z);
				if (g == Integer.MIN_VALUE) {
					return Survey.unfit("no ground");
				}
				if (!level.getFluidState(new BlockPos(x0 + x, g + 1, z0 + z)).isEmpty()) {
					return Survey.unfit("water");
				}
				ground[x * size + z] = g;
				min = Math.min(min, g);
				max = Math.max(max, g);
			}
		}
		if (max - min > slopeLimit) {
			return Survey.unfit("slope");
		}
		int[] sorted = ground.clone();
		Arrays.sort(sorted);
		int median = sorted[sorted.length / 2];
		for (int x = -1; x <= size; x++) {
			for (int z = -1; z <= size; z++) {
				for (int y = min - 1; y <= max + 3; y++) {
					if (level.getBlockState(new BlockPos(x0 + x, y, z0 + z)).is(WARDING)) {
						return Survey.unfit("warded");
					}
				}
			}
		}
		return new Survey(ground, min, max, median, null);
	}

	/**
	 * Natural stuff a Builder may dig out to make room: air, plants, trees, earth, stone, sand,
	 * snow, ice, ores. Never a player's block, never a fluid, never anything built.
	 */
	public static boolean clearable(ServerLevel level, BlockPos pos, BlockState s) {
		if (s.isAir() || s.canBeReplaced()) {
			return !PlayerBlocks.placedByPlayer(level, pos) || s.isAir();
		}
		if (!s.getFluidState().isEmpty()) {
			return false;
		}
		boolean natural = s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES) || s.getBlock() instanceof VegetationBlock
				|| s.is(BlockTags.DIRT) || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.SAND)
				|| s.is(Blocks.GRAVEL) || s.is(Blocks.CLAY) || s.is(BlockTags.SNOW) || s.is(BlockTags.ICE)
				|| s.is(BlockTags.TERRACOTTA) || s.is(BlockTags.MOSS_REPLACEABLE) || s.is(Blocks.MOSS_BLOCK)
				|| s.is(BlockTags.COPPER_ORES) || s.is(BlockTags.IRON_ORES) || s.is(BlockTags.GOLD_ORES)
				|| s.is(Blocks.COAL_ORE) || s.is(Blocks.DEEPSLATE_COAL_ORE) || s.is(Blocks.REDSTONE_ORE) || s.is(Blocks.DEEPSLATE_REDSTONE_ORE)
				|| s.is(Blocks.LAPIS_ORE) || s.is(Blocks.DEEPSLATE_LAPIS_ORE) || s.is(Blocks.DIAMOND_ORE) || s.is(Blocks.DEEPSLATE_DIAMOND_ORE)
				|| s.is(Blocks.EMERALD_ORE) || s.is(Blocks.DEEPSLATE_EMERALD_ORE)
				|| s.is(Blocks.COBBLESTONE) || s.is(Blocks.MOSSY_COBBLESTONE) || s.is(Blocks.CALCITE) || s.is(Blocks.DRIPSTONE_BLOCK);
		return natural && !PlayerBlocks.placedByPlayer(level, pos);
	}

	/**
	 * Whether a cell of the given height can stand with its floor at {@code y}: the floor layer
	 * may be any full block or clearable ground, everything above must be clearable, and the
	 * city's own transient blocks (the Seed, probes, terminals) are fine.
	 */
	public static boolean volumeClear(ServerLevel level, int x0, int z0, int size, int y, int height, BlockPos seed) {
		for (int x = 0; x < size; x++) {
			for (int z = 0; z < size; z++) {
				BlockPos floor = new BlockPos(x0 + x, y, z0 + z);
				BlockState f = level.getBlockState(floor);
				boolean floorOk = (!f.isAir() && !f.is(Blocks.BARRIER) && f.isCollisionShapeFullBlock(level, floor) && !PlayerBlocks.placedByPlayer(level, floor))
						|| clearable(level, floor, f);
				if (!floorOk) {
					return false;
				}
				for (int dy = 1; dy < height; dy++) {
					BlockPos p = floor.above(dy);
					BlockState st = level.getBlockState(p);
					if (st.is(SeedCityBlocks.PROBE) || st.is(SeedCityBlocks.TERMINAL) || (p.equals(seed) && st.is(SeedCityBlocks.SEED))) {
						continue;
					}
					if (!clearable(level, p, st)) {
						return false;
					}
				}
			}
		}
		return true;
	}

	// ---- site work ---------------------------------------------------------------------------

	/** Digging: every non-air block inside the footprint volume that the blueprint leaves empty. */
	public static List<Placed> clears(ServerLevel level, BoundingBox box, Predicate<BlockPos> blueprintHas) {
		List<Placed> out = new ArrayList<>();
		for (int y = box.maxY(); y > box.minY(); y--) {
			for (int x = box.minX(); x <= box.maxX(); x++) {
				for (int z = box.minZ(); z <= box.maxZ(); z++) {
					BlockPos p = new BlockPos(x, y, z);
					if (blueprintHas.test(p)) {
						continue;
					}
					BlockState s = level.getBlockState(p);
					if (!s.isAir() && !s.is(SeedCityBlocks.PROBE) && !s.is(SeedCityBlocks.TERMINAL) && !s.is(SeedCityBlocks.SEED)) {
						out.add(new Placed(p, Blocks.AIR.defaultBlockState()));
					}
				}
			}
		}
		return out;
	}

	/**
	 * Foundation: under every footprint column, fill from the ground up to just below the floor
	 * with that column's floor block, so a cell on a slope stands on masonry rather than on air.
	 */
	public static List<Placed> foundation(ServerLevel level, BoundingBox box, java.util.function.Function<BlockPos, BlockState> floorBlock, int maxDepth) {
		List<Placed> out = new ArrayList<>();
		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				BlockState fill = floorBlock.apply(new BlockPos(x, box.minY(), z));
				for (int y = box.minY() - 1; y >= box.minY() - maxDepth; y--) {
					BlockPos p = new BlockPos(x, y, z);
					BlockState s = level.getBlockState(p);
					if (isGround(level, p, s)) {
						break;
					}
					if (!PlayerBlocks.placedByPlayer(level, p)) {
						out.add(new Placed(p, fill));
					}
				}
			}
		}
		out.sort(java.util.Comparator.comparingInt((Placed p) -> p.pos().getY()).thenComparingInt(p -> p.pos().getX()).thenComparingInt(p -> p.pos().getZ()));
		return out;
	}

	/**
	 * The earth apron (the biome surface rule): outside each open side, for {@code width} blocks
	 * out, earth is banked one step lower per block until it meets the ground, using whatever
	 * surface the land already has there (grass stays grass, sand stays sand). Higher ground is
	 * left alone: the cell is cut into the hillside.
	 */
	public static List<Placed> apron(ServerLevel level, BoundingBox box, List<Direction> openSides, int width, Predicate<BlockPos> insideCity) {
		List<Placed> out = new ArrayList<>();
		java.util.Set<BlockPos> seen = new java.util.HashSet<>();
		for (Direction side : openSides) {
			for (int d = 1; d <= width; d++) {
				int top = box.minY() - d;
				List<BlockPos> row = row(box, side, d);
				for (BlockPos column : row) {
					if (insideCity.test(column) || !seen.add(column)) {
						continue;
					}
					int g = groundY(level, column.getX(), column.getZ());
					if (g == Integer.MIN_VALUE || g >= top) {
						continue;
					}
					BlockState surface = level.getBlockState(new BlockPos(column.getX(), g, column.getZ()));
					BlockState under = level.getBlockState(new BlockPos(column.getX(), g - 1, column.getZ()));
					if (!isGround(level, new BlockPos(column.getX(), g - 1, column.getZ()), under)) {
						under = surface;
					}
					for (int y = g + 1; y <= top; y++) {
						BlockPos p = new BlockPos(column.getX(), y, column.getZ());
						BlockState s = level.getBlockState(p);
						if (!(s.isAir() || s.canBeReplaced() || s.getBlock() instanceof VegetationBlock || s.is(BlockTags.LEAVES))
								|| PlayerBlocks.placedByPlayer(level, p)) {
							break;
						}
						out.add(new Placed(p, y == top ? surface : under));
					}
				}
			}
		}
		return out;
	}

	/** The columns {@code d} blocks outside one side of a box, widened by d-1 on each end so corners round off. */
	private static List<BlockPos> row(BoundingBox box, Direction side, int d) {
		List<BlockPos> out = new ArrayList<>();
		int ext = d - 1;
		switch (side) {
			case NORTH -> {
				for (int x = box.minX() - ext; x <= box.maxX() + ext; x++) {
					out.add(new BlockPos(x, box.minY(), box.minZ() - d));
				}
			}
			case SOUTH -> {
				for (int x = box.minX() - ext; x <= box.maxX() + ext; x++) {
					out.add(new BlockPos(x, box.minY(), box.maxZ() + d));
				}
			}
			case WEST -> {
				for (int z = box.minZ() - ext; z <= box.maxZ() + ext; z++) {
					out.add(new BlockPos(box.minX() - d, box.minY(), z));
				}
			}
			case EAST -> {
				for (int z = box.minZ() - ext; z <= box.maxZ() + ext; z++) {
					out.add(new BlockPos(box.maxX() + d, box.minY(), z));
				}
			}
			default -> {
			}
		}
		return out;
	}

	// ---- fences and mobs ---------------------------------------------------------------------

	/** True when a warding block stands on the line between two points (checked column by column). */
	public static boolean crossesWarding(ServerLevel level, BlockPos from, BlockPos to) {
		int dx = to.getX() - from.getX();
		int dz = to.getZ() - from.getZ();
		int steps = Math.max(Math.abs(dx), Math.abs(dz));
		int yLo = Math.min(from.getY(), to.getY()) - 1;
		int yHi = Math.max(from.getY(), to.getY()) + 2;
		for (int i = 0; i <= steps; i++) {
			int x = from.getX() + (steps == 0 ? 0 : Math.round((float) dx * i / steps));
			int z = from.getZ() + (steps == 0 ? 0 : Math.round((float) dz * i / steps));
			if (!level.hasChunk(x >> 4, z >> 4)) {
				continue;
			}
			for (int y = yLo; y <= yHi; y++) {
				if (level.getBlockState(new BlockPos(x, y, z)).is(WARDING)) {
					return true;
				}
			}
		}
		return false;
	}

	/** A spot a ground mob can stand on near a point: solid below, two clear blocks, on the surface. */
	public static Optional<BlockPos> standable(ServerLevel level, BlockPos near, int minRadius, int radius, Predicate<BlockPos> avoid) {
		for (int r = minRadius; r <= radius; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					int x = near.getX() + dx;
					int z = near.getZ() + dz;
					if (!level.hasChunk(x >> 4, z >> 4)) {
						continue;
					}
					int g = groundY(level, x, z);
					if (g == Integer.MIN_VALUE) {
						continue;
					}
					BlockPos feet = new BlockPos(x, g + 1, z);
					if (Math.abs(feet.getY() - near.getY()) > 8 || avoid.test(feet)) {
						continue;
					}
					if (level.getBlockState(feet).isAir() && level.getBlockState(feet.above()).isAir() && level.getFluidState(feet).isEmpty()) {
						return Optional.of(feet);
					}
				}
			}
		}
		return Optional.empty();
	}

	/**
	 * The nearest block of a kind a Collector may mine: within {@code radius} of the centre, on or
	 * near the surface, with an open face to work from, not inside the city, not a player's, and
	 * not beyond a warding line. Ring by ring, fixed order, so the search is deterministic.
	 */
	public static Optional<BlockPos> findSource(ServerLevel level, BlockPos centre, Kind kind, int radius, Predicate<BlockPos> forbidden) {
		for (int r = 2; r <= radius; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					int x = centre.getX() + dx;
					int z = centre.getZ() + dz;
					if (!level.hasChunk(x >> 4, z >> 4)) {
						continue;
					}
					int g = groundY(level, x, z);
					if (g == Integer.MIN_VALUE) {
						continue;
					}
					// trees stand above the ground, stone shows at the surface: scan the tree height down to just under the turf
					int top = Math.min(level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1, g + 24);
					for (int y = top; y >= g - 2 && y >= level.getMinY(); y--) {
						BlockPos p = new BlockPos(x, y, z);
						BlockState s = level.getBlockState(p);
						if (!kind.matches(s) || forbidden.test(p) || PlayerBlocks.placedByPlayer(level, p) || !exposed(level, p)) {
							continue;
						}
						if (crossesWarding(level, centre, p)) {
							continue;
						}
						return Optional.of(p);
					}
				}
			}
		}
		return Optional.empty();
	}

	/** Another block of the same kind within reach of a mined one, so a Collector works a tree or a face, not one block. */
	public static Optional<BlockPos> nearbySource(ServerLevel level, BlockPos around, Kind kind, int reach, Predicate<BlockPos> forbidden) {
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(around.offset(-reach, -reach, -reach), around.offset(reach, reach, reach))) {
			BlockState s = level.getBlockState(p);
			if (!kind.matches(s) || forbidden.test(p) || PlayerBlocks.placedByPlayer(level, p) || !exposed(level, p)) {
				continue;
			}
			double d = p.distSqr(around);
			if (d < bestD) {
				bestD = d;
				best = p.immutable();
			}
		}
		return Optional.ofNullable(best);
	}

	private static boolean exposed(ServerLevel level, BlockPos p) {
		for (Direction d : Direction.values()) {
			BlockState n = level.getBlockState(p.relative(d));
			if (n.isAir() || n.is(BlockTags.LEAVES) || n.getBlock() instanceof VegetationBlock || n.canBeReplaced()) {
				return true;
			}
		}
		return false;
	}
}
