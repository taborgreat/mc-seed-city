package net.tabor.seedcity.build;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.tabor.seedcity.cell.Cell;
import net.tabor.seedcity.cell.Placement;
import net.tabor.seedcity.core.CityState;
import net.tabor.seedcity.core.Terrain;
import net.tabor.seedcity.verify.Integrity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Interface: BuildTask (design doc 19, 23, 24). A cell at a position, executed one block at a
 * time by a Builder or a Warden. Growth, repair, and player blueprints all go through this one
 * type. Blocks that are already right are skipped, so the same task repairs as well as builds.
 *
 * <p>A growth task also carries the site work the land asks for (Phase 5): digging out what
 * stands in the way, a foundation under the floor, and the earth apron on the city's open sides.
 * Natural blocks dug out are salvaged into the ledger.
 */
public final class BuildTask {
	/** Material a task consumes, in the three currencies the city tracks. */
	public record Cost(int redstone, int stone, int wood) {
		public static Cost of(Map<String, Integer> m) {
			return new Cost(m.getOrDefault("redstone", 0), m.getOrDefault("stone", 0), m.getOrDefault("wood", 0));
		}

		public Cost plus(Terrain.Kind kind, int amount) {
			return switch (kind) {
				case REDSTONE -> new Cost(redstone + amount, stone, wood);
				case STONE -> new Cost(redstone, stone + amount, wood);
				case WOOD -> new Cost(redstone, stone, wood + amount);
			};
		}

		public static final Cost FREE = new Cost(0, 0, 0);
	}

	private record Step(BlockPos pos, BlockState state) {
	}

	private final Placement placement;
	private final CityState.SlotKey slot;
	private final Cost cost;
	private final List<Step> steps;
	private final boolean repair;
	private int next;
	private Cost salvage = Cost.FREE;

	/** A normal build. {@code omit} is a cell-local block to leave out (a planted fault), or null. */
	public BuildTask(Placement placement, CityState.SlotKey slot, BlockPos omit) {
		this(placement, slot, omit, false, List.of(), List.of());
	}

	private BuildTask(Placement placement, CityState.SlotKey slot, BlockPos omit, boolean repair, List<Terrain.Placed> before, List<Terrain.Placed> after) {
		this.placement = placement;
		this.slot = slot;
		this.repair = repair;
		this.cost = repair ? Cost.FREE : Cost.of(placement.cell().definition().cost());
		BlockPos omitWorld = omit == null ? null : placement.origin().offset(omit.rotate(placement.rotation()));
		List<Step> s = new ArrayList<>();
		for (Terrain.Placed p : before) {
			s.add(new Step(p.pos(), p.state()));
		}
		for (Cell.CellBlock b : placement.cell().blocks(placement.rotation())) {
			BlockPos world = placement.origin().offset(b.pos());
			if (world.equals(omitWorld)) {
				continue;
			}
			s.add(new Step(world, b.state()));
		}
		for (Terrain.Placed p : after) {
			s.add(new Step(p.pos(), p.state()));
		}
		this.steps = List.copyOf(s);
	}

	public BuildTask(Placement placement, CityState.SlotKey slot) {
		this(placement, slot, null, false, List.of(), List.of());
	}

	/** A repair: same blueprint, no material cost; only differing blocks get placed. */
	public static BuildTask repair(Placement placement, CityState.SlotKey slot) {
		return new BuildTask(placement, slot, null, true, List.of(), List.of());
	}

	/**
	 * Growth on real ground: dig out the footprint, lay a foundation, build the cell, then bank
	 * the earth apron against its open sides. {@code openSides} are the faces with nothing of the
	 * city beyond them; {@code insideCity} keeps the apron off other slots.
	 */
	public static BuildTask growth(ServerLevel level, Placement placement, CityState.SlotKey slot, BlockPos omit,
								   List<net.minecraft.core.Direction> openSides, java.util.function.Predicate<BlockPos> insideCity,
								   int foundationDepth, int apronWidth) {
		Set<BlockPos> has = new HashSet<>();
		Map<BlockPos, BlockState> floor = new java.util.HashMap<>();
		for (Cell.CellBlock b : placement.cell().blocks(placement.rotation())) {
			BlockPos world = placement.origin().offset(b.pos());
			has.add(world);
			if (world.getY() == placement.footprint().minY() && b.state().canOcclude()) {
				floor.put(world, b.state());
			}
		}
		BlockState defaultFloor = Blocks.STONE_BRICKS.defaultBlockState();
		// columns the blueprint leaves to the land (structure void): neither dug nor founded
		Set<Long> keep = new HashSet<>();
		for (long c : placement.cell().voidColumns(placement.rotation())) {
			int x = (int) (c / 1024);
			int z = (int) (c - (long) x * 1024);
			keep.add(Cell.column(placement.origin().offset(x, 0, z)));
		}
		java.util.function.Predicate<BlockPos> kept = p -> keep.contains(Cell.column(p));
		List<Terrain.Placed> before = new ArrayList<>();
		before.addAll(Terrain.clears(level, placement.footprint(), p -> has.contains(p) || kept.test(p)));
		before.addAll(Terrain.foundation(level, placement.footprint(), p -> floor.getOrDefault(p, defaultFloor), foundationDepth, kept));
		List<Terrain.Placed> after = apronWidth > 0 ? Terrain.apron(level, placement.footprint(), openSides, apronWidth, insideCity) : List.of();
		return new BuildTask(placement, slot, omit, false, before, after);
	}

	public Placement placement() {
		return placement;
	}

	public CityState.SlotKey slot() {
		return slot;
	}

	public Cost cost() {
		return cost;
	}

	/** Ledger units recovered from natural blocks dug out while building. */
	public Cost salvage() {
		return salvage;
	}

	public boolean isRepair() {
		return repair;
	}

	public boolean done() {
		return next >= steps.size();
	}

	public int remaining() {
		return steps.size() - next;
	}

	public double progress() {
		return steps.isEmpty() ? 1.0 : (double) next / steps.size();
	}

	/** The block that will be placed next, or the cell centre when finished. */
	public BlockPos nextPos() {
		if (done()) {
			return placement.origin().offset(3, 1, 3);
		}
		return steps.get(next).pos();
	}

	/**
	 * Places the next block with its placement sound. Blocks already equivalent to the blueprint
	 * are skipped without consuming a step of time. Returns true when the cell is complete.
	 */
	public boolean step(ServerLevel level) {
		while (!done()) {
			Step s = steps.get(next++);
			BlockState old = level.getBlockState(s.pos());
			if (Integrity.equivalent(s.state(), old)) {
				continue;
			}
			if (s.state().isAir()) {
				Terrain.Kind kind = Terrain.Kind.of(old);
				if (kind != null) {
					salvage = salvage.plus(kind, kind.yield);
				}
				level.setBlock(s.pos(), s.state(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
				level.playSound(null, s.pos().getX() + 0.5, s.pos().getY() + 0.5, s.pos().getZ() + 0.5,
						old.getSoundType().getBreakSound(), SoundSource.BLOCKS);
				break;
			}
			level.setBlock(s.pos(), s.state(), Block.UPDATE_ALL);
			Identifier loot = placement.cell().definition().loot();
			if (loot != null && s.state().is(Blocks.CHEST)) {
				RandomizableContainer.setBlockEntityLootTable(level, level.getRandom(), s.pos(), ResourceKey.create(Registries.LOOT_TABLE, loot));
			}
			level.playSound(null, s.pos().getX() + 0.5, s.pos().getY() + 0.5, s.pos().getZ() + 0.5,
					s.state().getSoundType().getPlaceSound(), SoundSource.BLOCKS);
			break;
		}
		return done();
	}
}
