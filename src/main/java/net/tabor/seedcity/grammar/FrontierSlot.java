package net.tabor.seedcity.grammar;

import net.minecraft.core.Direction;

import java.util.Set;

/**
 * A slot the planner wants filled: where it is, which district it lies in, which (cell, rotation)
 * pairs have already failed verification there, which of its sides face the world outside the
 * city (the perimeter, where walls stand with their backs out), and whether it is a gate site
 * (an avenue slot at the Core's ring or at the city's edge).
 */
public record FrontierSlot(int x, int z, String district, Set<String> rejected, Set<Direction> edges, boolean gateSite) {
	public FrontierSlot(int x, int z, String district, Set<String> rejected) {
		this(x, z, district, rejected, Set.of(), false);
	}

	public FrontierSlot {
		rejected = Set.copyOf(rejected);
		edges = Set.copyOf(edges);
	}

	public static String rejectKey(String cellId, int rotationOrdinal) {
		return cellId + "/" + rotationOrdinal;
	}
}
