package net.tabor.seedcity.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
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
import net.tabor.seedcity.entity.SeedCityEntities;
import net.tabor.seedcity.entity.SentinelEntity;
import net.tabor.seedcity.entity.WardenEntity;
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
							long since, String note, boolean faulted, boolean guarded) {
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
				Codec.BOOL.optionalFieldOf("guarded", false).forGetter(SlotData::guarded)
		).apply(i, SlotData::new));

		static SlotData of(Slot s) {
			return new SlotData(s.key.x(), s.key.z(), s.status.name(), Optional.ofNullable(s.cell), s.rotation,
					List.copyOf(s.rejected), s.since, s.note, s.faulted, s.guarded);
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
			if (s.status == SlotStatus.BUILDING || s.status == SlotStatus.VERIFY) {
				s.status = SlotStatus.PLANNED;   // re-run the task after a reload; correct blocks are skipped
			}
			return s;
		}
	}

	public static final Codec<CityState> CODEC = RecordCodecBuilder.create(i -> i.group(
			BlockPos.CODEC.fieldOf("seed").forGetter(c -> c.seedPos),
			Codec.LONG.fieldOf("city_seed").forGetter(c -> c.citySeed),
			Codec.BOOL.fieldOf("frozen").forGetter(c -> c.frozen),
			Codec.INT.fieldOf("built").forGetter(c -> c.builtCount),
			Codec.INT.fieldOf("redstone").forGetter(c -> c.redstone),
			Codec.INT.fieldOf("stone").forGetter(c -> c.stone),
			Codec.INT.fieldOf("wood").forGetter(c -> c.wood),
			SlotData.CODEC.listOf().fieldOf("slots").forGetter(c -> c.slots.values().stream().map(SlotData::of).toList())
	).apply(i, CityState::new));

	private final BlockPos seedPos;
	private long nextRatSpawnAttempt;
	private final long citySeed;
	private boolean frozen;
	private int builtCount;
	private int redstone;
	private int stone;
	private int wood;
	private final Map<SlotKey, Slot> slots = new LinkedHashMap<>();
	/** Finished cells waiting for their turn under the verifier. Not persisted. */
	private final List<BuildTask> pendingVerify = new ArrayList<>();
	private SlotKey verifyingSlot;
	/** District name to game time before which the Core will not send a replacement Warden. */
	private final Map<String, Long> wardenCooldown = new HashMap<>();
	/** Per-city config, set by tests or tools; null means the global config. Not persisted. */
	private SeedCityConfig configOverride;

	private CityState(BlockPos seedPos, long citySeed, boolean frozen, int builtCount, int redstone, int stone, int wood, List<SlotData> slotData) {
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
	}

	/**
	 * A fresh city rooted at a Seed. The core, the clock tower south of it, and a junction on the
	 * clock's output are planned by force: the clock has one output, and a dead end there would
	 * cap the whole city's signal at one cell.
	 */
	public static CityState create(long worldSeed, BlockPos seedPos, SeedCityConfig cfg) {
		long citySeed = cfg.citySeedOverride != 0 ? cfg.citySeedOverride : mix(worldSeed ^ seedPos.asLong());
		CityState c = new CityState(seedPos, citySeed, false, 0, cfg.initialRedstone, cfg.initialStone, cfg.initialWood, List.of());
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
		CityState c = new CityState(BlockPos.ZERO, citySeed, false, 0, cfg.initialRedstone, cfg.initialStone, cfg.initialWood, List.of());
		c.forceRoot();
		c.planAhead(k -> true, count, cfg);
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

	public BlockPos coreOrigin() {
		return seedPos.offset(-3, -1, -3);
	}

	public BlockPos slotOrigin(SlotKey k) {
		return coreOrigin().offset(k.x() * SLOT, 0, k.z() * SLOT);
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

	/** Districts are distance bands until Phase 4 makes them functional zones. */
	public String district(SlotKey k) {
		int d = k.chebyshev();
		if (d <= 1) {
			return "core";
		}
		return d == 2 ? "plaza" : "residential";
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

	/** Reads a port of a built cell: the strength its port block emits. -1 when unavailable. */
	public int readPort(SlotKey k, String port) {
		Optional<Placement> p = placement(k);
		if (p.isEmpty()) {
			return -1;
		}
		return -1;
	}

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
					if (n == null || !live.contains(n.key)) {
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
			if (s.status == SlotStatus.PENDING) {
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
				if (slots.containsKey(k) || k.chebyshev() > maxRadius) {
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
			if (n == null || !live.contains(n.key)) {
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

	/** Plans slots ahead of the builders until {@code lookahead} are waiting or the frontier is spent. */
	void planAhead(Predicate<SlotKey> buildable, int lookahead, SeedCityConfig cfg) {
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
			if (!buildable.test(k)) {
				s.status = SlotStatus.BLOCKED;
				s.note = "not buildable";
				continue;
			}
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
			Optional<Choice> choice = Grammar.choose(fs, constraints(k, live), goal, rng, CellLibrary.all());
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

	/**
	 * A slot is buildable when its floor layer is solid and everything above it up to the tallest
	 * cell is air (or the Seed itself). Anything else, including player builds, blocks the slot.
	 */
	public boolean buildable(ServerLevel level, SlotKey k) {
		BlockPos origin = slotOrigin(k);
		int maxHeight = 1;
		for (Cell c : CellLibrary.all()) {
			maxHeight = Math.max(maxHeight, c.size().getY());
		}
		for (int x = 0; x < SLOT; x++) {
			for (int z = 0; z < SLOT; z++) {
				BlockPos floor = origin.offset(x, 0, z);
				BlockState f = level.getBlockState(floor);
				if (f.isAir() || !f.isCollisionShapeFullBlock(level, floor)) {
					return false;
				}
				for (int y = 1; y < maxHeight; y++) {
					BlockPos p = floor.above(y);
					BlockState st = level.getBlockState(p);
					if (st.isAir() || st.canBeReplaced() || st.is(SeedCityBlocks.PROBE)) {
						continue;   // probes are ours and transient
					}
					if (p.equals(seedPos) && st.is(SeedCityBlocks.SEED)) {
						continue;
					}
					return false;
				}
			}
		}
		return true;
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

	private BuildTask taskFor(Slot s, Placement p) {
		BlockPos omit = s.faulted ? p.cell().definition().fault() : null;
		return new BuildTask(p, s.key, omit);
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
		if (chunkSpan() >= cfg.maxChunks) {
			return Optional.empty();
		}
		for (int attempt = 0; attempt < 8; attempt++) {
			planAhead(k -> buildable(level, k), LOOKAHEAD, cfg);
			Slot pick = null;
			for (Slot s : slots.values()) {
				if (s.status == SlotStatus.PLANNED && s.builder == null && !adjacentToVerification(s.key)) {
					pick = pick == null || better(pick.key, s.key) == s.key ? s : pick;
				}
			}
			if (pick == null) {
				return Optional.empty();
			}
			if (!buildable(level, pick.key)) {
				pick.status = SlotStatus.BLOCKED;
				pick.note = "not buildable";
				continue;
			}
			Optional<Placement> placement = placement(pick);
			if (placement.isEmpty()) {
				pick.status = SlotStatus.BLOCKED;
				pick.note = "cell " + pick.cell + " not in library";
				continue;
			}
			BuildTask task = taskFor(pick, placement.get());
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

	private int chunkSpan() {
		Set<Long> chunks = new HashSet<>();
		for (Slot s : slots.values()) {
			if (s.status == SlotStatus.BUILT) {
				BlockPos o = slotOrigin(s.key);
				chunks.add(((long) (o.getX() >> 4) << 32) ^ ((o.getZ() >> 4) & 0xFFFFFFFFL));
			}
		}
		return chunks.size();
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
		BuildTask t = taskFor(s, p);
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
		}
		return s;
	}

	// ---- per-second upkeep -------------------------------------------------------------------

	/** Called once a second by the manager. Re-queues orphans, watches faults, keeps the mobs posted. */
	public void upkeep(ServerLevel level) {
		if (!level.isPositionEntityTicking(seedPos)) {
			return;   // unloaded districts do not tick (doc 26), and spawning into unloaded chunks leaks mobs
		}
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
		List<BuilderEntity> builders = builders(level);
		Set<UUID> alive = new HashSet<>();
		for (BuilderEntity b : builders) {
			alive.add(b.getUUID());
		}
		for (Slot s : slots.values()) {
			if (s.status == SlotStatus.BUILDING && (s.builder == null || !alive.contains(s.builder))) {
				s.status = SlotStatus.PLANNED;
				s.builder = null;
			}
		}
		processVerification(level);
		watchFaults(level);
		int desired = Math.min(cfg.maxBuilders, 1 + builtCount / cfg.cellsPerBuilder);
		if (builders.size() < desired) {
			spawnBuilder(level);
		}
        if(level.getGameTime()>=nextRatSpawnAttempt) {
            nextRatSpawnAttempt=level.getGameTime()+1200;
            if(level.getNearestPlayer(seedPos.getX(),seedPos.getY(),seedPos.getZ(),64,false)!=null)
                net.tabor.seedcity.entity.RedstoneRatSpawning.trySpawn(level,seedPos,cfg.maxRedstoneRats);
        }
		keepWardensPosted(level);
		if (cfg.sentinelsOnRegisters) {
			postSentinels(level);
		}
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
		return new AABB(seedPos).inflate(cfg().maxRadiusSlots * SLOT + 24.0);
	}

	public void spawnBuilder(ServerLevel level) {
		BuilderEntity b = SeedCityEntities.BUILDER.spawn(level, seedPos.above(2), EntitySpawnReason.MOB_SUMMONED);
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
		Optional<BlockPos> feet = WardenEntity.groundSpawn(level, seedPos);
		if (feet.isEmpty()) return; // Retry on the next posting pass when floor space opens up.
		WardenEntity w = SeedCityEntities.WARDEN.spawn(level, feet.get(), EntitySpawnReason.MOB_SUMMONED);
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
				+ " stock=" + redstone + "r/" + stone + "s/" + wood + "w";
	}

	public List<String> describeSlots() {
		List<Slot> list = new ArrayList<>(slots.values());
		list.sort(Comparator.comparingInt((Slot s) -> s.key.chebyshev()).thenComparingInt(s -> s.key.x()).thenComparingInt(s -> s.key.z()));
		List<String> out = new ArrayList<>();
		for (Slot s : list) {
			out.add(s + (s.note.isEmpty() ? "" : " [" + s.note + "]"));
		}
		return out;
	}
}
