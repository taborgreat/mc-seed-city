package net.tabor.seedcity.grammar;

import net.minecraft.core.Direction;
import net.tabor.seedcity.cell.Port;

import java.util.List;

/**
 * What a neighbour presents on one face of one slot of a candidate footprint: the ports facing
 * us (a street has one in the middle, a bus has three) or a blank wall. Absence of a constraint
 * for a face means no neighbour is there yet.
 *
 * @param offset which slot of the candidate's footprint along that face (0 for the anchor; a
 *               7x14 cell facing north has offsets 0 and 1 on its north face)
 * @param side   the face, pointing toward the neighbour
 * @param facing the neighbour ports on that face
 */
public record Constraint(int offset, Direction side, List<Facing> facing, boolean street, java.util.Set<Integer> sensitive) {
	/** A neighbour that is not a street (or a wall). */
	public Constraint(int offset, Direction side, List<Facing> facing) {
		this(offset, side, facing, false, java.util.Set.of());
	}

	public Constraint(int offset, Direction side, List<Facing> facing, boolean street) {
		this(offset, side, facing, street, java.util.Set.of());
	}

	/** Key for a position on a shared face: where along the edge, how high. */
	public static int at(int along, int y) {
		return along * 256 + y;
	}

	/**
	 * True when the neighbour's wall at (along, y) has wiring right behind it: an output pointed
	 * there would power the wall block and leak into the neighbour's circuit.
	 */
	public boolean sensitiveAt(int along, int y) {
		return sensitive.contains(at(along, y));
	}

	/**
	 * A neighbour port as seen from our side of the shared edge.
	 *
	 * @param along position along the shared edge within the slot, 0..6
	 * @param y     height above our floor
	 * @param live  true when it is an output that carries a signal
	 * @param kind  the kind of the neighbour cell, or null when unknown
	 */
	public record Facing(Port port, int along, int y, boolean live, net.tabor.seedcity.cell.CellKind kind) {
		public Facing(Port port, int along, int y, boolean live) {
			this(port, along, y, live, null);
		}
	}

	public Constraint {
		facing = List.copyOf(facing);
		sensitive = java.util.Set.copyOf(sensitive);
	}

	public static Constraint wall(int offset, Direction side) {
		return new Constraint(offset, side, List.of());
	}

	public boolean hasPort() {
		return !facing.isEmpty();
	}

	/** The neighbour port that meets a port of ours at (along, y), if any. */
	public Facing meeting(int along, int y) {
		for (Facing f : facing) {
			if (f.along() == along && f.y() == y) {
				return f;
			}
		}
		return null;
	}
}
