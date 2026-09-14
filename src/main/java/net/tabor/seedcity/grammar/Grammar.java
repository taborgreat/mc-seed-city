package net.tabor.seedcity.grammar;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;
import net.tabor.seedcity.cell.Cell;
import net.tabor.seedcity.cell.Port;
import net.tabor.seedcity.cell.PortDir;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * Interface: Grammar (design doc 5.2, 19, 23). Given a frontier slot and what its neighbours
 * present on the shared faces, returns a legal cell and rotation, weighted by district.
 *
 * <p>Legality per face: an out port must meet an in port or a blank wall; out-out and in-in are
 * illegal. A face with no neighbour yet accepts anything. Cells with zero weight in the district
 * (core, clock tower) are never chosen here; the planner places those by force.
 *
 * <p>Two rules shape the city beyond legality:
 * <ul>
 *   <li><b>A live output is never wasted.</b> If any candidate would listen to a live neighbour
 *   output, only such candidates are considered. Dead ends (plazas, warehouses) fill the quiet
 *   slots.</li>
 *   <li><b>Signal likes to travel.</b> Among live candidates, ones that pass the signal on
 *   (outputs toward open sides, especially straight through) are weighted up, and a goal
 *   multiplies its kind.</li>
 * </ul>
 *
 * <p>Deterministic: the candidate order is fixed (cell id, then rotation) and all randomness
 * comes from the supplied {@link Random}, which the planner seeds per slot.
 */
public final class Grammar {
	private static final Direction[] SIDES = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

	/**
	 * How a rotated cell sits against its neighbours.
	 *
	 * @param liveInputs inputs mated to live outputs
	 * @param deadInputs inputs mated to outputs that carry nothing
	 * @param feeds      outputs mated to existing neighbour inputs
	 * @param openOuts   outputs facing empty sides: where the signal could go next
	 * @param straight   a live input has an output directly opposite it: a street continues
	 * @param sameWidth  every mated pair agrees on width
	 */
	public record Fit(boolean legal, int liveInputs, int deadInputs, int feeds, int openOuts, boolean straight, boolean sameWidth) {
		static final Fit ILLEGAL = new Fit(false, 0, 0, 0, 0, false, true);
	}

	private record Candidate(Choice choice, double weight) {
	}

	private Grammar() {
	}

	public static Optional<Choice> choose(FrontierSlot slot, List<Constraint> neighbours, Optional<Goal> goal, Random rng, Collection<Cell> library) {
		return choose(slot, neighbours, goal, Map.of(), rng, library);
	}

	/**
	 * @param wanted cells the current program still needs (docs/city-as-computer.md, demand);
	 *               each is weighted up strongly wherever it is legal, even in quiet slots
	 */
	public static Optional<Choice> choose(FrontierSlot slot, List<Constraint> neighbours, Optional<Goal> goal,
										  Map<net.minecraft.resources.Identifier, Integer> wanted, Random rng, Collection<Cell> library) {
		Map<Direction, Constraint> bySide = new EnumMap<>(Direction.class);
		for (Constraint c : neighbours) {
			bySide.put(c.side(), c);
		}
		List<Cell> cells = new ArrayList<>(library);
		cells.sort(Comparator.comparing(c -> c.id().toString()));

		List<Candidate> live = new ArrayList<>();
		List<Candidate> quiet = new ArrayList<>();
		for (Cell cell : cells) {
			int base = cell.definition().weight(slot.district());
			int want = wanted.getOrDefault(cell.id(), 0);
			if (base <= 0) {
				continue;   // zoning holds even for wanted cells: ALU cells grow in the Forge, vaults in Storage
			}
			for (Rotation rotation : Rotation.values()) {
				if (slot.rejected().contains(FrontierSlot.rejectKey(cell.id().toString(), rotation.ordinal()))) {
					continue;
				}
				Fit fit = fit(cell, rotation, bySide);
				if (!fit.legal()) {
					continue;
				}
				double w = base;
				if (want > 0) {
					w *= 6 * want;
				}
				if (fit.liveInputs() > 0) {
					w *= 1 + 0.75 * fit.openOuts();
					if (fit.straight()) {
						w *= 1.5;
					}
					if (fit.sameWidth()) {
						w *= 1.25;
					}
					if (goal.isPresent() && goal.get().kind() == cell.definition().kind()) {
						w *= goal.get().weightMul();
					}
					live.add(new Candidate(new Choice(cell, rotation), w));
				} else {
					if (fit.feeds() > 0) {
						w *= 1.5;
					}
					// a wanted cell competes with the live pool even when nothing feeds it yet
					(want > 0 ? live : quiet).add(new Candidate(new Choice(cell, rotation), w));
				}
			}
		}
		List<Candidate> pool = live.isEmpty() ? quiet : live;
		if (pool.isEmpty()) {
			return Optional.empty();
		}
		double total = 0;
		for (Candidate c : pool) {
			total += c.weight();
		}
		double pick = rng.nextDouble() * total;
		for (Candidate c : pool) {
			pick -= c.weight();
			if (pick <= 0) {
				return Optional.of(c.choice());
			}
		}
		return Optional.of(pool.getLast().choice());
	}

	/** Checks the rotated cell against the constraints and counts how it connects. */
	public static Fit fit(Cell cell, Rotation rotation, Map<Direction, Constraint> bySide) {
		List<Port> ports = cell.ports(rotation);
		int live = 0;
		int dead = 0;
		int feeds = 0;
		int openOuts = 0;
		boolean straight = false;
		boolean sameWidth = true;
		for (Direction side : SIDES) {
			Constraint c = bySide.get(side);
			Port ours = portOn(ports, side);
			if (c == null) {
				if (ours != null && ours.dir() == PortDir.OUT) {
					openOuts++;
				}
				continue;
			}
			if (!c.hasPort() || ours == null) {
				continue;   // a port against a blank wall dead-ends: allowed
			}
			if (!ours.compatibleWith(c.port())) {
				return Fit.ILLEGAL;
			}
			sameWidth &= ours.sameWidth(c.port());
			if (ours.dir() == PortDir.IN) {
				if (c.live()) {
					live++;
					Port opposite = portOn(ports, side.getOpposite());
					if (opposite != null && opposite.dir() == PortDir.OUT && !bySide.containsKey(side.getOpposite())) {
						straight = true;
					}
				} else {
					dead++;
				}
			} else {
				feeds++;
			}
		}
		return new Fit(true, live, dead, feeds, openOuts, straight, sameWidth);
	}

	public static Port portOn(List<Port> ports, Direction side) {
		for (Port p : ports) {
			if (p.face() == side) {
				return p;
			}
		}
		return null;
	}
}
