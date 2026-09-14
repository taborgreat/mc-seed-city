package net.tabor.seedcity.card;

import java.util.Locale;
import java.util.Optional;

/**
 * The twelve ops (design doc 15, 22.2). Fixed until Phase 4 is accepted. Arithmetic is analog
 * and saturating (docs/city-as-computer.md): SUB floors at 0, ADD ceils at 15, NOT is 15 - a,
 * AND is min, OR is max.
 */
public enum Opcode {
	SET, ADD, SUB, AND, OR, NOT, JMP, JZ, WAIT, OUT, IN, NEED;

	public static Optional<Opcode> parse(String word) {
		try {
			return Optional.of(valueOf(word.toUpperCase(Locale.ROOT)));
		} catch (IllegalArgumentException e) {
			return Optional.empty();
		}
	}

	/** Ops that route operands through an ALU cell, and which cell they need. */
	public boolean usesAlu() {
		return this == ADD || this == SUB || this == AND || this == OR || this == NOT;
	}
}
