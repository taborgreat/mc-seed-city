package net.tabor.seedcity.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.tabor.seedcity.SeedCity;
import net.tabor.seedcity.SeedCityBlocks;
import net.tabor.seedcity.build.BuildTask;
import net.tabor.seedcity.cell.Cell;
import net.tabor.seedcity.cell.CellDefinition;
import net.tabor.seedcity.cell.CellKind;
import net.tabor.seedcity.cell.CellLibrary;
import net.tabor.seedcity.cell.Placement;
import net.tabor.seedcity.cell.Port;
import net.tabor.seedcity.cell.PortDir;
import net.tabor.seedcity.config.SeedCityConfig;
import net.tabor.seedcity.entity.BuilderEntity;
import net.tabor.seedcity.entity.CollectorEntity;
import net.tabor.seedcity.entity.CourierEntity;
import net.tabor.seedcity.entity.SeedCityEntities;
import net.tabor.seedcity.entity.SentinelEntity;
import net.tabor.seedcity.entity.WardenEntity;
import net.tabor.seedcity.extra.RedstoneRats;
import net.tabor.seedcity.grammar.Choice;
import net.tabor.seedcity.grammar.Constraint;
import net.tabor.seedcity.grammar.FrontierSlot;
import net.tabor.seedcity.grammar.Goal;
import net.tabor.seedcity.grammar.Grammar;
import net.tabor.seedcity.verify.Integrity;
import net.tabor.seedcity.verify.Verifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * One city (design doc 13, 20.3, 23): the seed origin, the slot grid and what is planned or built
 * in it, the material stock, the frontier, and the roster of mobs that serve it. Entities only
 * ever consume tasks from here.
 *
 * <p>The layout is decided by a planner that walks the frontier in a fixed order and seeds the
 * grammar per slot from the city seed, so the city that grows depends only on (world seed, seed
 * block position), never on builder timing.
 */
public final class CityState {
	public static final int SLOT = 7;
	public static final Identifier CORE_CELL = SeedCity.id("core");
	public static final Identifier CLOCK_CELL = SeedCity.id("clock_tower");
	public static final Identifier JUNCTION_CELL = SeedCity.id("junction");
	private static final int LOOKAHEAD = 2;
	private static final Direction[] SIDES = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

	public record SlotKey(int x, int z) {
		public SlotKey offset(Direction d) {
			return new SlotKey(x + d.getStepX(), z + d.getStepZ());
		}

		public int chebyshev() {
			return Math.max(Math.abs(x), Math.abs(z));
		}

		@Override
		public String toString() {
			return "(" + x + "," + z + ")";
		}
	}

	public enum SlotStatus {
		/** In the map (keeps its rejection list) but needs a cell chosen. */
		PENDING,
		PLANNED,
		BUILDING,
		/** All blocks placed; waiting for, or undergoing, verification. */
		VERIFY,
		BUILT,
		/** Built deliberately broken (doc 5.1 Fault Cell). Dark until a player fixes it. */
		FAULT,
		BLOCKED;

		static SlotStatus parse(String s) {
			try {
				return valueOf(s.toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException e) {
				return PENDING;
			}
		}
	}

	public static final class Slot {
		public final SlotKey key;
		public SlotStatus status;
		public Identifier cell;
		public Rotation rotation = Rotation.NONE;
		public final Set<String> rejected = new LinkedHashSet<>();
		public long since;
		public String note = "";
		/** Planned as a Fault Cell: built with its fault block left out. */
		public boolean faulted;
		/** A Sentinel has been posted here once; it is not replaced if killed. */
		public boolean guarded;
		/** Builder currently working here. Not persisted; a reload re-queues the slot. */
		public UUID builder;
		/** Verification attempts spent on the current cell. Not persisted. */
		public int retries;
		/** 1-based build order among cells of the same type; how cards name a cell (bridge.2.in). */
		public int ordinal;
		/** Floor level chosen from the terrain (Phase 5); null means the Seed's level. */
		public Integer y;

		Slot(SlotKey key, SlotStatus status) {
			this.key = key;
			this.status = status;
		}

		/** Counts as part of the city for planning purposes (has, or will have, a cell). */
		boolean occupies() {
			return status == SlotStatus.PLANNED || status == SlotStatus.BUILDING || status == SlotStatus.VERIFY
					|| status == SlotStatus.BUILT || status == SlotStatus.FAULT;
		}

		@Override
		public String toString() {
			return key + " " + status + (cell == null ? "" : " " + cell.getPath() + "/" + rotation.getSerializedName())
					+ (faulted ? " [fault]" : "");
		}
	}

	private record SlotData(int x, int z, String status, Optional<Identifier> cell, Rotation rotation, List<String> rejected,
							long since, String note, boolean faulted, boolean guarded, int ordinal, Optional<Integer> y) {
		static final Codec<SlotData> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.fieldOf("x").forGetter(SlotData::x),
				Codec.INT.fieldOf("z").forGetter(SlotData::z),
				Codec.STRING.fieldOf("status").forGetter(SlotData::status),
				Identifier.CODEC.optionalFieldOf("cell").forGetter(SlotData::cell),
				Rotation.CODEC.optionalFieldOf("rotation", Rotation.NONE).forGetter(SlotData::rotation),
				Codec.STRING.listOf().optionalFieldOf("rejected", List.of()).forGetter(SlotData::rejected),
				Codec.LONG.optionalFieldOf("since", 0L).forGetter(SlotData::since),
				Codec.STRING.optionalFieldOf("note", "").forGetter(SlotData::note),
				Codec.BOOL.optionalFieldOf("faulted", false).forGetter(SlotData::faulted),
				Codec.BOOL.optionalFieldOf("guarded", false).forGetter(SlotData::guarded),
				Codec.INT.optionalFieldOf("ordinal", 0).forGetter(SlotData::ordinal),
				Codec.INT.optionalFieldOf("y").forGetter(SlotData::y)
		).apply(i, SlotData::new));

		static SlotData of(Slot s) {
			return new SlotData(s.key.x(), s.key.z(), s.status.name(), Optional.ofNullable(s.cell), s.rotation,
					List.copyOf(s.rejected), s.since, s.note, s.faulted, s.guarded, s.ordinal, Optional.ofNullable(s.y));
		}

		Slot toSlot() {
			Slot s = new Slot(new SlotKey(x, z), SlotStatus.parse(status));
			s.cell = cell.orElse(null);
			s.rotation = rotation;
			s.rejected.addAll(rejected);
			s.since = since;
			s.note = note;
			s.faulted = faulted;
			s.guarded = guarded;
			s.ordinal = ordinal;
			s.y = y.orElse(null);
			if (s.status == SlotStatus.BUILDING || s.status == SlotStatus.VERIFY) {
				s.status = SlotStatus.PLANNED;   // re-run the task after a reload; correct blocks are skipped
			}
			return s;
		}
	}

	/** A Core terminal and the block it replaced, so ejecting the card restores the street. */
	private record TerminalData(BlockPos pos, BlockState saved) {
		static final Codec<TerminalData> CODEC = RecordCodecBuilder.create(i -> i.group(
				BlockPos.CODEC.fieldOf("pos").forGetter(TerminalData::pos),
				BlockState.CODEC.fieldOf("saved").forGetter(TerminalData::saved)
		).apply(i, TerminalData::new));
	}

	public static final Codec<CityState> CODEC = RecordCodecBuilder.create(i -> i.group(
			BlockPos.CODEC.fieldOf("seed").forGetter(c -> c.seedPos),
			Codec.LONG.fieldOf("city_seed").forGetter(c -> c.citySeed),
			Codec.BOOL.fieldOf("frozen").forGetter(c -> c.frozen),
			Codec.INT.fieldOf("built").forGetter(c -> c.builtCount),
			Codec.INT.fieldOf("redstone").forGetter(c -> c.redstone),
			Codec.INT.fieldOf("stone").forGetter(c -> c.stone),
			Codec.INT.fieldOf("wood").forGetter(c -> c.wood),
			SlotData.CODEC.listOf().fieldOf("slots").forGetter(c -> c.slots.values().stream().map(SlotData::of).toList()),
			Codec.STRING.optionalFieldOf("card", "").forGetter(c -> c.cardText == null ? "" : c.cardText),
			ItemStack.OPTIONAL_CODEC.optionalFieldOf("card_item", ItemStack.EMPTY).forGetter(c -> c.cardItem),
			TerminalData.CODEC.listOf().optionalFieldOf("terminals", List.of()).forGetter(c -> c.terminals.entrySet().stream().map(e -> new TerminalData(e.getKey(), e.getValue())).toList()),
			Codec.INT.optionalFieldOf("dreams", 0).forGetter(c -> c.dreamCount),
			Codec.BOOL.optionalFieldOf("dreamed", false).forGetter(c -> c.dreamed),
			Codec.STRING.optionalFieldOf("running", "").forGetter(c -> c.runningText == null ? "" : c.runningText)
	).apply(i, CityState::new));

	private final BlockPos seedPos;
	private long nextRatSpawnAttempt;   // not persisted; a fresh load just tries again
	private final long citySeed;
	private boolean frozen;
	private int builtCount;
	private int redstone;
	private int stone;
	private int wood;
	private final Map<SlotKey, Slot> slots = new LinkedHashMap<>();
	/** The inserted card's text and item; empty when the city runs its hardware default. */
	private String cardText;
	private ItemStack cardItem = ItemStack.EMPTY;
	/** Terminal positions and what stood there before. Persisted. */
	private final Map<BlockPos, BlockState> terminals = new LinkedHashMap<>();
	/** The running program. Rebuilt after a reload from runningText, else from cardText. Not persisted. */
	private Executor executor;
	/** A card accepted as a plan, waiting for its hardware; the running program keeps going meanwhile. */
	private Executor plan;
	/** Text of the running program when the card in the reader is a plan that superseded it. Persisted. */
	private String runningText;
	private String cardError;
	private boolean programRestorePending;
	/** Finished cells waiting for their turn under the verifier. Not persisted. */
	private final List<BuildTask> pendingVerify = new ArrayList<>();
	private SlotKey verifyingSlot;
	/** District name to game time before which the Core will not send a replacement Warden. */
	private final Map<String, Long> wardenCooldown = new HashMap<>();
	/** Per-city config, set by tests or tools; null means the global config. Not persisted. */
	private SeedCityConfig configOverride;
	/** Debug labels over every built cell (/seedcity labels). Not persisted. */
	private boolean labels;
	/** Slots whose ground could not be read this round (chunk not loaded). Not persisted. */
	private final Set<SlotKey> deferred = new HashSet<>();
	/** L3: how many cards the city has dreamed, and whether the current card is one of them. */
	private int dreamCount;
	private boolean dreamed;
	/** Game time of the last construction activity and of the last dream. Not persisted. */
	private long lastActivity;
	private long lastDream;

	private CityState(BlockPos seedPos, long citySeed, boolean frozen, int builtCount, int redstone, int stone, int wood, List<SlotData> slotData,
					  String card, ItemStack cardItem, List<TerminalData> terminalData, int dreamCount, boolean dreamed, String running) {
		this.seedPos = seedPos;
		this.citySeed = citySeed;
		this.frozen = frozen;
		this.builtCount = builtCount;
		this.redstone = redstone;
		this.stone = stone;
		this.wood = wood;
		for (SlotData d : slotData) {
			Slot s = d.toSlot();
			slots.put(s.key, s);
		}
		this.cardText = card.isEmpty() ? null : card;
		this.cardItem = cardItem;
		for (TerminalData t : terminalData) {
			terminals.put(t.pos(), t.saved());
		}
		this.runningText = running.isEmpty() ? null : running;
		this.programRestorePending = this.cardText != null || this.runningText != null;
		this.dreamCount = dreamCount;
		this.dreamed = dreamed;
	}

	private CityState(BlockPos seedPos, long citySeed, int redstone, int stone, int wood) {
		this(seedPos, citySeed, false, 0, redstone, stone, wood, List.of(), "", ItemStack.EMPTY, List.of(), 0, false, "");
	}

	/**
	 * A fresh city rooted at a Seed. The core, the clock tower south of it, and a junction on the
	 * clock's output are planned by force: the clock has one output, and a dead end there would
	 * cap the whole city's signal at one cell.
	 */
	public static CityState create(long worldSeed, BlockPos seedPos, SeedCityConfig cfg) {
		long citySeed = cfg.citySeedOverride != 0 ? cfg.citySeedOverride : mix(worldSeed ^ seedPos.asLong());
		CityState c = new CityState(seedPos, citySeed, cfg.initialRedstone, cfg.initialStone, cfg.initialWood);
		c.forceRoot();
		return c;
	}

	private void forceRoot() {
		force(new SlotKey(0, 0), CORE_CELL, Rotation.NONE);
		force(new SlotKey(0, 1), CLOCK_CELL, Rotation.NONE);
		force(new SlotKey(0, 2), JUNCTION_CELL, Rotation.NONE);
	}

	/** A throwaway city used to prove the planner is deterministic and plants faults. */
	public static List<String> previewPlan(long citySeed, int count, SeedCityConfig cfg) {
		CityState c = new CityState(BlockPos.ZERO, citySeed, cfg.initialRedstone, cfg.initialStone, cfg.initialWood);
		c.overrideConfig(cfg);
		c.forceRoot();
		c.planAhead(k -> new Fit(c.coreOrigin().getY(), null, false), count, cfg);
		List<String> out = new ArrayList<>();
		for (Slot s : c.slots.values()) {
			out.add(s.toString());
		}
		return out;
	}

	private static long mix(long z) {
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	private void force(SlotKey key, Identifier cell, Rotation rotation) {
		Slot s = new Slot(key, SlotStatus.PLANNED);
		s.cell = cell;
		s.rotation = rotation;
		slots.put(key, s);
	}

	// ---- geometry ---------------------------------------------------------------------------

	public SeedCityConfig cfg() {
		return configOverride != null ? configOverride : SeedCityConfig.get();
	}

	public void overrideConfig(SeedCityConfig cfg) {
		this.configOverride = cfg;
	}

	public BlockPos seedPos() {
		return seedPos;
	}

	public long citySeed() {
		return citySeed;
	}

	public boolean frozen() {
		return frozen;
	}

	public void freeze() {
		frozen = true;
	}

	public int builtCount() {
		return builtCount;
	}

	public int stock(Terrain.Kind kind) {
		return switch (kind) {
			case REDSTONE -> redstone;
			case STONE -> stone;
			case WOOD -> wood;
		};
	}

	public BlockPos coreOrigin() {
		return seedPos.offset(-3, -1, -3);
	}

	public BlockPos slotOrigin(SlotKey k) {
		BlockPos flat = coreOrigin();
		return new BlockPos(flat.getX() + k.x() * SLOT, slotY(k), flat.getZ() + k.z() * SLOT);
	}

	/** A slot's floor level: chosen from the terrain when it was planned, else the Seed's. */
	public int slotY(SlotKey k) {
		Slot s = slots.get(k);
		return s != null && s.y != null ? s.y : coreOrigin().getY();
	}

	/** Two slots can only mate ports when their floors are level. */
	private boolean level(SlotKey a, SlotKey b) {
		return slotY(a) == slotY(b);
	}

	/** True when a column lies inside any slot the city has or will have, at any height. */
	public boolean inCity(BlockPos p) {
		BlockPos flat = coreOrigin();
		int kx = Math.floorDiv(p.getX() - flat.getX(), SLOT);
		int kz = Math.floorDiv(p.getZ() - flat.getZ(), SLOT);
		Slot s = slots.get(new SlotKey(kx, kz));
		return s != null && s.occupies();
	}

	public Optional<Slot> slot(SlotKey k) {
		return Optional.ofNullable(slots.get(k));
	}

	public Iterable<Slot> slots() {
		return slots.values();
	}

	public Optional<Placement> placement(Slot s) {
		if (s.cell == null) {
			return Optional.empty();
		}
		return CellLibrary.get(s.cell).map(cell -> {
			BlockPos origin = slotOrigin(s.key).offset(Cell.rotationShift(s.rotation, cell.size()));
			return new Placement(cell, origin, s.rotation);
		});
	}

	public Optional<Placement> placement(SlotKey k) {
		Slot s = slots.get(k);
		return s == null ? Optional.empty() : placement(s);
	}

	private String[] sectors;
	private double sectorOffset;

	/**
	 * Functional zoning (docs/city-as-computer.md, decision 3): the core is the ring around the
	 * Seed; beyond it the city is cut into angular sectors, each a district (Forge, RAM, Storage,
	 * residential, plaza), shuffled and rotated by the city seed. Random per city, readable from
	 * the air through what grows there.
	 */
	public String district(SlotKey k) {
		if (k.chebyshev() <= 1) {
			return "core";
		}
		if (sectors == null) {
			List<String> names = new ArrayList<>(List.of("forge", "ram", "storage", "residential", "plaza"));
			Random rng = new Random(mix(citySeed ^ 0x5EC70125L));
			java.util.Collections.shuffle(names, rng);
			int n = Math.max(1, Math.min(names.size(), cfg().sectors));
			sectors = names.subList(0, n).toArray(new String[0]);
			sectorOffset = rng.nextDouble() * Math.PI * 2;
		}
		double angle = Math.atan2(k.z(), k.x()) + sectorOffset;
		double turn = ((angle % (Math.PI * 2)) + Math.PI * 2) % (Math.PI * 2) / (Math.PI * 2);
		int idx = Math.min(sectors.length - 1, (int) Math.floor(turn * sectors.length));
		return sectors[idx];
	}

	/** Built slots of a district, nearest the core first. */
	public List<Slot> districtSlots(String district) {
		List<Slot> out = new ArrayList<>();
		for (Slot s : slots.values()) {
			if (s.status == SlotStatus.BUILT && district.equals(district(s.key))) {
				out.add(s);
			}
		}
		out.sort(Comparator.comparingInt((Slot s) -> s.key.chebyshev()).thenComparingInt(s -> s.key.x()).thenComparingInt(s -> s.key.z()));
		return out;
	}

	public Set<String> districts() {
		Set<String> out = new TreeSet<>();
		for (Slot s : slots.values()) {
			if (s.status == SlotStatus.BUILT || s.status == SlotStatus.FAULT) {
				out.add(district(s.key));
			}
		}
		return out;
	}

	/** A district with an open Fault Cell has no Warden: that is what makes it dark (doc 7). */
	public boolean districtHasFault(String district) {
		for (Slot s : slots.values()) {
			if (s.status == SlotStatus.FAULT && district.equals(district(s.key))) {
				return true;
			}
		}
		return false;
	}

	/** Built slots of one cell type (by id path), in build order. */
	public List<Slot> builtOfType(String type) {
		List<Slot> out = new ArrayList<>();
		for (Slot s : slots.values()) {
			if (s.status == SlotStatus.BUILT && s.cell != null && s.cell.getPath().equals(type)) {
				out.add(s);
			}
		}
		out.sort(Comparator.comparingInt(s -> s.ordinal));
		return out;
	}

	private void assignOrdinal(Slot s) {
		if (s.ordinal > 0 || s.cell == null) {
			return;
		}
		int max = 0;
		for (Slot o : slots.values()) {
			if (o != s && s.cell.equals(o.cell)) {
				max = Math.max(max, o.ordinal);
			}
		}
		s.ordinal = max + 1;
	}

	// ---- ports, terminals, the clock ----------------------------------------------------------

	public Optional<Placement.WorldPort> worldPort(SlotKey k, String port) {
		Optional<Placement> p = placement(k);
		if (p.isEmpty()) {
			return Optional.empty();
		}
		for (Placement.WorldPort wp : p.get().ports()) {
			if (wp.port().name().equals(port)) {
				return Optional.of(wp);
			}
		}
		return Optional.empty();
	}

	/** Reads a port of a built cell: the strength its port block emits outward. -1 when unavailable. */
	public int readPort(SlotKey k, String port) {
		if (level == null) {
			return -1;
		}
		Optional<Placement.WorldPort> wp = worldPort(k, port);
		if (wp.isEmpty()) {
			return -1;
		}
		return level.getSignal(wp.get().pos(), wp.get().face().getOpposite());
	}

	/** The Clock Tower's output right now; 0 when there is no built clock. The program's heartbeat. */
	public int clockSignal(ServerLevel level) {
		this.level = level;
		Slot clock = slots.get(new SlotKey(0, 1));
		if (clock == null || clock.status != SlotStatus.BUILT) {
			return 0;
		}
		return Math.max(0, readPort(clock.key, "out"));
	}

	/**
	 * Drives an input port with a Core terminal: a directional signal block just outside the port,
	 * replacing whatever was there (a neighbour's output, a wall, air), which is saved and comes
	 * back when the card is ejected. Until buses exist this is how the Core reaches the city.
	 */
	public void driveTerminal(ServerLevel level, SlotKey k, String port, int power) {
		Optional<Placement.WorldPort> wp = worldPort(k, port);
		if (wp.isEmpty()) {
			return;
		}
		BlockPos outside = wp.get().outside();
		if (!terminals.containsKey(outside)) {
			terminals.put(outside, level.getBlockState(outside));
		}
		BlockState state = SeedCityBlocks.TERMINAL.defaultBlockState()
				.setValue(net.tabor.seedcity.verify.ProbeBlock.POWER, Math.max(0, Math.min(15, power)))
				.setValue(net.tabor.seedcity.verify.ProbeBlock.FACING, wp.get().face().getOpposite());
		if (!level.getBlockState(outside).equals(state)) {
			level.setBlock(outside, state, net.minecraft.world.level.block.Block.UPDATE_ALL);
		}
	}

	private void clearTerminals(ServerLevel level) {
		for (Map.Entry<BlockPos, BlockState> e : terminals.entrySet()) {
			if (level.getBlockState(e.getKey()).is(SeedCityBlocks.TERMINAL)) {
				level.setBlock(e.getKey(), e.getValue(), net.minecraft.world.level.block.Block.UPDATE_ALL);
			}
		}
		terminals.clear();
		outbox.clear();
		mail.clear();
	}

	public boolean isTerminal(BlockPos pos) {
		return terminals.containsKey(pos);
	}

	// ---- couriers: OUT and IN across districts ------------------------------------------------

	/** A letter: drive {@code slot.port} with {@code value}, or read it back under {@code ticket}. */
	public record Mail(long ticket, SlotKey slot, String port, int value, boolean read) {
	}

	private final java.util.ArrayDeque<Mail> mail = new java.util.ArrayDeque<>();
	private final Map<Long, Integer> inbound = new HashMap<>();
	private long nextTicket = 1;

	/** Whether the Core reaches a slot by Courier (another district) or by a terminal next door (its own). */
	public boolean needsCourier(SlotKey k) {
		return !"core".equals(district(k));
	}

	/** Last value sent to each port by Courier, so a loop that repeats an OUT does not flood the streets. */
	private final Map<String, Integer> outbox = new HashMap<>();

	/**
	 * Posts a letter and makes sure a Courier is around to carry it. Returns the ticket, or -1 when
	 * the port already holds that value and nothing needs to travel.
	 */
	public long post(ServerLevel level, SlotKey slot, String port, int value, boolean read) {
		if (!read) {
			String key = slot + "." + port;
			Integer last = outbox.get(key);
			if (last != null && last == value) {
				return -1;
			}
			outbox.put(key, value);
		}
		long ticket = nextTicket++;
		mail.add(new Mail(ticket, slot, port, value, read));
		List<CourierEntity> couriers = couriers(level);
		boolean idle = couriers.stream().anyMatch(CourierEntity::idle);
		if (!idle && couriers.size() < cfg().maxCouriers) {
			CourierEntity c = SeedCityEntities.COURIER.spawn(level, seedPos.above(5), EntitySpawnReason.MOB_SUMMONED);
			if (c != null) {
				c.setCity(seedPos);
			}
		}
		return ticket;
	}

	public Optional<Mail> takeMail() {
		return Optional.ofNullable(mail.poll());
	}

	public int pendingMail() {
		return mail.size();
	}

	public void deliverInbound(long ticket, int value) {
		inbound.put(ticket, value);
	}

	/** The value a read letter came back with, once; empty while the Courier is still out. */
	public Optional<Integer> inboundResult(long ticket) {
		Integer v = inbound.remove(ticket);
		return Optional.ofNullable(v);
	}

	public List<CourierEntity> couriers(ServerLevel level) {
		return level.getEntities(SeedCityEntities.COURIER, bounds(), c -> seedPos.equals(c.cityPos()));
	}

	// ---- player blueprints -------------------------------------------------------------------

	private String lastBlueprintResult = "";

	/** Any footprint: floor solid, everything above clear. Used for player blueprints. */
	public boolean buildableAt(ServerLevel level, Placement p) {
		var box = p.footprint();
		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				BlockPos floor = new BlockPos(x, box.minY(), z);
				BlockState f = level.getBlockState(floor);
				if (f.isAir() || !f.isCollisionShapeFullBlock(level, floor)) {
					return false;
				}
				for (int y = box.minY() + 1; y <= box.maxY(); y++) {
					BlockState st = level.getBlockState(new BlockPos(x, y, z));
					if (!(st.isAir() || st.canBeReplaced())) {
						return false;
					}
				}
			}
		}
		return true;
	}

	public void noteBlueprintResult(String s) {
		lastBlueprintResult = s;
	}

	public String lastBlueprintResult() {
		return lastBlueprintResult;
	}

	// ---- the program -------------------------------------------------------------------------

	/** Outcome of inserting a card. On rejection the previous card stays in the reader. */
	public record CardResult(boolean accepted, String message, ItemStack ejected) {
	}

	/**
	 * Parses and resolves a card. A parse or binding error is reported with its line and leaves
	 * the previous program running. A card whose hardware is missing is accepted as a plan: its
	 * needs become goals for the builders, the previous program (or the hardware default) keeps
	 * running meanwhile, and the plan goes live by itself when the hardware is built (doc 15.2).
	 */
	public CardResult insertCard(ServerLevel level, String text, ItemStack item) {
		this.level = level;
		net.tabor.seedcity.card.Program program;
		Executor next;
		try {
			program = net.tabor.seedcity.card.CardParser.parse(text);
			next = Executor.resolve(program, this);
		} catch (net.tabor.seedcity.card.CardError e) {
			cardError = "CARD ERROR " + e;
			SeedCity.LOGGER.info("City {}: card rejected: {}", seedPos.toShortString(), e);
			return new CardResult(false, "Card rejected, " + e + ". " + (executor == null ? "The city runs its default." : "The previous card keeps running."), ItemStack.EMPTY);
		}
		ItemStack previous = cardItem;
		String previousText = cardText;
		cardError = null;
		cardText = text;
		cardItem = item == null ? ItemStack.EMPTY : item;
		programRestorePending = false;
		dreamed = false;
		lastActivity = level.getGameTime();
		String msg;
		if (next.live()) {
			if (executor != null) {
				clearTerminals(level);
			}
			executor = next;
			plan = null;
			runningText = null;
			msg = "Card accepted and running (" + program.code().size() + " ops).";
		} else {
			if (executor != null && runningText == null) {
				runningText = previousText;
			}
			plan = next;
			msg = "Card accepted as a plan; the city needs " + String.join(", ", next.needs()) + " before it can run"
					+ (executor != null ? "; the previous card keeps running meanwhile." : ".");
		}
		SeedCity.LOGGER.info("City {}: {}", seedPos.toShortString(), msg);
		return new CardResult(true, msg, previous);
	}

	/** Removes the card: terminals come out, the streets go back to what they were, L0 runs. */
	public ItemStack ejectCard(ServerLevel level) {
		this.level = level;
		if (cardText == null) {
			return ItemStack.EMPTY;
		}
		clearTerminals(level);
		ItemStack out = cardItem;
		cardText = null;
		cardItem = ItemStack.EMPTY;
		executor = null;
		plan = null;
		runningText = null;
		cardError = null;
		programRestorePending = false;
		dreamed = false;
		lastActivity = level.getGameTime();
		SeedCity.LOGGER.info("City {}: card ejected", seedPos.toShortString());
		return out;
	}

	/** The running program. */
	public Optional<Executor> executor() {
		return Optional.ofNullable(executor);
	}

	/** The plan waiting for hardware, if the card in the reader is one. */
	public Optional<Executor> plan() {
		return Optional.ofNullable(plan);
	}

	public boolean programLive() {
		return executor != null && executor.live();
	}

	/** Cells the current plan still needs, for the grammar. */
	public Map<Identifier, Integer> wantedCells() {
		return plan == null ? Map.of() : plan.wanted();
	}

	/** After a reload: the running program comes back from its text, the card in the reader from its. */
	private void restoreProgram(ServerLevel level) {
		if (runningText != null) {
			try {
				Executor running = Executor.resolve(net.tabor.seedcity.card.CardParser.parse(runningText), this);
				if (running.live()) {
					executor = running;
				} else {
					runningText = null;
				}
			} catch (net.tabor.seedcity.card.CardError e) {
				runningText = null;
			}
		}
		if (cardText != null) {
			try {
				Executor card = Executor.resolve(net.tabor.seedcity.card.CardParser.parse(cardText), this);
				if (card.live()) {
					if (executor != null) {
						clearTerminals(level);
					}
					executor = card;
					plan = null;
					runningText = null;
				} else {
					plan = card;
				}
			} catch (net.tabor.seedcity.card.CardError e) {
				cardError = "CARD ERROR " + e;
			}
		}
	}

	/** Called every game tick by the manager while the seed chunk ticks. */
	public void tickProgram(ServerLevel level) {
		this.level = level;
		if (frozen) {
			return;
		}
		if (programRestorePending) {
			programRestorePending = false;
			restoreProgram(level);
		}
		if (plan != null && level.getGameTime() % 40 == 0) {
			// hardware may have arrived since the card was inserted: try to bind again
			try {
				Executor again = Executor.resolve(plan.program(), this);
				if (again.live()) {
					if (executor != null) {
						clearTerminals(level);
					}
					executor = again;
					plan = null;
					runningText = null;
					SeedCity.LOGGER.info("City {}: plan became live", seedPos.toShortString());
				}
			} catch (net.tabor.seedcity.card.CardError e) {
				cardError = "CARD ERROR " + e;
			}
		}
		if (executor != null && executor.live()) {
			executor.tick(level);
		}
	}

	public String programSummary() {
		StringBuilder sb = new StringBuilder();
		if (executor != null) {
			sb.append(executor.status());
		} else {
			sb.append(cardError != null ? cardError + "; running the hardware default" : "L0: hardware default");
		}
		if (plan != null) {
			sb.append(" | ").append(plan.status());
		}
		return sb.toString();
	}

	/** What the Reader wall shows. */
	public List<String> readerLines() {
		List<String> lines = new ArrayList<>();
		lines.add("SEED CITY CORE");
		if (cardError != null) {
			lines.add(cardError);
		}
		if (executor == null && plan == null) {
			lines.add("no card: hardware default");
		} else {
			if (dreamed && plan == null) {
				lines.add("DREAM #" + dreamCount + " (the city wrote this card)");
			}
			if (executor != null) {
				lines.add(executor.status());
				StringBuilder regs = new StringBuilder();
				for (Map.Entry<Integer, SlotKey> e : executor.registers().entrySet()) {
					regs.append("R").append(e.getKey()).append('=').append(Math.max(0, readPort(e.getValue(), "out"))).append("  ");
				}
				if (!regs.isEmpty()) {
					lines.add(regs.toString().trim());
				}
			}
			if (plan != null) {
				lines.add((dreamed ? "DREAM #" + dreamCount + " " : "") + plan.status() + (executor != null ? " (previous card runs meanwhile)" : ""));
			}
		}
		lines.add("clock " + (clockSignal(level) > 0 ? "HIGH" : "low") + "  built " + builtCount);
		return lines;
	}

	private ServerLevel level;

	// ---- planning ---------------------------------------------------------------------------

	private Port portFacing(Slot n, Direction sideFromUs) {
		Optional<Cell> cell = CellLibrary.get(n.cell);
		if (cell.isEmpty()) {
			return null;
		}
		return Grammar.portOn(cell.get().ports(n.rotation), sideFromUs.getOpposite());
	}

	/** What the neighbours of a slot present on each shared face. */
	public List<Constraint> constraints(SlotKey k) {
		return constraints(k, liveSlots(false, false));
	}

	/**
	 * Blocked neighbours and the world beyond the radius count as walls, so a rotation that
	 * points an output into the void is not mistaken for one with room to grow.
	 */
	private List<Constraint> constraints(SlotKey k, Set<SlotKey> live) {
		int maxRadius = cfg().maxRadiusSlots;
		List<Constraint> out = new ArrayList<>();
		for (Direction side : SIDES) {
			SlotKey nk = k.offset(side);
			Slot n = slots.get(nk);
			if (nk.chebyshev() > maxRadius || (n != null && n.status == SlotStatus.BLOCKED)) {
				out.add(Constraint.wall(side));
				continue;
			}
			if (n == null || !n.occupies() || n.cell == null) {
				continue;
			}
			if (!level(k, nk)) {
				out.add(Constraint.wall(side));   // a step in the ground: the street ends here
				continue;
			}
			Port facingUs = portFacing(n, side);
			if (facingUs == null) {
				out.add(Constraint.wall(side));
			} else {
				boolean isLive = facingUs.dir() == PortDir.OUT && live.contains(n.key);
				out.add(new Constraint(side, facingUs, isLive));
			}
		}
		return out;
	}

	/** Slots whose signal traces back to the clock tower. */
	public Set<SlotKey> clockedSlots(boolean builtOnly) {
		return liveSlots(builtOnly, true);
	}

	/**
	 * Slots that carry a signal: sources (the clock; sensors too unless {@code clockOnly}) and any
	 * cell with an input mated to the output of a live cell. A fixpoint over the planned city.
	 * Fault Cells conduct for planning (the dark district is wired, just broken) but never count
	 * when {@code builtOnly}.
	 */
	public Set<SlotKey> liveSlots(boolean builtOnly, boolean clockOnly) {
		Set<SlotKey> live = new HashSet<>();
		List<Slot> eligible = new ArrayList<>();
		for (Slot s : slots.values()) {
			boolean ok = builtOnly ? s.status == SlotStatus.BUILT : s.occupies();
			if (ok && s.cell != null && CellLibrary.get(s.cell).isPresent()) {
				eligible.add(s);
				CellDefinition def = CellLibrary.get(s.cell).get().definition();
				if ("clock".equals(def.truth()) || (!clockOnly && def.kind() == CellKind.SENSOR)) {
					live.add(s.key);
				}
			}
		}
		boolean changed = true;
		while (changed) {
			changed = false;
			for (Slot s : eligible) {
				if (live.contains(s.key)) {
					continue;
				}
				Cell cell = CellLibrary.get(s.cell).get();
				for (Direction side : SIDES) {
					Port ours = Grammar.portOn(cell.ports(s.rotation), side);
					if (ours == null || ours.dir() != PortDir.IN) {
						continue;
					}
					Slot n = slots.get(s.key.offset(side));
					if (n == null || !live.contains(n.key) || !level(s.key, n.key)) {
						continue;
					}
					Port theirs = portFacing(n, side);
					if (theirs != null && theirs.dir() == PortDir.OUT && ours.compatibleWith(theirs)) {
						live.add(s.key);
						changed = true;
						break;
					}
				}
			}
		}
		return live;
	}

	private boolean hasClockedActuator(boolean builtOnly) {
		Set<SlotKey> clocked = clockedSlots(builtOnly);
		for (Slot s : slots.values()) {
			if (s.cell == null || !clocked.contains(s.key)) {
				continue;
			}
			Optional<Cell> cell = CellLibrary.get(s.cell);
			if (cell.isPresent() && cell.get().definition().kind() == CellKind.ACTUATOR) {
				return true;
			}
		}
		return false;
	}

	/** An actuator is built and its input traces back to the built clock tower: it cycles. */
	public boolean hasClockedActuatorBuilt() {
		return hasClockedActuator(true);
	}

	/**
	 * The next slot to plan. Slots that a live output already points at come first (the clock
	 * network grows like a root system before the quiet districts fill in), then pending re-plans,
	 * then the rest by distance from the core, x, z. Deterministic.
	 */
	private Optional<SlotKey> nextFrontier(int maxRadius, Set<SlotKey> live) {
		SlotKey bestLive = null;
		SlotKey bestPending = null;
		SlotKey bestAny = null;
		for (Slot s : slots.values()) {
			if (s.status == SlotStatus.PENDING && !deferred.contains(s.key)) {
				bestPending = better(bestPending, s.key);
				if (fedByLive(s.key, live)) {
					bestLive = better(bestLive, s.key);
				}
			}
		}
		for (Slot s : slots.values()) {
			if (!s.occupies()) {
				continue;
			}
			for (Direction d : SIDES) {
				SlotKey k = s.key.offset(d);
				if (slots.containsKey(k) || k.chebyshev() > maxRadius || deferred.contains(k)) {
					continue;
				}
				bestAny = better(bestAny, k);
				if (fedByLive(k, live)) {
					bestLive = better(bestLive, k);
				}
			}
		}
		if (bestLive != null) {
			return Optional.of(bestLive);
		}
		if (bestPending != null) {
			return Optional.of(bestPending);
		}
		return Optional.ofNullable(bestAny);
	}

	/** True when a live neighbour has an output port on the face shared with this slot. */
	private boolean fedByLive(SlotKey k, Set<SlotKey> live) {
		for (Direction side : SIDES) {
			Slot n = slots.get(k.offset(side));
			if (n == null || !live.contains(n.key) || (slots.containsKey(k) && !level(k, n.key))) {
				continue;
			}
			Port p = portFacing(n, side);
			if (p != null && p.dir() == PortDir.OUT) {
				return true;
			}
		}
		return false;
	}

	private static SlotKey better(SlotKey a, SlotKey b) {
		if (a == null) {
			return b;
		}
		int c = Integer.compare(a.chebyshev(), b.chebyshev());
		if (c == 0) {
			c = Integer.compare(a.x(), b.x());
		}
		if (c == 0) {
			c = Integer.compare(a.z(), b.z());
		}
		return c <= 0 ? a : b;
	}

	private int unassignedPlanned() {
		int n = 0;
		for (Slot s : slots.values()) {
			if (s.status == SlotStatus.PLANNED && s.builder == null) {
				n++;
			}
		}
		return n;
	}

	private int faultsPlanted() {
		int n = 0;
		for (Slot s : slots.values()) {
			if (s.faulted) {
				n++;
			}
		}
		return n;
	}

	/** How a slot sits on the land: its floor level, or why it cannot be built, or that the ground is not loaded yet. */
	public record Fit(int y, String reason, boolean unloaded) {
		public boolean ok() {
			return reason == null && !unloaded;
		}
	}

	/** The tallest cell the grammar may choose (forced cells such as the clock tower are checked by their own height). */
	private int maxCellHeight() {
		int h = 1;
		for (Cell c : CellLibrary.all()) {
			boolean chosen = false;
			for (String d : List.of("core", "residential", "forge", "plaza", "ram", "storage")) {
				chosen |= c.definition().weight(d) > 0;
			}
			if (chosen) {
				h = Math.max(h, c.size().getY());
			}
		}
		return h;
	}

	/** The level of a neighbour this slot should join, preferring one with a port on the shared face. */
	private Integer neighbourLevel(SlotKey k) {
		Integer any = null;
		for (Direction side : SIDES) {
			Slot n = slots.get(k.offset(side));
			if (n == null || !n.occupies() || n.cell == null) {
				continue;
			}
			int y = slotY(n.key);
			if (portFacing(n, side) != null) {
				return y;
			}
			if (any == null) {
				any = y;
			}
		}
		return any;
	}

	/**
	 * Fits a slot to the terrain (doc 8): the ground under it must be readable, dry and within the
	 * slope limit; the floor goes at the median ground level, or at a neighbour's level when the
	 * ground is close enough that the street can continue; the drop below must be within reach of
	 * a foundation; and nothing a player built may stand in the way.
	 */
	public Fit fitSlot(ServerLevel level, SlotKey k) {
		SeedCityConfig cfg = cfg();
		BlockPos flat = coreOrigin();
		int x0 = flat.getX() + k.x() * SLOT;
		int z0 = flat.getZ() + k.z() * SLOT;
		if (!Terrain.loaded(level, x0 - 1, z0 - 1, SLOT + 2)) {
			return new Fit(0, "unloaded", true);
		}
		Terrain.Survey sv = Terrain.survey(level, x0, z0, SLOT, cfg.slopeLimit);
		if (!sv.ok()) {
			return new Fit(0, sv.reason(), false);
		}
		int y;
		if (k.chebyshev() == 0) {
			y = flat.getY();   // the Core sits where the Seed was placed
		} else {
			Integer nb = neighbourLevel(k);
			y = nb != null && Math.abs(sv.median() - nb) <= cfg.terrainStep ? nb : sv.median();
		}
		if (y - sv.min() > cfg.foundationDepth) {
			return new Fit(0, "drop", false);
		}
		if (!Terrain.volumeClear(level, x0, z0, SLOT, y, maxCellHeight(), seedPos)) {
			return new Fit(0, "occupied", false);
		}
		return new Fit(y, null, false);
	}

	/** Chunks a slot's footprint touches. */
	private Set<Long> chunksOf(SlotKey k) {
		BlockPos flat = coreOrigin();
		int x0 = flat.getX() + k.x() * SLOT;
		int z0 = flat.getZ() + k.z() * SLOT;
		Set<Long> out = new HashSet<>();
		for (int cx = x0 >> 4; cx <= (x0 + SLOT - 1) >> 4; cx++) {
			for (int cz = z0 >> 4; cz <= (z0 + SLOT - 1) >> 4; cz++) {
				out.add(((long) cx << 32) ^ (cz & 0xFFFFFFFFL));
			}
		}
		return out;
	}

	/** Plans slots ahead of the builders until {@code lookahead} are waiting or the frontier is spent. */
	void planAhead(Function<SlotKey, Fit> fitter, int lookahead, SeedCityConfig cfg) {
		int guard = 0;
		while (unassignedPlanned() < lookahead && guard++ < 512) {
			Set<SlotKey> clocked = clockedSlots(false);
			Set<SlotKey> live = liveSlots(false, false);
			Optional<SlotKey> next = nextFrontier(cfg.maxRadiusSlots, live);
			if (next.isEmpty()) {
				return;
			}
			SlotKey k = next.get();
			Slot s = slots.computeIfAbsent(k, key -> new Slot(key, SlotStatus.PENDING));
			// the hard chunk cap (doc 4.3, 25): a slot that would spread the city past it is never planned
			Set<Long> span = chunkSpan();
			span.addAll(chunksOf(k));
			if (span.size() > cfg.maxChunks) {
				s.status = SlotStatus.BLOCKED;
				s.note = "chunk cap";
				continue;
			}
			Fit fit = fitter.apply(k);
			if (fit.unloaded()) {
				deferred.add(k);
				if (s.status == SlotStatus.PENDING && s.cell == null && !slots.containsKey(k)) {
					slots.remove(k);
				}
				continue;
			}
			if (!fit.ok()) {
				s.status = SlotStatus.BLOCKED;
				s.note = fit.reason();
				continue;
			}
			s.y = fit.y();
			Random rng = new Random(mix(citySeed ^ (k.x() * 0x9E3779B97F4A7C15L) ^ (k.z() * 0xC2B2AE3D27D4EB4FL)));
			boolean haveActuator = false;
			for (Slot o : slots.values()) {
				if (o.cell != null && clocked.contains(o.key) && CellLibrary.get(o.cell).map(c -> c.definition().kind() == CellKind.ACTUATOR).orElse(false)) {
					haveActuator = true;
					break;
				}
			}
			Optional<Goal> goal = haveActuator ? Optional.empty() : Optional.of(Goal.WANT_ACTUATOR);
			FrontierSlot fs = new FrontierSlot(k.x(), k.z(), district(k), Set.copyOf(s.rejected));
			Optional<Choice> choice = Grammar.choose(fs, constraints(k, live), goal, wantedCells(), rng, CellLibrary.all());
			if (choice.isEmpty()) {
				s.status = SlotStatus.BLOCKED;
				s.note = "no legal cell";
				continue;
			}
			s.status = SlotStatus.PLANNED;
			s.cell = choice.get().cell().id();
			s.rotation = choice.get().rotation();
			s.note = "";
			// Plant a fault once the clock reaches enough of the city (doc 7: every city ships with faults).
			CellDefinition def = choice.get().cell().definition();
			if (!s.faulted && faultsPlanted() < cfg.faultsPerCity && clocked.size() >= cfg.faultAfterLiveCells
					&& def.faultable() && fedByLive(k, clocked)) {
				s.faulted = true;
			}
			revisitQuietNeighbours(s);
		}
	}

	/**
	 * A freshly planned cell that points an output at a neighbour which is planned but not yet
	 * started, and which would not listen, sends that neighbour back to planning so the signal
	 * is not wasted on a cell chosen before anything pointed at it.
	 */
	private void revisitQuietNeighbours(Slot s) {
		Optional<Cell> cell = CellLibrary.get(s.cell);
		if (cell.isEmpty()) {
			return;
		}
		for (Direction side : SIDES) {
			Port ours = Grammar.portOn(cell.get().ports(s.rotation), side);
			if (ours == null || ours.dir() != PortDir.OUT) {
				continue;
			}
			Slot n = slots.get(s.key.offset(side));
			if (n == null || n.status != SlotStatus.PLANNED || n.builder != null || n.cell == null) {
				continue;
			}
			if (n.cell.equals(CORE_CELL) || n.cell.equals(CLOCK_CELL)) {
				continue;
			}
			Port theirs = portFacing(n, side);
			if (theirs == null || theirs.dir() != PortDir.IN) {
				n.status = SlotStatus.PENDING;
				n.cell = null;
				n.faulted = false;
			}
		}
	}

	/** Whether a slot fits the land right now (see {@link #fitSlot}). */
	public boolean buildable(ServerLevel level, SlotKey k) {
		return fitSlot(level, k).ok();
	}

	/** Whether a planned slot's volume is still clear at its chosen level (a player may have built there since). */
	private boolean stillClear(ServerLevel level, SlotKey k) {
		BlockPos o = slotOrigin(k);
		int height = placement(k).map(p -> p.footprint().getYSpan()).orElse(maxCellHeight());
		return Terrain.volumeClear(level, o.getX(), o.getZ(), SLOT, o.getY(), height, seedPos);
	}

	// ---- tasks ------------------------------------------------------------------------------

	private boolean affordable(BuildTask.Cost c, SeedCityConfig cfg) {
		return cfg.unlimitedMaterials || (redstone >= c.redstone() && stone >= c.stone() && wood >= c.wood());
	}

	private void spend(BuildTask.Cost c, int sign) {
		redstone += sign * -c.redstone();
		stone += sign * -c.stone();
		wood += sign * -c.wood();
	}

	/** The growth task for a slot: site work from the land, the blueprint, and an apron on its open sides. */
	private BuildTask taskFor(ServerLevel level, Slot s, Placement p) {
		BlockPos omit = s.faulted ? p.cell().definition().fault() : null;
		List<Direction> open = new ArrayList<>();
		for (Direction d : SIDES) {
			Slot n = slots.get(s.key.offset(d));
			if (n == null || !n.occupies()) {
				open.add(d);
			}
		}
		SeedCityConfig cfg = cfg();
		return BuildTask.growth(level, p, s.key, omit, open, this::inCity, cfg.foundationDepth, cfg.apronWidth);
	}

	/**
	 * Hands the next task to a builder in priority order, or nothing if the city is frozen, the
	 * frontier is spent, or the stock cannot cover the next cell (growth stalls).
	 */
	public Optional<BuildTask> claimTask(ServerLevel level, UUID builder) {
		if (frozen) {
			return Optional.empty();
		}
		SeedCityConfig cfg = cfg();
		this.level = level;
		Set<SlotKey> skip = new HashSet<>();
		for (int attempt = 0; attempt < 8; attempt++) {
			planAhead(k -> fitSlot(level, k), LOOKAHEAD, cfg);
			Slot pick = null;
			for (Slot s : slots.values()) {
				if (s.status == SlotStatus.PLANNED && s.builder == null && !adjacentToVerification(s.key) && !skip.contains(s.key)) {
					pick = pick == null || better(pick.key, s.key) == s.key ? s : pick;
				}
			}
			if (pick == null) {
				return Optional.empty();
			}
			if (!stillClear(level, pick.key)) {
				skip.add(pick.key);   // something stands there now; try it again later
				continue;
			}
			Optional<Placement> placement = placement(pick);
			if (placement.isEmpty()) {
				pick.status = SlotStatus.BLOCKED;
				pick.note = "cell " + pick.cell + " not in library";
				continue;
			}
			BuildTask task = taskFor(level, pick, placement.get());
			if (!affordable(task.cost(), cfg)) {
				return Optional.empty();
			}
			spend(task.cost(), 1);
			pick.status = SlotStatus.BUILDING;
			pick.builder = builder;
			pick.since = level.getGameTime();
			return Optional.of(task);
		}
		return Optional.empty();
	}

	public void abandon(BuildTask task) {
		Slot s = slots.get(task.slot());
		if (s != null && s.status == SlotStatus.BUILDING) {
			s.status = SlotStatus.PLANNED;
			s.builder = null;
			spend(task.cost(), -1);
		}
	}

	/** A builder has placed every block. Normal cells queue for verification; faults stay dark. */
	public void onBuildComplete(BuildTask task) {
		Slot s = slots.get(task.slot());
		if (s == null || s.status != SlotStatus.BUILDING) {
			return;
		}
		s.builder = null;
		spend(task.salvage(), -1);   // what was dug out of the way goes to the ledger
		if (s.faulted) {
			s.status = SlotStatus.FAULT;
			SeedCity.LOGGER.info("City {}: fault planted at {} ({})", seedPos.toShortString(), s.key, s.cell);
		} else {
			s.status = SlotStatus.VERIFY;
			pendingVerify.add(task);
		}
	}

	/** A Warden has re-placed a damaged cell; it must prove itself again before it counts as live. */
	public void onRepaired(BuildTask task) {
		Slot s = slots.get(task.slot());
		if (s != null && s.status == SlotStatus.BUILT) {
			s.status = SlotStatus.VERIFY;
			s.retries = 0;
			builtCount--;
			pendingVerify.add(BuildTask.repair(task.placement(), task.slot()));
		}
	}

	private boolean adjacentToVerification(SlotKey k) {
		if (verifyingSlot == null) {
			return false;
		}
		return Math.abs(verifyingSlot.x() - k.x()) + Math.abs(verifyingSlot.z() - k.z()) == 1;
	}

	private boolean neighbourUnderConstruction(SlotKey k) {
		for (Direction d : SIDES) {
			Slot n = slots.get(k.offset(d));
			if (n != null && n.status == SlotStatus.BUILDING) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Starts the next queued verification when none is running and no builder is working next to
	 * the candidate, so probes and builders never touch the same blocks. One at a time per city.
	 */
	private void processVerification(ServerLevel level) {
		if (verifyingSlot != null || pendingVerify.isEmpty()) {
			return;
		}
		for (BuildTask task : pendingVerify) {
			if (neighbourUnderConstruction(task.slot())) {
				continue;
			}
			Slot s = slots.get(task.slot());
			if (s == null || s.status != SlotStatus.VERIFY) {
				pendingVerify.remove(task);
				return;
			}
			pendingVerify.remove(task);
			verifyingSlot = task.slot();
			Verifier.verify(level, task.placement(), builtNeighbours(task.slot()), result -> {
				verifyingSlot = null;
				if (result.pass()) {
					onBuilt(task);
				} else if (s.retries++ < 1) {
					SeedCity.LOGGER.info("City {}: {} at {} failed once ({}); retrying", seedPos.toShortString(), s.cell, s.key, result.message());
					pendingVerify.add(task);
				} else {
					task.placement().clear(level, true);
					onVerifyFailed(task, result.message());
				}
			});
			return;
		}
	}

	private void onBuilt(BuildTask task) {
		Slot s = slots.get(task.slot());
		if (s != null) {
			s.status = SlotStatus.BUILT;
			s.builder = null;
			builtCount++;
			assignOrdinal(s);
		}
	}

	/** The cell failed in place: remember the rejection, re-plan this slot and any unbuilt dependants. */
	private void onVerifyFailed(BuildTask task, String reason) {
		Slot s = slots.get(task.slot());
		if (s == null) {
			return;
		}
		SeedCity.LOGGER.warn("City {}: {} at {} failed verification ({}); re-planning", seedPos.toShortString(), s.cell, s.key, reason);
		s.rejected.add(FrontierSlot.rejectKey(s.cell.toString(), s.rotation.ordinal()));
		s.status = SlotStatus.PENDING;
		s.cell = null;
		s.builder = null;
		s.retries = 0;
		s.faulted = false;
		spend(task.cost(), -1);
		for (Direction d : SIDES) {
			Slot n = slots.get(s.key.offset(d));
			if (n != null && n.status == SlotStatus.PLANNED && n.builder == null) {
				n.status = SlotStatus.PENDING;
				n.cell = null;
			}
		}
	}

	public List<Placement> builtNeighbours(SlotKey k) {
		List<Placement> out = new ArrayList<>();
		for (Direction d : SIDES) {
			Slot n = slots.get(k.offset(d));
			if (n != null && (n.status == SlotStatus.BUILT || n.status == SlotStatus.FAULT)) {
				placement(n).ifPresent(out::add);
			}
		}
		return out;
	}

	/** Where a builder fetches material: the nearest built storage cell, else the core. */
	public BlockPos storageTarget(BlockPos from) {
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		for (Slot s : slots.values()) {
			if (s.status != SlotStatus.BUILT || s.cell == null) {
				continue;
			}
			Optional<Cell> cell = CellLibrary.get(s.cell);
			if (cell.isEmpty() || cell.get().definition().kind() != CellKind.STORAGE) {
				continue;
			}
			BlockPos top = slotOrigin(s.key).offset(3, cell.get().size().getY() + 1, 3);
			double d = top.distSqr(from);
			if (d < bestDist) {
				bestDist = d;
				best = top;
			}
		}
		return best != null ? best : seedPos.above(5);
	}

	public int pendingVerifications() {
		return pendingVerify.size() + (verifyingSlot == null ? 0 : 1);
	}

	/** Chunks touched by every slot the city has or will have. */
	public Set<Long> chunkSpan() {
		Set<Long> chunks = new HashSet<>();
		for (Slot s : slots.values()) {
			if (s.occupies()) {
				chunks.addAll(chunksOf(s.key));
			}
		}
		return chunks;
	}

	/**
	 * Dev and test shortcut: place a cell instantly and record it as built (or as a planted fault).
	 * Skips verification.
	 */
	public Slot adopt(ServerLevel level, SlotKey k, Identifier cellId, Rotation rotation, boolean faulted) {
		Slot s = slots.computeIfAbsent(k, key -> new Slot(key, SlotStatus.PLANNED));
		s.cell = cellId;
		s.rotation = rotation;
		s.faulted = faulted;
		s.builder = null;
		Placement p = placement(s).orElseThrow(() -> new IllegalArgumentException("unknown cell " + cellId));
		BuildTask t = taskFor(level, s, p);
		while (!t.step(level)) {
			// place everything now
		}
		if (faulted) {
			s.status = SlotStatus.FAULT;
		} else {
			if (s.status != SlotStatus.BUILT) {
				builtCount++;
			}
			s.status = SlotStatus.BUILT;
			assignOrdinal(s);
		}
		return s;
	}

	// ---- per-second upkeep -------------------------------------------------------------------

	/** Called once a second by the manager. Re-queues orphans, watches faults, keeps the mobs posted. */
	public void upkeep(ServerLevel level) {
		if (!level.isPositionEntityTicking(seedPos)) {
			return;   // unloaded districts do not tick (doc 26), and spawning into unloaded chunks leaks mobs
		}
		this.level = level;
		if (!level.getBlockState(seedPos).is(SeedCityBlocks.SEED)) {
			if (!frozen) {
				SeedCity.LOGGER.info("City {}: seed destroyed, freezing", seedPos.toShortString());
			}
			frozen = true;
		}
		if (frozen) {
			return;
		}
		SeedCityConfig cfg = cfg();
		deferred.clear();
		List<BuilderEntity> builders = builders(level);
		Set<UUID> alive = new HashSet<>();
		for (BuilderEntity b : builders) {
			alive.add(b.getUUID());
		}
		boolean busy = !pendingVerify.isEmpty() || verifyingSlot != null;
		for (Slot s : slots.values()) {
			if (s.status == SlotStatus.BUILDING && (s.builder == null || !alive.contains(s.builder))) {
				s.status = SlotStatus.PLANNED;
				s.builder = null;
			}
			if (s.status == SlotStatus.BUILDING || s.status == SlotStatus.VERIFY) {
				busy = true;
			}
		}
		long now = level.getGameTime();
		if (busy || lastActivity == 0) {
			lastActivity = now;
		}
		processVerification(level);
		watchFaults(level);
		if (slots.get(new SlotKey(0, 0)) != null && slots.get(new SlotKey(0, 0)).status == SlotStatus.BUILT) {
			ReaderWall.update(level, seedPos, readerLines());
		}
		if (labels) {
			CellLabels.update(level, this);
		}
		int desired = Math.min(cfg.maxBuilders, 1 + builtCount / cfg.cellsPerBuilder);
		if (builders.size() < desired) {
			spawnBuilder(level);
		}
		// extra, outside the five mobs: a couple of Redstone Rats keep creepers off the city
		if (level.getGameTime() >= nextRatSpawnAttempt) {
			nextRatSpawnAttempt = level.getGameTime() + RedstoneRats.PERIOD_TICKS;
			if (level.getNearestPlayer(seedPos.getX(), seedPos.getY(), seedPos.getZ(), 64, false) != null) {
				RedstoneRats.trySpawn(level, seedPos, cfg.maxRedstoneRats);
			}
		}
		keepWardensPosted(level);
		if (cfg.sentinelsOnRegisters) {
			postSentinels(level);
		}
		keepCollectorsWorking(level);
		// L3 (doc 14, 25). An empty reader: the city dreams something to run, or something to build
		// toward, at once by default or after the idle time. A running dream: once it has run its
		// length and the frontier is idle, the city dreams bigger, and the builders grow the hardware.
		long quiet = cfg.dreamAfterSeconds * 20L;
		long length = cfg.dreamLengthSeconds * 20L;
		boolean coreBuilt = slots.get(new SlotKey(0, 0)) != null && slots.get(new SlotKey(0, 0)).status == SlotStatus.BUILT;
		if (coreBuilt && cardText == null && (cfg.dreamAtStart || now - lastActivity >= quiet) && now - lastDream >= 100) {
			dream(level, false);
		} else if (coreBuilt && cardText != null && dreamed && plan == null && now - lastActivity >= quiet && now - lastDream >= length) {
			dream(level, true);
		}
	}

	// ---- collectors: supply (doc 20.1) ------------------------------------------------------

	/** A gather order: what to bring back, and the block to start on. */
	public record GatherOrder(Terrain.Kind kind, BlockPos source) {
	}

	/** Collector to the source it is working. Not persisted. */
	private final Map<UUID, BlockPos> gathering = new HashMap<>();
	/** Sources Collectors could not reach or that turned out empty, and until when to leave them alone. */
	private final Map<BlockPos, Long> forbiddenSources = new HashMap<>();
	private final Map<Terrain.Kind, Long> noSourceUntil = new java.util.EnumMap<>(Terrain.Kind.class);

	/** Material the planned frontier needs beyond what is in stock, plus the configured reserve. */
	public Optional<Terrain.Kind> deficit() {
		SeedCityConfig cfg = cfg();
		if (cfg.unlimitedMaterials) {
			return Optional.empty();
		}
		int needR = cfg.reserveRedstone;
		int needS = cfg.reserveStone;
		int needW = cfg.reserveWood;
		for (Slot s : slots.values()) {
			if (s.status == SlotStatus.PLANNED && s.cell != null) {
				Optional<Cell> cell = CellLibrary.get(s.cell);
				if (cell.isPresent()) {
					BuildTask.Cost c = BuildTask.Cost.of(cell.get().definition().cost());
					needR += c.redstone();
					needS += c.stone();
					needW += c.wood();
				}
			}
		}
		double worst = 0;
		Terrain.Kind pick = null;
		double r = needR > 0 ? (double) (needR - redstone) / needR : 0;
		double st = needS > 0 ? (double) (needS - stone) / needS : 0;
		double w = needW > 0 ? (double) (needW - wood) / needW : 0;
		if (r > worst) {
			worst = r;
			pick = Terrain.Kind.REDSTONE;
		}
		if (st > worst) {
			worst = st;
			pick = Terrain.Kind.STONE;
		}
		if (w > worst) {
			pick = Terrain.Kind.WOOD;
		}
		return Optional.ofNullable(pick);
	}

	/** Hands a Collector something to fetch, or nothing when the stock is fine or no source is within reach. */
	public Optional<GatherOrder> claimGather(ServerLevel level, UUID who, BlockPos from) {
		this.level = level;
		if (frozen) {
			return Optional.empty();
		}
		Optional<Terrain.Kind> kind = deficit();
		if (kind.isEmpty()) {
			return Optional.empty();
		}
		long now = level.getGameTime();
		if (noSourceUntil.getOrDefault(kind.get(), 0L) > now) {
			return Optional.empty();
		}
		SeedCityConfig cfg = cfg();
		int radius = cfg.maxRadiusSlots * SLOT + cfg.collectorRange;
		Optional<BlockPos> src = Terrain.findSource(level, seedPos, kind.get(), radius, this::sourceForbidden);
		if (src.isEmpty()) {
			noSourceUntil.put(kind.get(), now + 600);
			SeedCity.LOGGER.info("City {}: no {} within {} blocks; growth will stall on it", seedPos.toShortString(), kind.get().name, radius);
			return Optional.empty();
		}
		gathering.put(who, src.get());
		return Optional.of(new GatherOrder(kind.get(), src.get()));
	}

	/** Blocks no Collector may take: the city's own footprint and margin, another Collector's find, or a source given up on. */
	public boolean sourceForbidden(BlockPos p) {
		long now = level == null ? 0 : level.getGameTime();
		Long until = forbiddenSources.get(p);
		if (until != null && until > now) {
			return true;
		}
		if (gathering.containsValue(p)) {
			return true;
		}
		for (Direction d : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
			if (inCity(p.relative(d))) {
				return true;
			}
		}
		return inCity(p);
	}

	public void forbidSource(BlockPos p) {
		long now = level == null ? 0 : level.getGameTime();
		forbiddenSources.put(p, now + 20L * 60 * 5);
	}

	public void releaseGather(UUID who) {
		gathering.remove(who);
	}

	/** A Collector brings its load home. */
	public void deposit(Terrain.Kind kind, int amount) {
		switch (kind) {
			case REDSTONE -> redstone += amount;
			case STONE -> stone += amount;
			case WOOD -> wood += amount;
		}
	}

	/** Where a Collector unloads: beside the nearest warehouse, else at the Core. */
	public Vec3 depositPoint(BlockPos from) {
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		for (Slot s : slots.values()) {
			if (s.status != SlotStatus.BUILT || s.cell == null) {
				continue;
			}
			Optional<Cell> cell = CellLibrary.get(s.cell);
			if (cell.isEmpty() || cell.get().definition().kind() != CellKind.STORAGE) {
				continue;
			}
			BlockPos centre = slotOrigin(s.key).offset(3, 1, 3);
			double d = centre.distSqr(from);
			if (d < bestDist) {
				bestDist = d;
				best = centre;
			}
		}
		return Vec3.atCenterOf(best != null ? best : seedPos);
	}

	public List<CollectorEntity> collectors(ServerLevel level) {
		return level.getEntities(SeedCityEntities.COLLECTOR, bounds(), c -> seedPos.equals(c.cityPos()));
	}

	/** Sends Collectors out while the stock is short: one to start, one more per warehouse, up to the cap. */
	private void keepCollectorsWorking(ServerLevel level) {
		SeedCityConfig cfg = cfg();
		if (cfg.unlimitedMaterials || deficit().isEmpty()) {
			return;
		}
		int warehouses = 0;
		for (Slot s : slots.values()) {
			if (s.status == SlotStatus.BUILT && s.cell != null && CellLibrary.get(s.cell).map(c -> c.definition().kind() == CellKind.STORAGE).orElse(false)) {
				warehouses++;
			}
		}
		int desired = Math.min(cfg.maxCollectors, 1 + warehouses);
		if (collectors(level).size() >= desired) {
			return;
		}
		Optional<BlockPos> spot = Terrain.standable(level, seedPos, 6, 24, this::inCity);
		if (spot.isEmpty()) {
			SeedCity.LOGGER.info("City {}: short of {} but no ground near the Core for a Collector to stand on", seedPos.toShortString(), deficit().get().name);
			return;
		}
		CollectorEntity c = SeedCityEntities.COLLECTOR.spawn(level, spot.get(), EntitySpawnReason.MOB_SUMMONED);
		if (c != null) {
			c.setCity(seedPos);
			SeedCity.LOGGER.info("City {}: Collector sent out from {}", seedPos.toShortString(), spot.get().toShortString());
		}
	}

	// ---- L3: the city's dreams (doc 14) ---------------------------------------------------

	public boolean labels() {
		return labels;
	}

	public void setLabels(ServerLevel level, boolean on) {
		labels = on;
		if (on) {
			CellLabels.update(level, this);
		} else {
			CellLabels.remove(level, this);
		}
	}

	public boolean dreaming() {
		return dreamed && (executor != null || plan != null);
	}

	public int dreamCount() {
		return dreamCount;
	}

	/**
	 * The hardware a dream may be written over: actuator inputs, sensor outputs, vault count, the
	 * ALU. With {@code ambitious}, one more of each than exists: an actuator and a sensor the
	 * library could build next, one more vault, the ALU; a dream written over those is a plan the
	 * builders then grow toward.
	 */
	private net.tabor.seedcity.card.Dreamer.Hardware hardware(boolean ambitious) {
		List<String> actuators = new ArrayList<>();
		List<String> sensors = new ArrayList<>();
		Map<String, Integer> counts = new HashMap<>();
		for (Slot s : slots.values()) {
			if (s.status != SlotStatus.BUILT || s.cell == null) {
				continue;
			}
			Optional<Cell> cell = CellLibrary.get(s.cell);
			if (cell.isEmpty()) {
				continue;
			}
			CellDefinition def = cell.get().definition();
			counts.merge(s.cell.getPath(), 1, Integer::sum);
			if (def.kind() == CellKind.ACTUATOR) {
				for (Port p : def.inputs()) {
					actuators.add(s.cell.getPath() + "." + s.ordinal + "." + p.name());
				}
			} else if (def.kind() == CellKind.SENSOR) {
				for (Port p : def.outputs()) {
					sensors.add(s.cell.getPath() + "." + s.ordinal + "." + p.name());
				}
			}
		}
		int registers = builtOfType("register_block").size();
		boolean alu = !builtOfType("alu_sub").isEmpty() && !builtOfType("alu_not").isEmpty() && !builtOfType("alu_or").isEmpty();
		if (ambitious) {
			for (Cell c : CellLibrary.all()) {
				CellDefinition def = c.definition();
				boolean growable = false;
				for (String d : List.of("core", "residential", "forge", "plaza", "ram", "storage")) {
					growable |= def.weight(d) > 0;
				}
				if (!growable) {
					continue;
				}
				int next = counts.getOrDefault(c.id().getPath(), 0) + 1;
				if (def.kind() == CellKind.ACTUATOR && !def.inputs().isEmpty()) {
					actuators.add(c.id().getPath() + "." + next + "." + def.inputs().getFirst().name());
				} else if (def.kind() == CellKind.SENSOR && !def.outputs().isEmpty()) {
					sensors.add(c.id().getPath() + "." + next + "." + def.outputs().getFirst().name());
				}
			}
			registers++;
			alu = true;
		}
		return new net.tabor.seedcity.card.Dreamer.Hardware(actuators, sensors, registers, alu);
	}

	/**
	 * Composes a card from the fragment library and puts it in the reader as a real book, authored
	 * by the city. Over the hardware that exists it runs at once; over more than exists it is a
	 * plan the builders grow toward while the previous dream keeps running. {@code ambitious}
	 * asks for the plan first. A previous dream goes to a warehouse chest. True when a card went in.
	 */
	public boolean dream(ServerLevel level, boolean ambitious) {
		this.level = level;
		lastDream = level.getGameTime();
		if (cardText != null && !dreamed) {
			return false;   // never over a player's card
		}
		boolean[] order = ambitious ? new boolean[] {true, false} : new boolean[] {false, true};
		for (boolean bigger : order) {
			net.tabor.seedcity.card.Dreamer.Hardware hw = hardware(bigger);
			Random rng = new Random(mix(citySeed ^ (0xD2EA0000L + dreamCount) ^ (bigger ? 0xA5B1710L : 0)));
			for (int attempt = 0; attempt < 8; attempt++) {
				Optional<net.tabor.seedcity.card.Dreamer.Dream> d = net.tabor.seedcity.card.Dreamer.compose(
						net.tabor.seedcity.card.FragmentLibrary.all(), hw, rng, dreamCount + 1);
				if (d.isEmpty()) {
					break;
				}
				try {
					net.tabor.seedcity.card.Program program = net.tabor.seedcity.card.CardParser.parse(d.get().text());
					Executor e = Executor.resolve(program, this);
					if (!bigger && !e.live()) {
						continue;   // a dream over what exists must run now
					}
					if (bigger && e.live() && !ambitious) {
						continue;   // asked for something to build toward; this asks for nothing
					}
				} catch (net.tabor.seedcity.card.CardError ex) {
					SeedCity.LOGGER.warn("City {}: dream did not parse ({}); fragments {}", seedPos.toShortString(), ex, d.get().fragments());
					continue;
				}
				ItemStack previous = dreamed ? cardItem : ItemStack.EMPTY;
				CardResult result = insertCard(level, d.get().text(), dreamBook(d.get()));
				if (!result.accepted()) {
					continue;
				}
				dreamed = true;
				dreamCount++;
				if (!previous.isEmpty()) {
					stash(level, previous);
				}
				SeedCity.LOGGER.info("City {}: dreaming {} ({}){}", seedPos.toShortString(), d.get().title(), String.join(" + ", d.get().fragments()),
						plan != null ? ", a plan: needs " + String.join(", ", plan.needs()) : "");
				return true;
			}
		}
		return false;
	}

	private ItemStack dreamBook(net.tabor.seedcity.card.Dreamer.Dream d) {
		ItemStack book = new ItemStack(net.minecraft.world.item.Items.WRITTEN_BOOK);
		List<net.minecraft.server.network.Filterable<net.minecraft.network.chat.Component>> pages = new ArrayList<>();
		String[] lines = d.text().split("\n");
		StringBuilder page = new StringBuilder();
		int n = 0;
		for (String line : lines) {
			page.append(line).append('\n');
			if (++n == 12) {
				pages.add(net.minecraft.server.network.Filterable.passThrough(net.minecraft.network.chat.Component.literal(page.toString())));
				page.setLength(0);
				n = 0;
			}
		}
		if (!page.isEmpty()) {
			pages.add(net.minecraft.server.network.Filterable.passThrough(net.minecraft.network.chat.Component.literal(page.toString())));
		}
		book.set(net.minecraft.core.component.DataComponents.WRITTEN_BOOK_CONTENT,
				new net.minecraft.world.item.component.WrittenBookContent(net.minecraft.server.network.Filterable.passThrough(d.title()), "the city", 0, pages, true));
		return book;
	}

	/** Cards the city has run end up in a warehouse (docs/city-as-computer.md): the first free slot of a container in a storage cell. */
	private void stash(ServerLevel level, ItemStack item) {
		for (Slot s : slots.values()) {
			if (s.status != SlotStatus.BUILT || s.cell == null) {
				continue;
			}
			Optional<Cell> cell = CellLibrary.get(s.cell);
			if (cell.isEmpty() || cell.get().definition().kind() != CellKind.STORAGE) {
				continue;
			}
			Optional<Placement> p = placement(s);
			if (p.isEmpty()) {
				continue;
			}
			for (BlockPos pos : BlockPos.betweenClosed(p.get().footprint().minX(), p.get().footprint().minY(), p.get().footprint().minZ(),
					p.get().footprint().maxX(), p.get().footprint().maxY(), p.get().footprint().maxZ())) {
				if (level.getBlockEntity(pos) instanceof net.minecraft.world.Container container) {
					for (int i = 0; i < container.getContainerSize(); i++) {
						if (container.getItem(i).isEmpty()) {
							container.setItem(i, item);
							container.setChanged();
							return;
						}
					}
				}
			}
		}
		net.minecraft.world.Containers.dropItemStack(level, seedPos.getX() + 0.5, seedPos.getY() + 1.5, seedPos.getZ() + 0.5, item);
	}

	/** A player who restores a Fault Cell's missing block gets the district back: verify, then the Warden comes. */
	private void watchFaults(ServerLevel level) {
		for (Slot s : slots.values()) {
			if (s.status != SlotStatus.FAULT) {
				continue;
			}
			Optional<Placement> p = placement(s);
			if (p.isPresent() && Integrity.damaged(level, p.get()).isEmpty()) {
				SeedCity.LOGGER.info("City {}: fault at {} repaired by hand; verifying", seedPos.toShortString(), s.key);
				s.faulted = false;
				s.status = SlotStatus.VERIFY;
				s.retries = 0;
				pendingVerify.add(new BuildTask(p.get(), s.key));
			}
		}
	}

	public List<BuilderEntity> builders(ServerLevel level) {
		return level.getEntities(SeedCityEntities.BUILDER, bounds(), b -> seedPos.equals(b.cityPos()));
	}

	public List<WardenEntity> wardens(ServerLevel level) {
		return level.getEntities(SeedCityEntities.WARDEN, bounds(), w -> seedPos.equals(w.cityPos()));
	}

	public List<SentinelEntity> sentinels(ServerLevel level) {
		return level.getEntities(SeedCityEntities.SENTINEL, bounds(), s -> seedPos.equals(s.cityPos()));
	}

	private AABB bounds() {
		return new AABB(seedPos).inflate(cfg().maxRadiusSlots * SLOT + cfg().collectorRange + 24.0);
	}

	public void spawnBuilder(ServerLevel level) {
		// above the Core roof: a mob that spawns overlapping a block never finds a path out
		BuilderEntity b = SeedCityEntities.BUILDER.spawn(level, seedPos.above(5), EntitySpawnReason.MOB_SUMMONED);
		if (b != null) {
			b.setCity(seedPos);
		}
	}

	/** One Warden per district with built cells, unless the district has an open fault or one died recently. */
	private void keepWardensPosted(ServerLevel level) {
		Set<String> posted = new HashSet<>();
		for (WardenEntity w : wardens(level)) {
			posted.add(w.district());
		}
		long now = level.getGameTime();
		for (String district : districts()) {
			if (posted.contains(district) || districtHasFault(district) || districtSlots(district).isEmpty()) {
				continue;
			}
			if (wardenCooldown.getOrDefault(district, 0L) > now) {
				continue;
			}
			spawnWarden(level, district);
		}
	}

	public void spawnWarden(ServerLevel level, String district) {
		WardenEntity w = SeedCityEntities.WARDEN.spawn(level, seedPos.above(5), EntitySpawnReason.MOB_SUMMONED);
		if (w != null) {
			w.assign(seedPos, district);
			SeedCity.LOGGER.info("City {}: Warden posted to {}", seedPos.toShortString(), district);
		}
	}

	public void noteWardenDeath(String district, long gameTime) {
		wardenCooldown.put(district, gameTime + cfg().wardenRespawnSeconds * 20L);
	}

	/** Every built register vault gets a Sentinel once, reading the vault's output. */
	private void postSentinels(ServerLevel level) {
		for (Slot s : slots.values()) {
			if (s.status != SlotStatus.BUILT || s.guarded || s.cell == null) {
				continue;
			}
			Optional<Cell> cell = CellLibrary.get(s.cell);
			if (cell.isEmpty() || !"register".equals(cell.get().definition().truth())) {
				continue;
			}
			s.guarded = true;
			spawnSentinel(level, s.key, "out");
		}
	}

	public Optional<SentinelEntity> spawnSentinel(ServerLevel level, SlotKey k, String port) {
		Optional<Placement> p = placement(k);
		if (p.isEmpty()) {
			return Optional.empty();
		}
		BlockPos post = p.get().footprint().getCenter().atY(p.get().footprint().maxY() + 1);
		SentinelEntity e = SeedCityEntities.SENTINEL.spawn(level, post, EntitySpawnReason.MOB_SUMMONED);
		if (e == null) {
			return Optional.empty();
		}
		e.bind(seedPos, k, port, post);
		return Optional.of(e);
	}

	public String summary() {
		int planned = 0, building = 0, verify = 0, built = 0, fault = 0, blocked = 0, pending = 0;
		for (Slot s : slots.values()) {
			switch (s.status) {
				case PLANNED -> planned++;
				case BUILDING -> building++;
				case VERIFY -> verify++;
				case BUILT -> built++;
				case FAULT -> fault++;
				case BLOCKED -> blocked++;
				case PENDING -> pending++;
			}
		}
		return "city@" + seedPos.toShortString() + (frozen ? " FROZEN" : "") + " seed=" + Long.toHexString(citySeed)
				+ " built=" + built + " faults=" + fault + " verifying=" + verify + " building=" + building + " planned=" + planned
				+ " pending=" + pending + " blocked=" + blocked
				+ " stock=" + redstone + "r/" + stone + "s/" + wood + "w"
				+ (dreamCount > 0 ? " dreams=" + dreamCount : "")
				+ " program=[" + (dreamed ? "dream: " : "") + programSummary() + "]";
	}

	public List<String> describeSlots() {
		List<Slot> list = new ArrayList<>(slots.values());
		list.sort(Comparator.comparingInt((Slot s) -> s.key.chebyshev()).thenComparingInt(s -> s.key.x()).thenComparingInt(s -> s.key.z()));
		List<String> out = new ArrayList<>();
		for (Slot s : list) {
			out.add(s + " {" + district(s.key) + "}" + (s.y != null ? " y=" + s.y : "") + (s.note.isEmpty() ? "" : " [" + s.note + "]"));
		}
		return out;
	}
}
