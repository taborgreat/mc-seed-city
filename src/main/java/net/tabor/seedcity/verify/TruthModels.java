package net.tabor.seedcity.verify;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.tabor.seedcity.cell.CellDefinition;
import net.tabor.seedcity.cell.Port;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * The shipped truth models (design doc 22.3): passthrough, not, and, or, register, counter,
 * decoder, actuator, sensor, none, plus clock for free-running oscillators.
 */
public final class TruthModels {
	private static final Map<String, TruthModel> MODELS = new TreeMap<>();

	static {
		register(new Passthrough());
		register(new Not());
		register(new And());
		register(new Or());
		register(new Register());
		register(new Counter());
		register(new Decoder());
		register(new Actuator());
		register(new Sensor());
		register(new Clock());
		register(new None());
		register(new Analog("sub", (a, b) -> Math.max(a - b, 0)));
		register(new Analog("max", Math::max));
		register(new Analog("min", Math::min));
		register(new Complement());
	}

	private TruthModels() {
	}

	private static void register(TruthModel m) {
		MODELS.put(m.name(), m);
	}

	public static Optional<TruthModel> byName(String name) {
		return Optional.ofNullable(MODELS.get(name));
	}

	public static Iterable<String> names() {
		return MODELS.keySet();
	}

	// ---- helpers ----------------------------------------------------------------------------

	/** Every combination of test levels across the given input ports, in a stable order. */
	static List<Map<String, Integer>> combinations(List<Port> inputs) {
		List<Map<String, Integer>> out = new ArrayList<>();
		out.add(new LinkedHashMap<>());
		for (Port p : inputs) {
			List<Map<String, Integer>> next = new ArrayList<>();
			for (Map<String, Integer> partial : out) {
				for (int level : TruthModel.levelsFor(p.bits())) {
					Map<String, Integer> m = new LinkedHashMap<>(partial);
					m.put(p.name(), level);
					next.add(m);
				}
			}
			out = next;
		}
		return out;
	}

	static Map<String, Integer> vec(Object... kv) {
		Map<String, Integer> m = new LinkedHashMap<>();
		for (int i = 0; i < kv.length; i += 2) {
			m.put((String) kv[i], (Integer) kv[i + 1]);
		}
		return m;
	}

	/** Expected reading on an output port for a logical level, honouring the port width. */
	static int expect(Port out, int level) {
		return out.bits() == 4 ? level : TruthModel.bit(TruthModel.on(level));
	}

	static Optional<String> mismatch(Port out, int expected, Sample s, String context) {
		int got = s.out(out.name());
		if (got != expected) {
			return Optional.of("port " + out.name() + ": expected " + expected + " got " + got + " " + context);
		}
		return Optional.empty();
	}

	static Optional<String> requirePorts(CellDefinition def, int inputs, int minOutputs) {
		if (inputs >= 0 && def.inputs().size() != inputs) {
			return Optional.of("model needs " + inputs + " input port(s), cell declares " + def.inputs().size());
		}
		if (def.outputs().size() < minOutputs) {
			return Optional.of("model needs at least " + minOutputs + " output port(s), cell declares " + def.outputs().size());
		}
		return Optional.empty();
	}

	// ---- combinational -----------------------------------------------------------------------

	/** Single input copied to every output. Width-converting: a 1-bit out reads 15/0. */
	static final class Passthrough implements TruthModel {
		public String name() {
			return "passthrough";
		}

		public List<Map<String, Integer>> stimuli(CellDefinition def) {
			return combinations(def.inputs());
		}

		public Optional<String> judge(CellDefinition def, List<Sample> samples) {
			Optional<String> shape = requirePorts(def, 1, 1);
			if (shape.isPresent()) {
				return shape;
			}
			Port in = def.inputs().getFirst();
			for (Sample s : samples) {
				for (Port out : def.outputs()) {
					Optional<String> m = mismatch(out, expect(out, s.in(in.name())), s, "for " + in.name() + "=" + s.in(in.name()));
					if (m.isPresent()) {
						return m;
					}
				}
			}
			return Optional.empty();
		}
	}

	static final class Not implements TruthModel {
		public String name() {
			return "not";
		}

		public List<Map<String, Integer>> stimuli(CellDefinition def) {
			return combinations(def.inputs());
		}

		public Optional<String> judge(CellDefinition def, List<Sample> samples) {
			Optional<String> shape = requirePorts(def, 1, 1);
			if (shape.isPresent()) {
				return shape;
			}
			Port in = def.inputs().getFirst();
			for (Sample s : samples) {
				boolean expected = !TruthModel.on(s.in(in.name()));
				for (Port out : def.outputs()) {
					Optional<String> m = mismatch(out, TruthModel.bit(expected), s, "for " + in.name() + "=" + s.in(in.name()));
					if (m.isPresent()) {
						return m;
					}
				}
			}
			return Optional.empty();
		}
	}

	abstract static class Gate implements TruthModel {
		abstract boolean combine(boolean acc, boolean next);

		abstract boolean identity();

		public List<Map<String, Integer>> stimuli(CellDefinition def) {
			return combinations(def.inputs());
		}

		public Optional<String> judge(CellDefinition def, List<Sample> samples) {
			Optional<String> shape = requirePorts(def, -1, 1);
			if (shape.isPresent()) {
				return shape;
			}
			if (def.inputs().size() < 2) {
				return Optional.of("model needs at least 2 input ports");
			}
			for (Sample s : samples) {
				boolean acc = identity();
				for (Port in : def.inputs()) {
					acc = combine(acc, TruthModel.on(s.in(in.name())));
				}
				for (Port out : def.outputs()) {
					Optional<String> m = mismatch(out, TruthModel.bit(acc), s, "for inputs " + s.inputs());
					if (m.isPresent()) {
						return m;
					}
				}
			}
			return Optional.empty();
		}
	}

	static final class And extends Gate {
		public String name() {
			return "and";
		}

		boolean combine(boolean acc, boolean next) {
			return acc && next;
		}

		boolean identity() {
			return true;
		}
	}

	static final class Or extends Gate {
		public String name() {
			return "or";
		}

		boolean combine(boolean acc, boolean next) {
			return acc || next;
		}

		boolean identity() {
			return false;
		}
	}

	// ---- sequential --------------------------------------------------------------------------

	/**
	 * Gated latch: ports in, clk, out. While clk is high, out follows in; while clk is low, out
	 * holds whatever it last saw. Registers are transparent on clk high by design so that the
	 * Clock Tower can step the program at human speed.
	 *
	 * <p>Contract: in is stable across a falling clk edge (it may change together with a rising
	 * edge, and freely while clk is low or high). Each step therefore changes only one of the two
	 * at a falling edge: write, then drop clk with in unchanged, then change in while holding.
	 */
	static final class Register implements TruthModel {
		public String name() {
			return "register";
		}

		public List<Map<String, Integer>> stimuli(CellDefinition def) {
			Port in = def.port("in");
			int[] v = in != null && in.bits() == 4 ? new int[] {7, 1, 15, 0} : new int[] {15, 0, 15, 0};
			List<Map<String, Integer>> s = new ArrayList<>();
			for (int value : v) {
				int other = value == 0 ? 15 : 0;
				s.add(vec("in", value, "clk", 15));   // write
				s.add(vec("in", value, "clk", 0));    // fall with in stable
				s.add(vec("in", other, "clk", 0));    // in changes while holding: out must not
			}
			return s;
		}

		public Optional<String> judge(CellDefinition def, List<Sample> samples) {
			Port in = def.port("in");
			Port clk = def.port("clk");
			Port out = def.port("out");
			if (in == null || clk == null || out == null) {
				return Optional.of("register model needs ports named in, clk, out");
			}
			int held = -1;
			for (int i = 0; i < samples.size(); i++) {
				Sample s = samples.get(i);
				if (TruthModel.on(s.in("clk"))) {
					held = s.in("in");
				}
				int expected = expect(out, held);
				Optional<String> m = mismatch(out, expected, s, "at step " + i + " (in=" + s.in("in") + ", clk=" + s.in("clk") + ")");
				if (m.isPresent()) {
					return m;
				}
			}
			return Optional.empty();
		}
	}

	/** Ports clk, out. Each rising edge on clk adds one to out, wrapping at 16. */
	static final class Counter implements TruthModel {
		public String name() {
			return "counter";
		}

		public List<Map<String, Integer>> stimuli(CellDefinition def) {
			List<Map<String, Integer>> s = new ArrayList<>();
			s.add(vec("clk", 0));
			for (int i = 0; i < 3; i++) {
				s.add(vec("clk", 15));
				s.add(vec("clk", 0));
			}
			return s;
		}

		public Optional<String> judge(CellDefinition def, List<Sample> samples) {
			Port out = def.port("out");
			if (def.port("clk") == null || out == null) {
				return Optional.of("counter model needs ports named clk, out");
			}
			int base = samples.getFirst().out("out");
			int edges = 0;
			boolean prev = false;
			for (int i = 0; i < samples.size(); i++) {
				Sample s = samples.get(i);
				boolean clk = TruthModel.on(s.in("clk"));
				if (clk && !prev) {
					edges++;
				}
				prev = clk;
				int expected = (base + edges) & 15;
				Optional<String> m = mismatch(out, expected, s, "after " + edges + " rising edge(s)");
				if (m.isPresent()) {
					return m;
				}
			}
			return Optional.empty();
		}
	}

	/** Port in (4-bit); outputs named out0..out15. outK is on exactly when in == K. */
	static final class Decoder implements TruthModel {
		public String name() {
			return "decoder";
		}

		public List<Map<String, Integer>> stimuli(CellDefinition def) {
			int max = 0;
			for (Port p : def.outputs()) {
				Integer k = index(p);
				if (k != null) {
					max = Math.max(max, k);
				}
			}
			List<Map<String, Integer>> s = new ArrayList<>();
			for (int v = 0; v <= max; v++) {
				s.add(vec("in", v));
			}
			return s;
		}

		private static Integer index(Port p) {
			if (!p.name().startsWith("out")) {
				return null;
			}
			try {
				return Integer.parseInt(p.name().substring(3));
			} catch (NumberFormatException e) {
				return null;
			}
		}

		public Optional<String> judge(CellDefinition def, List<Sample> samples) {
			if (def.port("in") == null) {
				return Optional.of("decoder model needs a port named in");
			}
			for (Sample s : samples) {
				for (Port out : def.outputs()) {
					Integer k = index(out);
					if (k == null) {
						return Optional.of("port " + out.name() + ": decoder outputs must be named outN");
					}
					Optional<String> m = mismatch(out, TruthModel.bit(s.in("in") == k), s, "for in=" + s.in("in"));
					if (m.isPresent()) {
						return m;
					}
				}
			}
			return Optional.empty();
		}
	}

	// ---- world-facing ------------------------------------------------------------------------

	/**
	 * Driving any input changes blocks inside the footprint, and releasing it restores them.
	 * Three samples per input: rest, driven, rest.
	 */
	static final class Actuator implements TruthModel {
		public String name() {
			return "actuator";
		}

		public List<Map<String, Integer>> stimuli(CellDefinition def) {
			List<Map<String, Integer>> s = new ArrayList<>();
			for (Port in : def.inputs()) {
				s.add(vec());
				s.add(vec(in.name(), 15));
				s.add(vec());
			}
			return s;
		}

		public Optional<String> judge(CellDefinition def, List<Sample> samples) {
			if (def.inputs().isEmpty()) {
				return Optional.of("actuator model needs at least one input port");
			}
			for (int i = 0; i + 2 < samples.size(); i += 3) {
				Sample rest = samples.get(i);
				Sample driven = samples.get(i + 1);
				Sample back = samples.get(i + 2);
				String port = driven.inputs().keySet().iterator().next();
				if (sameWorld(rest.snapshot(), driven.snapshot())) {
					return Optional.of("port " + port + ": nothing moved when driven (" + describe(rest.snapshot()) + ")");
				}
				if (!sameWorld(rest.snapshot(), back.snapshot())) {
					return Optional.of("port " + port + ": cell did not return to rest after release (" + diff(rest.snapshot(), back.snapshot()) + ")");
				}
			}
			return Optional.empty();
		}

		private static boolean sameWorld(Map<BlockPos, BlockState> a, Map<BlockPos, BlockState> b) {
			return a.equals(b);
		}

		/** The redstone-relevant blocks of a snapshot, for failure messages. */
		private static String describe(Map<BlockPos, BlockState> snap) {
			StringBuilder sb = new StringBuilder();
			snap.entrySet().stream()
					.filter(e -> e.getValue().isSignalSource() || e.getValue().hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.EXTENDED)
							|| e.getValue().hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED))
					.sorted(Map.Entry.comparingByKey())
					.limit(24)
					.forEach(e -> sb.append(e.getKey().toShortString()).append('=').append(e.getValue()).append("; "));
			return sb.toString();
		}

		private static String diff(Map<BlockPos, BlockState> a, Map<BlockPos, BlockState> b) {
			StringBuilder sb = new StringBuilder();
			a.forEach((pos, state) -> {
				BlockState other = b.get(pos);
				if (other != state) {
					sb.append(pos.toShortString()).append(": ").append(state).append(" -> ").append(other).append("; ");
				}
			});
			return sb.toString();
		}
	}

	/** Output is read-only: no inputs, and every output reads a legal level. */
	static final class Sensor implements TruthModel {
		public String name() {
			return "sensor";
		}

		public List<Map<String, Integer>> stimuli(CellDefinition def) {
			return List.of(vec());
		}

		public Optional<String> judge(CellDefinition def, List<Sample> samples) {
			if (!def.inputs().isEmpty()) {
				return Optional.of("sensor cells may not declare input ports");
			}
			for (Port out : def.outputs()) {
				int v = samples.getFirst().out(out.name());
				if (v < 0 || v > 15) {
					return Optional.of("port " + out.name() + ": unreadable");
				}
			}
			return Optional.empty();
		}
	}

	/** Free-running: every output toggles at least twice within the window and visits both states. */
	static final class Clock implements TruthModel {
		public String name() {
			return "clock";
		}

		public List<Map<String, Integer>> stimuli(CellDefinition def) {
			return List.of(vec());
		}

		@Override
		public int settleTicks(CellDefinition def) {
			return Math.max(def.settleTicks(), 200);
		}

		public Optional<String> judge(CellDefinition def, List<Sample> samples) {
			if (def.outputs().isEmpty()) {
				return Optional.of("clock model needs at least one output port");
			}
			Sample s = samples.getFirst();
			for (Port out : def.outputs()) {
				int transitions = 0;
				boolean sawOn = false;
				boolean sawOff = false;
				Integer prev = null;
				for (Map<String, Integer> tick : s.trace()) {
					int v = tick.getOrDefault(out.name(), -1);
					sawOn |= v > 0;
					sawOff |= v == 0;
					if (prev != null && TruthModel.on(prev) != TruthModel.on(v)) {
						transitions++;
					}
					prev = v;
				}
				if (transitions < 2 || !sawOn || !sawOff) {
					return Optional.of("port " + out.name() + ": no oscillation (" + transitions + " transitions in " + s.trace().size() + " ticks)");
				}
			}
			return Optional.empty();
		}
	}

	/**
	 * Two-input analog function on strengths: ports a, b (4-bit in) and out. The analog ALU cells
	 * of docs/city-as-computer.md: sub (saturating), max (OR), min (AND).
	 */
	static final class Analog implements TruthModel {
		private final String name;
		private final java.util.function.IntBinaryOperator f;

		Analog(String name, java.util.function.IntBinaryOperator f) {
			this.name = name;
			this.f = f;
		}

		public String name() {
			return name;
		}

		public List<Map<String, Integer>> stimuli(CellDefinition def) {
			List<Map<String, Integer>> s = new ArrayList<>();
			for (int a : FOUR_BIT_LEVELS) {
				for (int b : FOUR_BIT_LEVELS) {
					s.add(vec("a", a, "b", b));
				}
			}
			return s;
		}

		public Optional<String> judge(CellDefinition def, List<Sample> samples) {
			Port out = def.port("out");
			if (def.port("a") == null || def.port("b") == null || out == null) {
				return Optional.of(name + " model needs ports named a, b, out");
			}
			for (Sample s : samples) {
				int expected = f.applyAsInt(s.in("a"), s.in("b"));
				Optional<String> m = mismatch(out, expected, s, "for a=" + s.in("a") + " b=" + s.in("b"));
				if (m.isPresent()) {
					return m;
				}
			}
			return Optional.empty();
		}
	}

	/** Port a (4-bit in) and out: out = 15 - a. The analog NOT. */
	static final class Complement implements TruthModel {
		public String name() {
			return "complement";
		}

		public List<Map<String, Integer>> stimuli(CellDefinition def) {
			List<Map<String, Integer>> s = new ArrayList<>();
			for (int a : FOUR_BIT_LEVELS) {
				s.add(vec("a", a));
			}
			return s;
		}

		public Optional<String> judge(CellDefinition def, List<Sample> samples) {
			Port out = def.port("out");
			if (def.port("a") == null || out == null) {
				return Optional.of("complement model needs ports named a, out");
			}
			for (Sample s : samples) {
				Optional<String> m = mismatch(out, 15 - s.in("a"), s, "for a=" + s.in("a"));
				if (m.isPresent()) {
					return m;
				}
			}
			return Optional.empty();
		}
	}

	/** Storage and decor: nothing to check beyond loading and placing. */
	static final class None implements TruthModel {
		public String name() {
			return "none";
		}

		public List<Map<String, Integer>> stimuli(CellDefinition def) {
			return List.of();
		}

		public Optional<String> judge(CellDefinition def, List<Sample> samples) {
			return Optional.empty();
		}
	}
}
