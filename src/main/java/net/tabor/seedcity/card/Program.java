package net.tabor.seedcity.card;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** A parsed card: instructions, label targets, and what the program touches. */
public record Program(List<Instr> code, Map<String, Integer> labels, String source) {
	public Program {
		code = List.copyOf(code);
		labels = Map.copyOf(labels);
	}

	public Set<Integer> registersUsed() {
		Set<Integer> out = new TreeSet<>();
		for (Instr i : code) {
			if (i.hasRd()) {
				out.add(i.rd());
			}
			if (i.src() != null && i.src().register()) {
				out.add(i.src().value());
			}
		}
		return out;
	}

	public Set<Opcode> opsUsed() {
		Set<Opcode> out = new TreeSet<>();
		for (Instr i : code) {
			out.add(i.op());
		}
		return out;
	}

	public boolean isEmpty() {
		return code.isEmpty();
	}
}
