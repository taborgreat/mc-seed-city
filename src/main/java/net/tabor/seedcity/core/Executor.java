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
import java.util.function.IntConsumer;

/**
 * Interface: Card, the Resolve and Run halves (design doc 15.2, 17). Binds a parsed program to
 * the city's hardware and steps it one instruction per Clock Tower beat.
 *
 * <p>Honesty rule (docs/city-as-computer.md): every register is a RAM vault on the bus, every
 * arithmetic op runs through an ALU cell in the Forge, every OUT and IN goes to a real port. The
 * Core reaches its vaults over the bus: it puts an address on the select lane and a value on
 * the data lane, and reads answers off the return lane at its own gate. ALU cells and ports
 * outside the core district are still reached by terminals and Couriers. A card whose hardware
 * is missing binds as a plan: {@link #live()} is false and {@link #needs()} lists the cells.
 *
 * <p>Each instruction expands into micro-steps (put the address on the bus, wait for the lanes
 * to settle, read the gate, and so on) that run over the following ticks; a step may add the
 * steps that continue it to the front of the queue, so a value fetched from the bus can decide
 * what happens next.
 */
public final class Executor {
	/** How many vaults a bus can address in this version (ram_vault_1, ram_vault_2). */
	public static final int MAX_REGISTERS = 2;
	/** Ticks a comparator chain of one cell needs to settle, generously. */
	private static final int ALU_SETTLE = 24;
	private static final int WRITE_SETTLE = 8;
	/** Ticks for an address and a value to travel the bus, through the vault, and back. */
	private static final int BUS_SETTLE = 90;

	public record PortRef(CityState.SlotKey slot, String port) {
	}

	/** A vault on the bus and the select values that read and write it. */
	private record Vault(CityState.SlotKey slot, int readAt, int writeAt) {
	}

	private record Step(int after, Runnable run) {
	}

	private final Program program;
	private final CityState city;
	private final Map<Integer, Vault> regs = new TreeMap<>();
	private final Map<Integer, Integer> known = new TreeMap<>();
	private CityState.SlotKey aluSub;
	private CityState.SlotKey aluNot;
	private CityState.SlotKey aluOr;
	private final Map<Instr, List<PortRef>> ports = new LinkedHashMap<>();
	private final List<String> needs = new ArrayList<>();
	private final Map<Identifier, Integer> wanted = new LinkedHashMap<>();
	/** Ports already counted as needs, so two ops on one missing port ask for one cell, not two. */
	private final java.util.Set<String> neededPorts = new java.util.HashSet<>();

	private int pc;
	private int waitBeats;
	private boolean prevClock;
	private long beats;
	private Instr current;
	private final Deque<Step> steps = new ArrayDeque<>();
	private int delay;
	/** A Courier is out fetching a value for IN; the program waits for it (or gives up after a while). */
	private long inboundTicket;
	private int inboundWait;
	private IntConsumer onInbound;
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
		if (used.size() > MAX_REGISTERS) {
			throw new CardError(0, "this city's bus addresses " + MAX_REGISTERS + " vaults: use R0 to R" + (MAX_REGISTERS - 1));
		}
		List<CityState.Slot> vaults = city.busVaults();
		int i = 0;
		for (int r : used) {
			if (i < vaults.size()) {
				CityState.Slot v = vaults.get(i);
				int[] rw = CityState.vaultAddresses(v).orElse(new int[] {0, 0});
				regs.put(r, new Vault(v.key, rw[0], rw[1]));
			}
			i++;
		}
		Set<String> bound = new java.util.HashSet<>();
		for (CityState.Slot v : vaults) {
			bound.add(v.cell.getPath());
		}
		int missing = used.size() - vaults.size();
		for (int k = 1; k <= MAX_REGISTERS && missing > 0; k++) {
			String type = "ram_vault_" + k;
			if (bound.contains(type)) {
				continue;
			}
			missing--;
			if (city.connecting(type)) {
				needs.add(type + " (connecting)");   // it stands or is planned and its bus is still being built or verified
				continue;
			}
			need(type, 1);
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
		// cells already planned or under construction count toward the want, so a city plants one bridge, not a row of them
		int coming = Math.max(0, city.committedOfType(type) - city.builtOfType(type).size());
		if (coming < count) {
			wanted.merge(SeedCity.id(type), count - coming, Integer::sum);
		}
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
			if (refs.isEmpty() && neededPorts.add(pn.type() + ".*")) {
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
				if (neededPorts.add(pn.type() + "." + ord)) {
					need(pn.type(), Math.max(1, ord - built.size()));
				}
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

	/** The vault each register is bound to. */
	public Map<Integer, CityState.SlotKey> registers() {
		Map<Integer, CityState.SlotKey> out = new TreeMap<>();
		regs.forEach((r, v) -> out.put(r, v.slot()));
		return out;
	}

	/** Register values as last read from or written to the bus. */
	public Map<Integer, Integer> knownValues() {
		return known;
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
				IntConsumer k = onInbound;
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

	/** Puts steps at the front of the queue, in order: what continues the step that is running. */
	private void chain(Step... more) {
		for (int i = more.length - 1; i >= 0; i--) {
			steps.addFirst(more[i]);
		}
	}

	private static Step step(int after, Runnable r) {
		return new Step(after, r);
	}

	// ---- the bus -----------------------------------------------------------------------------

	private void lane(ServerLevel level, String lane, int v) {
		city.driveCoreLane(level, lane, v);
	}

	/** Reads a register over the bus: address on select, wait, read the gate, release. */
	private void readReg(ServerLevel level, int reg, IntConsumer then) {
		Vault v = regs.get(reg);
		if (v == null) {
			then.accept(0);
			return;
		}
		chain(step(0, () -> lane(level, "sel", v.readAt())),
				step(BUS_SETTLE, () -> {
					int x = Math.max(0, city.readInput(new CityState.SlotKey(0, 0), "ret_in"));
					known.put(reg, x);
					lane(level, "sel", 0);
					then.accept(x);
				}));
	}

	/** Writes a register over the bus: value on data, address on select, release select, drop data. */
	private void writeReg(ServerLevel level, int reg, int value) {
		Vault v = regs.get(reg);
		if (v == null) {
			return;
		}
		int val = Math.max(0, Math.min(15, value));
		chain(step(0, () -> lane(level, "data", val)),
				step(WRITE_SETTLE, () -> lane(level, "sel", v.writeAt())),
				step(BUS_SETTLE, () -> lane(level, "sel", 0)),
				step(WRITE_SETTLE, () -> {
					lane(level, "data", 0);
					known.put(reg, val);
				}));
	}

	/** An operand: a register fetched over the bus, or a number. */
	private void readVal(ServerLevel level, Operand o, IntConsumer then) {
		if (o.register()) {
			readReg(level, o.value(), then);
		} else {
			then.accept(o.value());
		}
	}

	private void drive(ServerLevel level, CityState.SlotKey slot, String port, int v) {
		city.driveTerminal(level, slot, port, v);
	}

	private void alu2(ServerLevel level, CityState.SlotKey alu, int a, int b, IntConsumer then) {
		chain(step(0, () -> {
					drive(level, alu, "a", a);
					drive(level, alu, "b", b);
				}),
				step(ALU_SETTLE, () -> then.accept(Math.max(0, city.readPort(alu, "out")))));
	}

	private void alu1(ServerLevel level, CityState.SlotKey alu, int a, IntConsumer then) {
		chain(step(0, () -> drive(level, alu, "a", a)),
				step(ALU_SETTLE, () -> then.accept(Math.max(0, city.readPort(alu, "out")))));
	}

	private void expand(ServerLevel level, Instr in) {
		switch (in.op()) {
			case SET -> readVal(level, in.src(), v -> writeReg(level, in.rd(), v));
			case SUB -> readReg(level, in.rd(), a -> readVal(level, in.src(), b ->
					alu2(level, aluSub, a, b, r -> writeReg(level, in.rd(), r))));
			case OR -> readReg(level, in.rd(), a -> readVal(level, in.src(), b ->
					alu2(level, aluOr, a, b, r -> writeReg(level, in.rd(), r))));
			case AND -> readReg(level, in.rd(), a -> readVal(level, in.src(), b ->
					// min(a, b) = a - (a - b)
					alu2(level, aluSub, a, b, d -> alu2(level, aluSub, a, d, r -> writeReg(level, in.rd(), r)))));
			case ADD -> readReg(level, in.rd(), a -> readVal(level, in.src(), b ->
					// min(a + b, 15) = 15 - ((15 - a) - b)
					alu1(level, aluNot, a, na -> alu2(level, aluSub, na, b, d -> alu1(level, aluNot, d, r -> writeReg(level, in.rd(), r))))));
			case NOT -> readReg(level, in.rd(), a -> alu1(level, aluNot, a, r -> writeReg(level, in.rd(), r)));
			case JMP -> pc = program.labels().get(in.label());
			case JZ -> readReg(level, in.rd(), v -> {
				if (v == 0) {
					pc = program.labels().get(in.label());
				}
			});
			case WAIT -> waitBeats = in.count();
			case OUT -> {
				List<PortRef> refs = ports.getOrDefault(in, List.of());
				readVal(level, in.src(), v -> chain(step(0, () -> {
					for (PortRef ref : refs) {
						if (city.needsCourier(ref.slot())) {
							city.post(level, ref.slot(), ref.port(), v, false);   // a Courier carries it
						} else {
							drive(level, ref.slot(), ref.port(), v);
						}
					}
				})));
			}
			case IN -> {
				List<PortRef> refs = ports.getOrDefault(in, List.of());
				if (refs.isEmpty()) {
					writeReg(level, in.rd(), 0);
				} else if (city.needsCourier(refs.getFirst().slot())) {
					// send a Courier to read it; the program waits until the value is back at the Core
					chain(step(0, () -> {
						inboundTicket = city.post(level, refs.getFirst().slot(), refs.getFirst().port(), 0, true);
						inboundWait = 0;
						onInbound = v -> writeReg(level, in.rd(), v);
					}));
				} else {
					int v = Math.max(0, city.readPort(refs.getFirst().slot(), refs.getFirst().port()));
					writeReg(level, in.rd(), v);
				}
			}
			case NEED -> {
			}
		}
	}
}
