package net.tabor.seedcity.core;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.tabor.seedcity.SeedCity;
import net.tabor.seedcity.card.CardError;
import net.tabor.seedcity.card.Instr;
import net.tabor.seedcity.card.Opcode;
import net.tabor.seedcity.card.Operand;
import net.tabor.seedcity.card.PortName;
import net.tabor.seedcity.card.Program;
import net.tabor.seedcity.cell.Cell;
import net.tabor.seedcity.cell.CellLibrary;
import net.tabor.seedcity.cell.Port;
import net.tabor.seedcity.cell.PortDir;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Interface: Card, resolve and run halves (design doc 15.2, 23), built to the honesty rule of
 * docs/city-as-computer.md: every value lives in a register cell and every operation runs through
 * an ALU cell. The Core only sequences.
 *
 * <p>Resolve binds R0..R7 to built register vaults by build ordinal, each ALU op to a built ALU
 * cell, and every port name to a built cell's port. Anything missing becomes a hardware need and
 * the program is a plan until the city has built it.
 *
 * <p>Run: one instruction starts per beat of the Clock Tower's output. An instruction expands to
 * micro-steps that drive terminals at the input ports of registers and ALU cells, wait for the
 * redstone to settle, and read output ports back. Arithmetic is analog and saturating: ADD is
 * NOT, SUB, NOT; AND is SUB, SUB; OR is a max cell.
 */
public final class Executor {
	/** Ticks a comparator chain of one cell needs to settle, generously. */
	private static final int ALU_SETTLE = 24;
	private static final int WRITE_SETTLE = 8;

	public record PortRef(CityState.SlotKey slot, String port) {
	}

	private record Step(int after, Runnable run) {
	}

	private final Program program;
	private final CityState city;
	private final Map<Integer, CityState.SlotKey> regs = new TreeMap<>();
	private CityState.SlotKey aluSub;
	private CityState.SlotKey aluNot;
	private CityState.SlotKey aluOr;
	private final Map<Instr, List<PortRef>> ports = new LinkedHashMap<>();
	private final List<String> needs = new ArrayList<>();
	private final Map<Identifier, Integer> wanted = new LinkedHashMap<>();

	private int pc;
	private int waitBeats;
	private boolean prevClock;
	private long beats;
	private Instr current;
	private final Deque<Step> steps = new ArrayDeque<>();
	private int delay;
	private int tmp;
	private int tmp2;
	/** A Courier is out fetching a value for IN; the program waits for it (or gives up after a while). */
	private long inboundTicket;
	private int inboundWait;
	private java.util.function.IntConsumer onInbound;
	private static final int INBOUND_TIMEOUT = 20 * 60;

	private Executor(Program program, CityState city) {
		this.program = program;
		this.city = city;
	}

	/** Binds a program to a city. Throws for port names that can never resolve (a card error). */
	public static Executor resolve(Program program, CityState city) throws CardError {
		Executor e = new Executor(program, city);
		e.bind();
		return e;
	}

	private void bind() throws CardError {
		Set<Integer> used = program.registersUsed();
		List<CityState.Slot> vaults = city.builtOfType("register_block");
		int i = 0;
		for (int r : used) {
			if (i < vaults.size()) {
				regs.put(r, vaults.get(i).key);
			}
			i++;
		}
		if (used.size() > vaults.size()) {
			need("register_block", used.size() - vaults.size());
		}
		Set<Opcode> ops = program.opsUsed();
		if (ops.contains(Opcode.SUB) || ops.contains(Opcode.ADD) || ops.contains(Opcode.AND)) {
			aluSub = first("alu_sub");
		}
		if (ops.contains(Opcode.NOT) || ops.contains(Opcode.ADD)) {
			aluNot = first("alu_not");
		}
		if (ops.contains(Opcode.OR)) {
			aluOr = first("alu_or");
		}
		for (Instr in : program.code()) {
			if (in.op() == Opcode.OUT || in.op() == Opcode.IN) {
				ports.put(in, bindPorts(in));
			} else if (in.op() == Opcode.NEED) {
				Identifier id = in.cell().contains(":") ? Identifier.parse(in.cell()) : SeedCity.id(in.cell());
				if (city.builtOfType(id.getPath()).isEmpty()) {
					need(id.getPath(), 1);
				}
			}
		}
	}

	private CityState.SlotKey first(String type) {
		List<CityState.Slot> cells = city.builtOfType(type);
		if (cells.isEmpty()) {
			need(type, 1);
			return null;
		}
		return cells.getFirst().key;
	}

	private void need(String type, int count) {
		needs.add(type + (count > 1 ? " x" + count : ""));
		wanted.merge(SeedCity.id(type), count, Integer::sum);
	}

	private List<PortRef> bindPorts(Instr in) throws CardError {
		PortName pn = PortName.parse(in.port());
		Optional<Cell> cell = CellLibrary.get(SeedCity.id(pn.type()));
		if (cell.isEmpty()) {
			throw new CardError(in.line(), "no such cell type '" + pn.type() + "'");
		}
		Port port = cell.get().definition().port(pn.port());
		if (port == null) {
			throw new CardError(in.line(), pn.type() + " has no port '" + pn.port() + "'");
		}
		PortDir wantDir = in.op() == Opcode.OUT ? PortDir.IN : PortDir.OUT;
		if (port.dir() != wantDir) {
			throw new CardError(in.line(), pn.type() + "." + pn.port() + " is an " + port.dir().name().toLowerCase() + " port; " + in.op() + " needs an " + wantDir.name().toLowerCase());
		}
		List<CityState.Slot> built = city.builtOfType(pn.type());
		List<PortRef> refs = new ArrayList<>();
		if (pn.all()) {
			for (CityState.Slot s : built) {
				refs.add(new PortRef(s.key, pn.port()));
			}
			if (refs.isEmpty()) {
				need(pn.type(), 1);
			}
		} else {
			int ord = Math.max(1, pn.ordinal());
			CityState.Slot match = null;
			for (CityState.Slot s : built) {
				if (s.ordinal == ord) {
					match = s;
				}
			}
			if (match == null) {
				need(pn.type(), Math.max(1, ord - built.size()));
			} else {
				refs.add(new PortRef(match.key, pn.port()));
			}
		}
		return refs;
	}

	// ---- state ------------------------------------------------------------------------------

	public boolean live() {
		return needs.isEmpty();
	}

	public List<String> needs() {
		return needs;
	}

	public Map<Identifier, Integer> wanted() {
		return wanted;
	}

	public Program program() {
		return program;
	}

	public int pc() {
		return pc;
	}

	public long beats() {
		return beats;
	}

	public Instr current() {
		return current;
	}

	public Map<Integer, CityState.SlotKey> registers() {
		return regs;
	}

	/** Human-readable state for the Reader wall and commands. */
	public String status() {
		if (!live()) {
			return "plan: needs " + String.join(", ", needs);
		}
		return "live: beat " + beats + (current == null ? "" : ", line " + current.line() + " " + current)
				+ (waitBeats > 0 ? " (waiting " + waitBeats + ")" : "") + (onInbound != null ? " (courier out)" : "");
	}

	// ---- run --------------------------------------------------------------------------------

	/** Called every game tick while the program is live. */
	public void tick(ServerLevel level) {
		boolean clock = city.clockSignal(level) > 0;
		boolean beat = clock && !prevClock;
		prevClock = clock;
		if (onInbound != null) {
			Optional<Integer> v = city.inboundResult(inboundTicket);
			if (v.isPresent() || ++inboundWait > INBOUND_TIMEOUT) {
				java.util.function.IntConsumer k = onInbound;
				onInbound = null;
				k.accept(Math.max(0, v.orElse(0)));
				delay = steps.isEmpty() ? 0 : steps.peek().after();
			}
			return;
		}
		if (!steps.isEmpty()) {
			if (--delay <= 0) {
				Step s = steps.poll();
				try {
					s.run().run();
				} catch (Exception e) {
					SeedCity.LOGGER.error("Program step failed at line {}", current == null ? -1 : current.line(), e);
					steps.clear();
				}
				delay = steps.isEmpty() ? 0 : steps.peek().after();
			}
			return;
		}
		if (!beat) {
			return;
		}
		beats++;
		if (waitBeats > 0) {
			waitBeats--;
			return;
		}
		if (program.code().isEmpty()) {
			return;
		}
		Instr in = program.code().get(pc);
		current = in;
		pc = (pc + 1) % program.code().size();
		expand(level, in);
		delay = steps.isEmpty() ? 0 : steps.peek().after();
	}

	private void schedule(int after, Runnable r) {
		steps.add(new Step(after, r));
	}

	private int read(int reg) {
		CityState.SlotKey k = regs.get(reg);
		return k == null ? 0 : Math.max(0, city.readPort(k, "out"));
	}

	private int value(Operand o) {
		return o.register() ? read(o.value()) : o.value();
	}

	private void drive(ServerLevel level, CityState.SlotKey slot, String port, int v) {
		city.driveTerminal(level, slot, port, v);
	}

	/** Latch v into a register: in first, then a clk pulse; in stays put across the falling edge. */
	private void write(ServerLevel level, int reg, java.util.function.IntSupplier v) {
		CityState.SlotKey k = regs.get(reg);
		if (k == null) {
			return;
		}
		schedule(0, () -> drive(level, k, "in", v.getAsInt()));
		schedule(WRITE_SETTLE, () -> drive(level, k, "clk", 15));
		schedule(WRITE_SETTLE + 4, () -> drive(level, k, "clk", 0));
	}

	private void alu2(ServerLevel level, CityState.SlotKey alu, java.util.function.IntSupplier a, java.util.function.IntSupplier b, java.util.function.IntConsumer then) {
		schedule(0, () -> {
			drive(level, alu, "a", a.getAsInt());
			drive(level, alu, "b", b.getAsInt());
		});
		schedule(ALU_SETTLE, () -> then.accept(Math.max(0, city.readPort(alu, "out"))));
	}

	private void alu1(ServerLevel level, CityState.SlotKey alu, java.util.function.IntSupplier a, java.util.function.IntConsumer then) {
		schedule(0, () -> drive(level, alu, "a", a.getAsInt()));
		schedule(ALU_SETTLE, () -> then.accept(Math.max(0, city.readPort(alu, "out"))));
	}

	private void expand(ServerLevel level, Instr in) {
		switch (in.op()) {
			case SET -> {
				int v = value(in.src());
				write(level, in.rd(), () -> v);
			}
			case SUB -> {
				int a = read(in.rd());
				int b = value(in.src());
				alu2(level, aluSub, () -> a, () -> b, r -> tmp = r);
				write(level, in.rd(), () -> tmp);
			}
			case OR -> {
				int a = read(in.rd());
				int b = value(in.src());
				alu2(level, aluOr, () -> a, () -> b, r -> tmp = r);
				write(level, in.rd(), () -> tmp);
			}
			case AND -> {
				// min(a, b) = a - (a - b)
				int a = read(in.rd());
				int b = value(in.src());
				alu2(level, aluSub, () -> a, () -> b, r -> tmp = r);
				alu2(level, aluSub, () -> a, () -> tmp, r -> tmp2 = r);
				write(level, in.rd(), () -> tmp2);
			}
			case ADD -> {
				// min(a + b, 15) = 15 - ((15 - a) - b)
				int a = read(in.rd());
				int b = value(in.src());
				alu1(level, aluNot, () -> a, r -> tmp = r);
				alu2(level, aluSub, () -> tmp, () -> b, r -> tmp2 = r);
				alu1(level, aluNot, () -> tmp2, r -> tmp = r);
				write(level, in.rd(), () -> tmp);
			}
			case NOT -> {
				int a = read(in.rd());
				alu1(level, aluNot, () -> a, r -> tmp = r);
				write(level, in.rd(), () -> tmp);
			}
			case JMP -> pc = program.labels().get(in.label());
			case JZ -> {
				if (read(in.rd()) == 0) {
					pc = program.labels().get(in.label());
				}
			}
			case WAIT -> waitBeats = in.count();
			case OUT -> {
				int v = value(in.src());
				List<PortRef> refs = ports.getOrDefault(in, List.of());
				schedule(0, () -> {
					for (PortRef ref : refs) {
						if (city.needsCourier(ref.slot())) {
							city.post(level, ref.slot(), ref.port(), v, false);   // a Courier carries it
						} else {
							drive(level, ref.slot(), ref.port(), v);
						}
					}
				});
			}
			case IN -> {
				List<PortRef> refs = ports.getOrDefault(in, List.of());
				if (refs.isEmpty()) {
					write(level, in.rd(), () -> 0);
				} else if (city.needsCourier(refs.getFirst().slot())) {
					// send a Courier to read it; the program waits until the value is back at the Core
					schedule(0, () -> {
						inboundTicket = city.post(level, refs.getFirst().slot(), refs.getFirst().port(), 0, true);
						inboundWait = 0;
						onInbound = v -> write(level, in.rd(), () -> v);
					});
				} else {
					int v = Math.max(0, city.readPort(refs.getFirst().slot(), refs.getFirst().port()));
					write(level, in.rd(), () -> v);
				}
			}
			case NEED -> {
			}
		}
	}
}
