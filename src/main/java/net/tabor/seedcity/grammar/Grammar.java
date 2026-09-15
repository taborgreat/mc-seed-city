package net.tabor.seedcity.grammar;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;
import net.tabor.seedcity.cell.Cell;
import net.tabor.seedcity.cell.CellKind;
import net.tabor.seedcity.cell.Port;
import net.tabor.seedcity.cell.PortDir;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.function.Function;

/**
 * Interface: Grammar (design doc 5.2, 19, 23). Given a frontier slot and what its neighbours
 * present on the shared faces, returns a legal cell and rotation, weighted by district.
 *
 * <p>Legality per port: an out port must meet an in port or a blank wall; out-out and in-in are
 * illegal. A face with no neighbour yet accepts anything. Cells with zero weight in the district
 * (core, clock tower) are never chosen here; the planner places those by force.
 *
 * <p>Cells may span several slots (a 7x14 vault is two slots along a street). The planner
 * supplies, per candidate, the constraints on every outer face of the footprint it would cover,
 * or nothing when there is no room for it.
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
	public static final int SLOT = 7;

	/** One face of one slot of a candidate footprint. */
	public record Face(int offset, Direction side) {
	}

	/**
	 * How a rotated cell sits against its neighbours.
	 *
	 * @param liveInputs inputs mated to live outputs
	 * @param deadInputs inputs mated to outputs that carry nothing
	 * @param feeds      outputs mated to existing neighbour inputs
	 * @param openOuts   outputs facing empty sides: where the signal could go next
	 * @param straight   a live input has an output on the opposite face: a street continues
	 * @param sameWidth  every mated pair agrees on width
	 */
	public record Fit(boolean legal, int liveInputs, int deadInputs, int feeds, int openOuts, boolean straight, boolean sameWidth, int busFed) {
		static final Fit ILLEGAL = new Fit(false, 0, 0, 0, 0, false, true, 0);
	}

	private record Candidate(Choice choice, double weight, boolean wanted, boolean fed, boolean busFed) {
	}

	private Grammar() {
	}

	/** A port of a rotated cell in footprint terms: which face of which slot, where along it, how high. */
	public record BoxPort(Port port, Face face, int along, int y) {
		public static BoxPort of(Port p, Rotation rotation, net.minecraft.core.Vec3i size) {
			Port r = p.rotated(rotation);
			BlockPos box = r.pos().offset(Cell.rotationShift(rotation, size));
			int a = r.face().getAxis() == Direction.Axis.Z ? box.getX() : box.getZ();
			return new BoxPort(r, new Face(a / SLOT, r.face()), a % SLOT, box.getY());
		}
	}

	public static List<BoxPort> boxPorts(Cell cell, Rotation rotation) {
		List<BoxPort> out = new ArrayList<>();
		for (Port p : cell.definition().ports()) {
			out.add(BoxPort.of(p, rotation, cell.size()));
		}
		return out;
	}

	/**
	 * @param room     for a candidate, the constraints on the outer faces of the footprint it
	 *                 would cover, or empty when the city has no room for that footprint
	 * @param wanted   cells the current program still needs (docs/city-as-computer.md, demand);
	 *                 each is weighted up strongly wherever it is legal, even in quiet slots
	 * @param existing how many of each cell the city already has or has planned; actuators, decor
	 *                 and storage are discounted the more of them there are, so a city does not
	 *                 line up four bridges, unless a program wants that cell
	 */
	public static Optional<Choice> choose(FrontierSlot slot, Function<Choice, Optional<Map<Face, Constraint>>> room, Optional<Goal> goal,
										  Map<net.minecraft.resources.Identifier, Integer> wanted,
										  Map<net.minecraft.resources.Identifier, Integer> existing, Random rng, Collection<Cell> library) {
		List<Cell> cells = new ArrayList<>(library);
		cells.sort(Comparator.comparing(c -> c.id().toString()));

		// while the program still wants RAM, the bus must stay extensible: no plain streets or dead
		// ends until the vaults stand, only branches (a tap for the next vault) and the vaults themselves
		boolean wantsBus = false;
		for (Cell cell : cells) {
			if (wanted.getOrDefault(cell.id(), 0) > 0 && hasBusInput(cell)) {
				wantsBus = true;
			}
		}
		List<Candidate> live = new ArrayList<>();
		List<Candidate> quiet = new ArrayList<>();
		for (Cell cell : cells) {
			int base = cell.definition().weight(slot.district());
			int want = wanted.getOrDefault(cell.id(), 0);
			if (base <= 0) {
				continue;   // zoning holds even for wanted cells: ALU cells grow in the Forge, vaults in RAM
			}
			if (wantsBus && want == 0 && hasBusInput(cell) && busOutputs(cell) != 2) {
				continue;   // a dead end (0 select outputs) or a plain street (1); a branch has 2
			}
			for (Rotation rotation : Rotation.values()) {
				if (slot.rejected().contains(FrontierSlot.rejectKey(cell.id().toString(), rotation.ordinal()))) {
					continue;
				}
				Choice choice = new Choice(cell, rotation);
				Optional<Map<Face, Constraint>> bySide = room.apply(choice);
				if (bySide.isEmpty()) {
					continue;
				}
				Fit fit = fit(cell, rotation, bySide.get());
				if (!fit.legal()) {
					continue;
				}
				if (hasBusInput(cell) && fit.busFed() == 0) {
					continue;   // a bus cell off the bus is a dead street: only grow them where a live lane arrives
				}
				double w = base;
				if (want > 0) {
					w *= 6 * want;
				} else {
					int have = existing.getOrDefault(cell.id(), 0);
					CellKind kind = cell.definition().kind();
					if (have > 0 && (kind == CellKind.ACTUATOR || kind == CellKind.DECOR || kind == CellKind.STORAGE || kind == CellKind.SENSOR)) {
						w /= 1 + 0.75 * have;
					}
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
					live.add(new Candidate(choice, w, want > 0, true, fit.busFed() > 0));
				} else {
					if (fit.feeds() > 0) {
						w *= 1.5;
					}
					// a wanted cell competes with the live pool even when nothing feeds it yet
					(want > 0 ? live : quiet).add(new Candidate(choice, w, want > 0, false, false));
				}
			}
		}
		// Priority: a slot a live output reaches takes a cell that listens; among those a bus lane
		// is never wasted on anything but a bus cell; and among those the program's wants decide.
		// A wanted cell nothing feeds only takes a slot nothing feeds.
		List<Candidate> fed = new ArrayList<>();
		List<Candidate> unfedWanted = new ArrayList<>();
		for (Candidate c : live) {
			(c.fed() ? fed : unfedWanted).add(c);
		}
		List<Candidate> onBus = new ArrayList<>();
		List<Candidate> wantedAll = new ArrayList<>();
		for (Candidate c : live) {
			if (c.busFed()) {
				onBus.add(c);
			}
			if (c.wanted()) {
				wantedAll.add(c);
			}
		}
		List<Candidate> pool;
		if (wantsBus && !onBus.isEmpty()) {
			// while RAM is wanted the lane is sacred: only bus cells, the wanted vault first
			pool = onBus;
			List<Candidate> wantedHere = new ArrayList<>();
			for (Candidate c : pool) {
				if (c.wanted()) {
					wantedHere.add(c);
				}
			}
			if (!wantedHere.isEmpty()) {
				pool = wantedHere;
			}
		} else if (!wantedAll.isEmpty()) {
			pool = wantedAll;   // the program's needs beat an idle extension of any street
		} else if (!fed.isEmpty()) {
			pool = fed;
		} else {
			pool = quiet;   // unfedWanted is empty here: it is a subset of wantedAll
		}
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

	/** Checks the rotated cell against the constraints on its footprint's faces and counts how it connects. */
	public static Fit fit(Cell cell, Rotation rotation, Map<Face, Constraint> bySide) {
		List<BoxPort> ports = boxPorts(cell, rotation);
		int live = 0;
		int dead = 0;
		int feeds = 0;
		int openOuts = 0;
		int busFed = 0;
		boolean straight = false;
		boolean sameWidth = true;
		for (BoxPort ours : ports) {
			Constraint c = bySide.get(ours.face());
			if (c == null) {
				if (ours.port().dir() == PortDir.OUT) {
					openOuts++;
				}
				continue;
			}
			Constraint.Facing theirs = c.meeting(ours.along(), ours.y());
			if (theirs == null) {
				continue;   // a port against a blank wall dead-ends: allowed
			}
			if (!ours.port().compatibleWith(theirs.port())) {
				return Fit.ILLEGAL;
			}
			if (ours.port().isBusLane() && ours.port().dir() == PortDir.IN && theirs.kind() == CellKind.CORE && busOutputs(cell) == 0) {
				return Fit.ILLEGAL;   // the Core's gate is never capped by a dead end or a vault: the bus must be able to grow
			}
			sameWidth &= ours.port().sameWidth(theirs.port());
			if (ours.port().dir() == PortDir.IN) {
				if (theirs.live()) {
					live++;
					if (ours.port().isBusLane()) {
						busFed++;
					}
					Direction opposite = ours.face().side().getOpposite();
					for (BoxPort o : ports) {
						if (o.port().dir() == PortDir.OUT && o.face().side() == opposite && !bySide.containsKey(o.face())) {
							straight = true;
						}
					}
				} else {
					dead++;
				}
			} else {
				feeds++;
			}
		}
		return new Fit(true, live, dead, feeds, openOuts, straight, sameWidth, busFed);
	}

	/** Whether the cell listens to a bus lane (a street, branch, end or RAM vault). */
	public static boolean hasBusInput(Cell cell) {
		for (Port p : cell.definition().ports()) {
			if (p.dir() == PortDir.IN && p.isBusLane()) {
				return true;
			}
		}
		return false;
	}

	/** How many select outputs a cell has: 0 for a dead end or vault, 1 for a street, 2 for a branch. */
	public static int busOutputs(Cell cell) {
		int n = 0;
		for (Port p : cell.definition().ports()) {
			if (p.dir() == PortDir.OUT && p.name().startsWith("sel_")) {
				n++;
			}
		}
		return n;
	}

	/** The first port on a face; single-port callers. */
	public static Port portOn(List<Port> ports, Direction side) {
		for (Port p : ports) {
			if (p.face() == side) {
				return p;
			}
		}
		return null;
	}

	/** Every port on a face, in declaration order. */
	public static List<Port> portsOn(List<Port> ports, Direction side) {
		List<Port> out = new ArrayList<>();
		for (Port p : ports) {
			if (p.face() == side) {
				out.add(p);
			}
		}
		return out;
	}
}
