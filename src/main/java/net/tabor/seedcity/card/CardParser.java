package net.tabor.seedcity.card;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Interface: Card, parse half (design doc 19, 22.2, 23). Plain text, one op per line, comments
 * after a semicolon, labels as {@code name:} on their own line. Registers R0-R7, immediates 0-15.
 * Port names are {@code cell.port}, {@code cell.N.port} (the Nth built cell of that type) or
 * {@code cell.*.port} (every one). Errors carry a line number and never escape as anything else.
 */
public final class CardParser {
	private CardParser() {
	}

	public static Program parse(String text) throws CardError {
		List<Instr> code = new ArrayList<>();
		Map<String, Integer> labels = new HashMap<>();
		List<int[]> labelRefs = new ArrayList<>();   // (instr index, line) for validation
		List<String> labelNames = new ArrayList<>();
		String[] lines = text.replace("\r", "").split("\n");
		for (int n = 0; n < lines.length; n++) {
			int line = n + 1;
			String raw = lines[n];
			int semi = raw.indexOf(';');
			if (semi >= 0) {
				raw = raw.substring(0, semi);
			}
			String s = raw.trim();
			if (s.isEmpty()) {
				continue;
			}
			if (s.endsWith(":")) {
				String name = s.substring(0, s.length() - 1).trim().toLowerCase(Locale.ROOT);
				if (name.isEmpty() || !name.matches("[a-z_][a-z0-9_]*")) {
					throw new CardError(line, "bad label '" + s + "'");
				}
				if (labels.containsKey(name)) {
					throw new CardError(line, "duplicate label '" + name + "'");
				}
				labels.put(name, code.size());
				continue;
			}
			String[] words = s.split("[\\s,]+");
			Opcode op = Opcode.parse(words[0]).orElseThrow(() -> new CardError(line, "unknown op '" + words[0] + "'"));
			String[] args = new String[words.length - 1];
			System.arraycopy(words, 1, args, 0, args.length);
			Instr instr = switch (op) {
				case SET, ADD, SUB, AND, OR -> {
					need(line, op, args, 2);
					yield new Instr(op, line, reg(line, args[0]), operand(line, args[1]), null, null, null, 0);
				}
				case NOT -> {
					need(line, op, args, 1);
					yield new Instr(op, line, reg(line, args[0]), null, null, null, null, 0);
				}
				case JMP -> {
					need(line, op, args, 1);
					String label = args[0].toLowerCase(Locale.ROOT);
					labelRefs.add(new int[] {code.size(), line});
					labelNames.add(label);
					yield new Instr(op, line, -1, null, label, null, null, 0);
				}
				case JZ -> {
					need(line, op, args, 2);
					String label = args[1].toLowerCase(Locale.ROOT);
					labelRefs.add(new int[] {code.size(), line});
					labelNames.add(label);
					yield new Instr(op, line, reg(line, args[0]), null, label, null, null, 0);
				}
				case WAIT -> {
					need(line, op, args, 1);
					int count = number(line, args[0], 1, 4095);
					yield new Instr(op, line, -1, null, null, null, null, count);
				}
				case OUT -> {
					need(line, op, args, 2);
					yield new Instr(op, line, -1, operand(line, args[1]), null, port(line, args[0]), null, 0);
				}
				case IN -> {
					need(line, op, args, 2);
					yield new Instr(op, line, reg(line, args[0]), null, null, port(line, args[1]), null, 0);
				}
				case NEED -> {
					need(line, op, args, 1);
					String cell = args[0].toLowerCase(Locale.ROOT);
					if (!cell.matches("[a-z0-9_.:/-]+")) {
						throw new CardError(line, "bad cell name '" + args[0] + "'");
					}
					yield new Instr(op, line, -1, null, null, null, cell, 0);
				}
			};
			code.add(instr);
		}
		for (int i = 0; i < labelRefs.size(); i++) {
			if (!labels.containsKey(labelNames.get(i))) {
				throw new CardError(labelRefs.get(i)[1], "unknown label '" + labelNames.get(i) + "'");
			}
		}
		if (code.isEmpty()) {
			throw new CardError(1, "empty card");
		}
		return new Program(code, labels, text);
	}

	private static void need(int line, Opcode op, String[] args, int count) throws CardError {
		if (args.length != count) {
			throw new CardError(line, op + " takes " + count + " argument" + (count == 1 ? "" : "s") + ", got " + args.length);
		}
	}

	private static int reg(int line, String word) throws CardError {
		String w = word.toUpperCase(Locale.ROOT);
		if (w.length() == 2 && w.charAt(0) == 'R' && Character.isDigit(w.charAt(1))) {
			int n = w.charAt(1) - '0';
			if (n <= 7) {
				return n;
			}
		}
		throw new CardError(line, "expected a register R0-R7, got '" + word + "'");
	}

	private static Operand operand(int line, String word) throws CardError {
		String w = word.toUpperCase(Locale.ROOT);
		if (w.startsWith("R") && w.length() == 2 && Character.isDigit(w.charAt(1))) {
			return Operand.reg(reg(line, word));
		}
		return Operand.imm(number(line, word, 0, 15));
	}

	private static int number(int line, String word, int min, int max) throws CardError {
		try {
			int v = Integer.parseInt(word);
			if (v < min || v > max) {
				throw new CardError(line, "number " + v + " out of range " + min + "-" + max);
			}
			return v;
		} catch (NumberFormatException e) {
			throw new CardError(line, "expected a number, got '" + word + "'");
		}
	}

	private static String port(int line, String word) throws CardError {
		String w = word.toLowerCase(Locale.ROOT);
		if (!w.matches("[a-z0-9_]+\\.(\\*|[0-9]+)?\\.?[a-z0-9_]+") || !w.contains(".")) {
			throw new CardError(line, "expected cell.port, cell.N.port or cell.*.port, got '" + word + "'");
		}
		return w;
	}
}
