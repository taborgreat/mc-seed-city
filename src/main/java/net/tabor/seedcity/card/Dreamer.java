package net.tabor.seedcity.card;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * L3, the city's dreams (design doc 14): with the card slot empty and the frontier idle, the
 * Core composes a card from the fragment library over the hardware it already has, and runs it
 * if it parses. Pure: given what exists and a seeded random, it returns text, or nothing when
 * the fragments cannot be satisfied. Whether the card binds is the Executor's judgement.
 */
public final class Dreamer {
	private static final Pattern PLACEHOLDER = Pattern.compile("\\$([ASRNL])(\\d+)");

	/** What a dream may be written over: port names as cards spell them, how many vaults there are, whether the ALU exists. */
	public record Hardware(List<String> actuators, List<String> sensors, int registers, boolean alu) {
	}

	/** A composed card and its title. */
	public record Dream(String title, String text, List<String> fragments) {
	}

	private Dreamer() {
	}

	/**
	 * Composes dream number {@code n} for a city from one to three fragments the hardware can
	 * satisfy, all wrapped in one loop. Deterministic for (fragments, hardware, rng).
	 */
	public static Optional<Dream> compose(Map<String, String> library, Hardware hw, Random rng, int n) {
		List<String> usable = new ArrayList<>();
		for (Map.Entry<String, String> e : library.entrySet()) {
			Needs needs = needsOf(e.getValue());
			if (needs.a <= hw.actuators().size() && needs.s <= hw.sensors().size() && needs.r <= hw.registers() && (!needs.alu || hw.alu())) {
				usable.add(e.getKey());
			}
		}
		if (usable.isEmpty()) {
			return Optional.empty();
		}
		int count = 1 + rng.nextInt(Math.min(3, usable.size()));
		List<String> chosen = new ArrayList<>();
		List<String> pool = new ArrayList<>(usable);
		for (int i = 0; i < count; i++) {
			chosen.add(pool.remove(rng.nextInt(pool.size())));
		}
		StringBuilder text = new StringBuilder();
		text.append("; Dream #").append(n).append(": ").append(String.join(" + ", chosen)).append('\n');
		text.append("dream:\n");
		int register = 0;
		for (int f = 0; f < chosen.size(); f++) {
			String body = library.get(chosen.get(f));
			Map<String, String> fill = new LinkedHashMap<>();
			Needs needs = needsOf(body);
			List<String> acts = pick(hw.actuators(), needs.a, rng);
			List<String> sens = pick(hw.sensors(), needs.s, rng);
			for (int i = 1; i <= needs.a; i++) {
				fill.put("A" + i, acts.get(i - 1));
			}
			for (int i = 1; i <= needs.s; i++) {
				fill.put("S" + i, sens.get(i - 1));
			}
			for (int i = 1; i <= needs.r; i++) {
				fill.put("R" + i, "R" + (register % Math.max(1, hw.registers())));
				register++;
			}
			for (int i = 1; i <= needs.n; i++) {
				fill.put("N" + i, Integer.toString(1 + rng.nextInt(15)));
			}
			for (int i = 1; i <= needs.l; i++) {
				fill.put("L" + i, "d" + n + "f" + f + "l" + i);
			}
			for (String line : body.split("\n")) {
				String t = line.strip();
				if (t.isEmpty() || t.startsWith(";")) {
					continue;
				}
				text.append(substitute(t, fill)).append('\n');
			}
		}
		text.append("JMP dream\n");
		return Optional.of(new Dream("Dream #" + n, text.toString(), chosen));
	}

	private static List<String> pick(List<String> from, int count, Random rng) {
		List<String> pool = new ArrayList<>(from);
		List<String> out = new ArrayList<>();
		for (int i = 0; i < count && !pool.isEmpty(); i++) {
			out.add(pool.remove(rng.nextInt(pool.size())));
		}
		return out;
	}

	private static String substitute(String line, Map<String, String> fill) {
		Matcher m = PLACEHOLDER.matcher(line);
		StringBuilder out = new StringBuilder();
		while (m.find()) {
			String key = m.group(1) + m.group(2);
			m.appendReplacement(out, Matcher.quoteReplacement(fill.getOrDefault(key, m.group())));
		}
		m.appendTail(out);
		return out.toString();
	}

	private record Needs(int a, int s, int r, int n, int l, boolean alu) {
	}

	private static final Pattern ALU_OP = Pattern.compile("(?m)^\\s*(ADD|SUB|AND|OR|NOT)\\b", Pattern.CASE_INSENSITIVE);

	/** The highest index of each placeholder class a fragment uses. */
	private static Needs needsOf(String body) {
		int a = 0, s = 0, r = 0, n = 0, l = 0;
		Matcher m = PLACEHOLDER.matcher(body);
		while (m.find()) {
			int idx = Integer.parseInt(m.group(2));
			switch (m.group(1)) {
				case "A" -> a = Math.max(a, idx);
				case "S" -> s = Math.max(s, idx);
				case "R" -> r = Math.max(r, idx);
				case "N" -> n = Math.max(n, idx);
				default -> l = Math.max(l, idx);
			}
		}
		return new Needs(a, s, r, n, l, ALU_OP.matcher(body).find());
	}
}
