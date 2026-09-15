package net.tabor.seedcity.cell;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Interface: Cell (design doc 19, 23). A verified redstone module: structure NBT plus sidecar.
 * The only unit anything in the mod knows how to place.
 */
public final class Cell {
	/** One block of the structure in cell-local coordinates. Air and structure voids are omitted. */
	public record CellBlock(BlockPos pos, BlockState state) {
	}

	private final CellDefinition definition;
	private final StructureTemplate template;
	private final List<CellBlock> blocks;
	private final List<BlockPos> voids;

	public Cell(CellDefinition definition, StructureTemplate template, List<CellBlock> blocks) {
		this(definition, template, blocks, List.of());
	}

	/** @param voids structure-void positions: the land there is left as it is, above and below */
	public Cell(CellDefinition definition, StructureTemplate template, List<CellBlock> blocks, List<BlockPos> voids) {
		this.definition = definition;
		this.template = template;
		this.blocks = order(blocks);
		this.voids = List.copyOf(voids);
	}

	/**
	 * Cell-local columns (x, z packed as {@code x * 1024 + z}) that hold only structure void, after
	 * rotation: site work leaves those columns alone, so a lane can keep the grass on its verges.
	 */
	public java.util.Set<Long> voidColumns(Rotation rotation) {
		java.util.Set<Long> built = new java.util.HashSet<>();
		for (CellBlock b : blocks(rotation)) {
			built.add(column(b.pos()));
		}
		java.util.Set<Long> out = new java.util.HashSet<>();
		for (BlockPos v : voids) {
			long c = column(v.rotate(rotation));
			if (!built.contains(c)) {
				out.add(c);
			}
		}
		return out;
	}

	public static long column(BlockPos p) {
		return (long) p.getX() * 1024 + p.getZ();
	}

	/**
	 * Build order for a Builder: full blocks bottom-up first, then everything that needs support
	 * (dust, torches, diodes, lanterns, glass), also bottom-up. Deterministic.
	 */
	private static List<CellBlock> order(List<CellBlock> in) {
		List<CellBlock> out = new ArrayList<>(in);
		Comparator<CellBlock> byPos = Comparator.<CellBlock>comparingInt(b -> b.pos().getY())
				.thenComparingInt(b -> b.pos().getZ())
				.thenComparingInt(b -> b.pos().getX());
		out.sort(Comparator.<CellBlock>comparingInt(b -> b.state().canOcclude() ? 0 : 1).thenComparing(byPos));
		return List.copyOf(out);
	}

	public Identifier id() {
		return definition.id();
	}

	public CellDefinition definition() {
		return definition;
	}

	public StructureTemplate template() {
		return template;
	}

	public Vec3i size() {
		return definition.size();
	}

	/** Blocks in build order, cell-local, unrotated. */
	public List<CellBlock> blocks() {
		return blocks;
	}

	/** Blocks in build order, rotated about the cell origin. */
	public List<CellBlock> blocks(Rotation rotation) {
		if (rotation == Rotation.NONE) {
			return blocks;
		}
		List<CellBlock> out = new ArrayList<>(blocks.size());
		for (CellBlock b : blocks) {
			out.add(new CellBlock(b.pos().rotate(rotation), b.state().rotate(rotation)));
		}
		return out;
	}

	/** World-space box the cell occupies when its local origin is placed at {@code origin}. */
	public BoundingBox footprint(BlockPos origin, Rotation rotation) {
		Vec3i s = definition.size();
		BlockPos far = new BlockPos(s.getX() - 1, s.getY() - 1, s.getZ() - 1).rotate(rotation).offset(origin);
		return BoundingBox.fromCorners(origin, far);
	}

	/**
	 * Offset from a footprint's min corner to the placement origin for a rotation, so a rotated
	 * cell still fills the same box (rotation about the origin swings it negative).
	 */
	public static BlockPos rotationShift(Rotation rotation, Vec3i size) {
		return switch (rotation) {
			case NONE -> BlockPos.ZERO;
			case CLOCKWISE_90 -> new BlockPos(size.getZ() - 1, 0, 0);
			case CLOCKWISE_180 -> new BlockPos(size.getX() - 1, 0, size.getZ() - 1);
			case COUNTERCLOCKWISE_90 -> new BlockPos(0, 0, size.getX() - 1);
		};
	}

	/** Ports in cell-local coordinates after rotation about the origin. */
	public List<Port> ports(Rotation rotation) {
		return definition.ports().stream().map(p -> p.rotated(rotation)).toList();
	}

	/**
	 * Writes the whole structure into the world at once (commands, tests, repairs). Structure
	 * voids are skipped so a cell can enclose an existing block such as the Seed.
	 */
	public boolean place(ServerLevel level, BlockPos origin, Rotation rotation) {
		StructurePlaceSettings settings = new StructurePlaceSettings()
				.setRotation(rotation)
				.setIgnoreEntities(true)
				.setKnownShape(false)
				.addProcessor(new BlockIgnoreProcessor(List.of(Blocks.STRUCTURE_VOID)));
		return template.placeInWorld(level, origin, origin, settings, level.getRandom(), Block.UPDATE_ALL);
	}

	@Override
	public String toString() {
		return id().toString();
	}
}
