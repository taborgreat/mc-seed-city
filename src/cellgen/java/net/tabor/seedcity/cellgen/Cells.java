package net.tabor.seedcity.cellgen;

import java.util.List;

import static net.tabor.seedcity.cellgen.Dir.EAST;
import static net.tabor.seedcity.cellgen.Dir.NORTH;
import static net.tabor.seedcity.cellgen.Dir.SOUTH;
import static net.tabor.seedcity.cellgen.Dir.UP;
import static net.tabor.seedcity.cellgen.Dir.WEST;

/**
 * The Phase 0 cell library, authored as circuits.
 *
 * <p>Every cell is 7 wide (x) by 7 deep (z) with a solid floor at y=0 and its ports at y=1 on
 * the centre of each face: north (3,1,0), south (3,1,6), west (0,1,3), east (6,1,3). Signal in
 * this library flows north to south unless a port says otherwise. Keeping one footprint makes the
 * Phase 1 frontier a plain 7x7 grid.
 *
 * <p>Port block conventions: a 1-bit port is a repeater, a 4-bit port is a comparator; in-ports
 * output inward, out-ports output outward. Comparator chains carry strength losslessly, dust
 * loses one per block, repeaters restore to 15.
 */
public final class Cells {
	static final String STONE = "minecraft:smooth_stone";
	static final String BRICK = "minecraft:stone_bricks";
	static final String GLASS = "minecraft:light_blue_stained_glass";
	static final String PLANKS = "minecraft:oak_planks";

	private Cells() {
	}

	public static List<CellBuilder> all() {
		return List.of(busSegment(), inverter(), registerBlock(), clockTower(), drawbridge(), storageCell(),
				core(), decorPlaza(), daylightPlaza(), wireSegment(), junction(),
				aluSub(), aluNot(), aluOr(), vault());
	}

	/**
	 * The vault (design doc 7: loot behind logic). A brick strongroom with a loot chest whose iron
	 * door opens only when the input reads exactly 15: a subtract-mode comparator takes 14 off the
	 * input (a torch two dust away is a constant 14) and what is left, 1 or 0, powers the door.
	 * A program has to compute the key; 14 is not enough.
	 */
	static CellBuilder vault() {
		CellBuilder b = shell("vault", 5);
		// the strongroom: walls, roof, chest, a lantern
		b.walls(1, 1, 2, 5, 3, 6, "minecraft:polished_deepslate");
		b.fill(1, 4, 2, 5, 4, 6, "minecraft:polished_deepslate");
		b.set(3, 1, 4, BlockSpec.of("minecraft:chest", "facing", "north"));
		b.set(3, 3, 4, BlockSpec.of("minecraft:lantern", "hanging", "true"));
		// the door in the north wall
		b.set(3, 1, 2, BlockSpec.of("minecraft:iron_door", "facing", "north", "half", "lower", "hinge", "left", "open", "false", "powered", "false"));
		b.set(3, 2, 2, BlockSpec.of("minecraft:iron_door", "facing", "north", "half", "upper", "hinge", "left", "open", "false", "powered", "false"));
		// the lock: in - 14, into the door
		b.comparator(3, 1, 0, SOUTH, false);
		b.comparator(3, 1, 1, SOUTH, true);
		b.torch(0, 1, 1);
		b.dust(1, 1, 1).dust(2, 1, 1);
		return b.kind("actuator").truth("actuator").loot("minecraft:chests/simple_dungeon")
				.port("in", "in", NORTH, 3, 1, 0, 4)
				.weight("core", 0).weight("residential", 1).weight("forge", 0).weight("plaza", 2).weight("ram", 1).weight("storage", 3)
				.cost(12, 120, 10);
	}

	static final String DARK = "minecraft:polished_blackstone_bricks";
	static final String DARK_GLASS = "minecraft:gray_stained_glass";

	/** Compute-district shell: dark bricks, low profile. The Forge look. */
	private static CellBuilder forgeShell(String id) {
		CellBuilder b = new CellBuilder(id, 7, 4, 7);
		b.fill(0, 0, 0, 6, 0, 6, "minecraft:polished_blackstone");
		b.fill(0, 1, 0, 0, 2, 0, DARK).fill(6, 1, 0, 6, 2, 0, DARK).fill(0, 1, 6, 0, 2, 6, DARK).fill(6, 1, 6, 6, 2, 6, DARK);
		return b;
	}

	/**
	 * Analog subtract: out = max(a - b, 0). a enters from the north through three comparators into
	 * a comparator in subtract mode; b enters from the west through three comparators into its
	 * side. One native comparator operation, dressed as a cell.
	 */
	static CellBuilder aluSub() {
		CellBuilder b = forgeShell("alu_sub");
		b.comparator(3, 1, 0, SOUTH, false).comparator(3, 1, 1, SOUTH, false).comparator(3, 1, 2, SOUTH, false);
		b.comparator(3, 1, 3, SOUTH, true);                               // rear a, side b
		b.comparator(3, 1, 4, SOUTH, false).comparator(3, 1, 5, SOUTH, false).comparator(3, 1, 6, SOUTH, false);
		b.comparator(0, 1, 3, EAST, false).comparator(1, 1, 3, EAST, false).comparator(2, 1, 3, EAST, false);
		b.set(3, 2, 3, DARK_GLASS);
		return b.kind("logic").truth("sub")
				.port("a", "in", NORTH, 3, 1, 0, 4)
				.port("b", "in", WEST, 0, 1, 3, 4)
				.port("out", "out", SOUTH, 3, 1, 6, 4)
				.weight("core", 0).weight("residential", 0).weight("forge", 8).weight("plaza", 0).weight("ram", 0).weight("storage", 0)
				.cost(30, 60, 0).setpiece(false);
	}

	/**
	 * Analog complement: out = 15 - a. A comparator in subtract mode with a constant 15 on its
	 * rear (a torch through a repeater) and a on its side. a comes in from the north into block
	 * B1; a comparator reading B1 points south into the side of the subtractor, which faces west
	 * and outputs east into block B2; a comparator reads B2 south into block B3; dust beside B3
	 * feeds the output comparator on the south face.
	 */
	static CellBuilder aluNot() {
		CellBuilder b = forgeShell("alu_not");
		b.comparator(3, 1, 0, SOUTH, false);          // a in
		b.set(3, 1, 1, DARK);                         // B1 = a
		b.comparator(3, 1, 2, SOUTH, false);          // reads B1, points into the subtractor's side
		b.torch(1, 1, 3);                             // constant
		b.repeater(2, 1, 3, EAST, 1);                 // 15 into the subtractor's rear
		b.comparator(3, 1, 3, EAST, true);            // rear 15, side a: 15 - a, outputs east
		b.set(4, 1, 3, DARK);                         // B2 = 15 - a
		b.comparator(4, 1, 4, SOUTH, false);          // reads B2
		b.set(4, 1, 5, DARK);                         // B3 = 15 - a
		b.dust(3, 1, 5);                              // tap beside B3
		b.comparator(3, 1, 6, SOUTH, false);          // out
		b.set(3, 2, 3, DARK_GLASS);
		return b.kind("logic").truth("complement")
				.port("a", "in", NORTH, 3, 1, 0, 4)
				.port("out", "out", SOUTH, 3, 1, 6, 4)
				.weight("core", 0).weight("residential", 0).weight("forge", 8).weight("plaza", 0).weight("ram", 0).weight("storage", 0)
				.cost(24, 60, 0).setpiece(false);
	}

	/** Analog OR: out = max(a, b). Both inputs drive one block; a block takes the stronger. */
	static CellBuilder aluOr() {
		CellBuilder b = forgeShell("alu_or");
		b.comparator(3, 1, 0, SOUTH, false).comparator(3, 1, 1, SOUTH, false).comparator(3, 1, 2, SOUTH, false);
		b.comparator(0, 1, 3, EAST, false).comparator(1, 1, 3, EAST, false).comparator(2, 1, 3, EAST, false);
		b.set(3, 1, 3, DARK);                                            // M = max(a, b)
		b.comparator(3, 1, 4, SOUTH, false).comparator(3, 1, 5, SOUTH, false).comparator(3, 1, 6, SOUTH, false);
		b.set(3, 2, 3, DARK_GLASS);
		return b.kind("logic").truth("max")
				.port("a", "in", NORTH, 3, 1, 0, 4)
				.port("b", "in", WEST, 0, 1, 3, 4)
				.port("out", "out", SOUTH, 3, 1, 6, 4)
				.weight("core", 0).weight("residential", 0).weight("forge", 8).weight("plaza", 0).weight("ram", 0).weight("storage", 0)
				.cost(28, 60, 0).setpiece(false);
	}

	/**
	 * The Core (design doc 13): the first cell, built around the Seed. A structure void at the
	 * Seed position leaves the Seed in place. Open on all four sides so it is always reachable.
	 * Never chosen by the grammar (zero weights); the planner places it by force.
	 */
	static CellBuilder core() {
		CellBuilder b = shell("core", 5);
		b.set(3, 1, 3, "minecraft:structure_void");
		for (int[] c : new int[][] {{1, 1}, {5, 1}, {1, 5}, {5, 5}}) {
			b.fill(c[0], 1, c[1], c[0], 3, c[1], "minecraft:chiseled_stone_bricks");
		}
		b.fill(0, 4, 0, 6, 4, 6, BRICK);
		b.set(3, 4, 3, GLASS);
		b.set(3, 3, 3, BlockSpec.of("minecraft:lantern", "hanging", "true"));
		b.set(2, 0, 3, "minecraft:chiseled_stone_bricks").set(4, 0, 3, "minecraft:chiseled_stone_bricks")
				.set(3, 0, 2, "minecraft:chiseled_stone_bricks").set(3, 0, 4, "minecraft:chiseled_stone_bricks");
		// the Card Reader: a slot on the south side of the chamber, facing the Seed
		b.set(3, 1, 5, "seedcity:card_reader");
		return b.kind("core").truth("none")
				.weight("core", 0).weight("residential", 0).weight("forge", 0).weight("plaza", 0)
				.cost(0, 40, 0);
	}

	/** A paved square with a fountain in the middle and four lantern posts. No ports: the grammar's fallback that always fits. */
	static CellBuilder decorPlaza() {
		CellBuilder b = shell("decor_plaza", 5);
		b.fill(0, 0, 0, 6, 0, 6, "minecraft:polished_andesite");
		for (int[] c : new int[][] {{1, 1}, {5, 1}, {1, 5}, {5, 5}}) {
			b.set(c[0], 1, c[1], "minecraft:oak_fence").set(c[0], 2, c[1], "minecraft:oak_fence");
			b.set(c[0], 3, c[1], BlockSpec.of("minecraft:lantern", "hanging", "false"));
		}
		// the fountain: a brick basin around one block of water, a column rising from it
		b.fill(2, 0, 2, 4, 0, 4, BRICK);
		b.set(2, 1, 3, BRICK).set(4, 1, 3, BRICK).set(3, 1, 2, BRICK).set(3, 1, 4, BRICK);
		b.set(3, 1, 3, BlockSpec.of("minecraft:water", "level", "0"));
		b.set(3, 2, 3, "minecraft:stone_brick_wall").set(3, 3, 3, BlockSpec.of("minecraft:lantern", "hanging", "false"));
		return b.kind("decor").truth("none")
				.weight("core", 1).weight("residential", 2).weight("forge", 0).weight("plaza", 2).weight("ram", 1).weight("storage", 1)
				.cost(0, 40, 8);
	}

	/** A daylight detector under open sky read by a comparator: the city's slow breath, 4-bit out. */
	static CellBuilder daylightPlaza() {
		CellBuilder b = shell("daylight_plaza", 3);
		b.set(3, 1, 5, BlockSpec.of("minecraft:daylight_detector", "inverted", "false", "power", "0"));
		b.comparator(3, 1, 6, SOUTH, false);
		b.walls(1, 1, 1, 5, 1, 5, "minecraft:stone_brick_slab");
		b.set(3, 1, 5, BlockSpec.of("minecraft:daylight_detector", "inverted", "false", "power", "0"));
		b.set(3, 1, 1, "minecraft:air").set(1, 1, 3, "minecraft:air").set(5, 1, 3, "minecraft:air");
		return b.kind("sensor").truth("sensor")
				.port("out", "out", SOUTH, 3, 1, 6, 4)
				.weight("core", 0).weight("residential", 1).weight("forge", 0).weight("plaza", 2).weight("ram", 0).weight("storage", 0)
				.cost(4, 40, 0);
	}

	/** A 1-bit street: repeater in, dust under glass down the middle, repeater out. Carries the clock across the city. */
	static CellBuilder wireSegment() {
		CellBuilder b = street("wire_segment");
		b.repeater(3, 1, 0, SOUTH, 1);
		for (int z = 1; z <= 5; z++) {
			b.dust(3, 1, z);
		}
		b.repeater(3, 1, 6, SOUTH, 1);
		return b.kind("logic").truth("passthrough")
				.port("in", "in", NORTH, 3, 1, 0, 1)
				.port("out", "out", SOUTH, 3, 1, 6, 1)
				.fault(3, 1, 3)
				.weight("core", 3).weight("residential", 4).weight("forge", 4).weight("plaza", 3).weight("ram", 3).weight("storage", 3)
				.cost(6, 50, 4);
	}

	/** A crossroads: one input from the north fans out south, east and west under glass; lantern posts on the corners. */
	static CellBuilder junction() {
		CellBuilder b = street("junction");
		b.fill(0, 0, 3, 6, 0, 3, BRICK);
		b.fill(0, 1, 3, 6, 1, 3, "minecraft:air");
		b.fill(0, 2, 3, 6, 2, 3, GLASS);
		b.set(3, 0, 3, "minecraft:chiseled_stone_bricks");
		b.repeater(3, 1, 0, SOUTH, 1);
		b.dust(3, 1, 1).dust(3, 1, 2).dust(3, 1, 3).dust(3, 1, 4).dust(3, 1, 5);
		b.dust(2, 1, 3).dust(1, 1, 3).dust(4, 1, 3).dust(5, 1, 3);
		b.repeater(0, 1, 3, WEST, 1);
		b.repeater(6, 1, 3, EAST, 1);
		b.repeater(3, 1, 6, SOUTH, 1);
		return b.kind("logic").truth("passthrough")
				.port("in", "in", NORTH, 3, 1, 0, 1)
				.port("out_s", "out", SOUTH, 3, 1, 6, 1)
				.port("out_w", "out", WEST, 0, 1, 3, 1)
				.port("out_e", "out", EAST, 6, 1, 3, 1)
				.fault(3, 1, 3)
				.weight("core", 3).weight("residential", 2).weight("forge", 3).weight("plaza", 3).weight("ram", 2).weight("storage", 2)
				.cost(10, 50, 4);
	}

	static final String SLAB = "minecraft:stone_brick_slab";

	/**
	 * A street: paving slabs across the whole cell, a glass-covered groove down the middle for
	 * the wire, lantern posts on the corners. The signal path itself is left to the caller.
	 */
	private static CellBuilder street(String id) {
		CellBuilder b = shell(id, 4);
		b.fill(0, 0, 0, 6, 0, 6, "minecraft:polished_andesite");
		b.fill(3, 0, 0, 3, 0, 6, BRICK);
		b.fill(0, 1, 0, 6, 1, 6, SLAB);
		b.fill(3, 1, 0, 3, 1, 6, "minecraft:air");
		b.fill(3, 2, 0, 3, 2, 6, GLASS);
		for (int[] c : new int[][] {{0, 0}, {6, 0}, {0, 6}, {6, 6}}) {
			b.set(c[0], 1, c[1], "minecraft:oak_fence").set(c[0], 2, c[1], "minecraft:oak_fence");
			b.set(c[0], 3, c[1], BlockSpec.of("minecraft:lantern", "hanging", "false"));
		}
		return b;
	}

	private static CellBuilder shell(String id, int height) {
		CellBuilder b = new CellBuilder(id, 7, height, 7);
		b.fill(0, 0, 0, 6, 0, 6, STONE);
		return b;
	}

	/** A 4-bit street: seven comparators in a row under glass. Strength in equals strength out. */
	static CellBuilder busSegment() {
		CellBuilder b = street("bus_segment");
		for (int z = 0; z <= 6; z++) {
			b.comparator(3, 1, z, SOUTH, false);
		}
		return b.kind("logic").truth("passthrough")
				.port("in", "in", NORTH, 3, 1, 0, 4)
				.port("out", "out", SOUTH, 3, 1, 6, 4)
				.fault(3, 1, 3)
				.weight("core", 2).weight("residential", 1).weight("forge", 6).weight("plaza", 1).weight("ram", 5).weight("storage", 1)
				.cost(14, 60, 4);
	}

	/** Repeater into a block, torch on its far side, dust to the output. Lamp shows the inverted state. */
	static CellBuilder inverter() {
		CellBuilder b = shell("inverter", 5);
		b.repeater(3, 1, 0, SOUTH, 1);
		b.set(3, 1, 1, BRICK);
		b.wallTorch(3, 1, 2, SOUTH);
		b.lamp(3, 2, 2);
		b.dust(3, 1, 3).dust(3, 1, 4).dust(3, 1, 5);
		b.repeater(3, 1, 6, SOUTH, 1);
		b.fill(2, 1, 0, 2, 2, 6, BRICK).fill(4, 1, 0, 4, 2, 6, BRICK);
		b.fill(2, 3, 0, 4, 3, 6, GLASS);
		return b.kind("logic").truth("not")
				.port("in", "in", NORTH, 3, 1, 0, 1)
				.port("out", "out", SOUTH, 3, 1, 6, 1)
				.weight("core", 1).weight("residential", 2).weight("forge", 6).weight("plaza", 1).weight("ram", 1).weight("storage", 0)
				.cost(6, 50, 0);
	}

	/**
	 * The register vault (design doc 5.1: "a vault-like building with lit indicator windows").
	 * Four comparators and four blocks in a ring hold a strength losslessly. C4 (west edge) is in
	 * subtract mode with clk on its side: clk high breaks the loop. The input comparator Cin is in
	 * subtract mode with NOT clk on its side: clk high lets the new value in. Result: a latch that
	 * follows in while clk is high and holds while clk is low.
	 *
	 * <p>Two readouts. Inside, lamps above the ring blocks light whenever the value is non-zero.
	 * Outside, a gauge: a comparator reads the ring into a block, and a dust line runs from it
	 * along the top of the ground-floor wall, losing one per block; lamps set in the wall under
	 * the line light at value 1, above 4, above 7 and above 10, so the vault reads its own value
	 * from the street.
	 *
	 * <p>Timing, in redstone ticks after a clk edge: C4 reopens at 3, Cin closes at 4 (make before
	 * break, so the ring is never undriven while holding). A change on in reaches the ring at 3, so
	 * in must not change in the same redstone tick as a falling clk edge (hold time 1 rt). The
	 * register truth model never changes both in one step; card execution honours the same rule.
	 */
	static CellBuilder registerBlock() {
		CellBuilder b = shell("register_block", 6);
		// the vault: brick walls with a window band, a brick roof and a lantern
		b.walls(0, 1, 0, 6, 2, 6, BRICK);
		b.walls(0, 3, 0, 6, 3, 6, GLASS);
		b.walls(0, 4, 0, 6, 4, 6, BRICK);
		b.fill(0, 5, 0, 6, 5, 6, BRICK);
		b.set(3, 5, 3, "minecraft:chiseled_stone_bricks");
		b.set(3, 4, 3, BlockSpec.of("minecraft:lantern", "hanging", "true"));
		// input path: port comparator, a delay comparator, then the gated input comparator
		b.comparator(3, 1, 0, SOUTH, false);
		b.comparator(3, 1, 1, SOUTH, false);   // D: one tick of delay so in cannot outrun the gate
		b.comparator(3, 1, 2, SOUTH, true);    // Cin, gated by NOT clk
		// the ring (x 3..5, z 3..5)
		b.set(3, 1, 3, BRICK);                 // B1  <- Cin and C4 both write here
		b.comparator(4, 1, 3, EAST, false);    // C1 reads B1 -> B2
		b.set(5, 1, 3, BRICK);                 // B2
		b.comparator(5, 1, 4, SOUTH, false);   // C2 reads B2 -> B3
		b.set(5, 1, 5, BRICK);                 // B3
		b.comparator(4, 1, 5, WEST, false);    // C3 reads B3 -> B4
		b.set(3, 1, 5, BRICK);                 // B4
		b.comparator(3, 1, 4, NORTH, true);    // C4 reads B4 -> B1, gated by clk
		// clk: port repeater, dust south then east, repeater into the side of C4
		b.repeater(0, 1, 3, EAST, 1);
		b.dust(1, 1, 3);
		b.dust(1, 1, 4);
		b.repeater(2, 1, 4, EAST, 1);
		// NOT clk: repeater north into a block, torch on the block, dust into the side of Cin.
		// The repeater (not dust) at (1,1,2) keeps the clk net from touching the NOT clk dust.
		b.repeater(1, 1, 2, NORTH, 1);
		b.set(1, 1, 1, BRICK);
		b.wallTorch(2, 1, 1, EAST);
		b.dust(2, 1, 2);
		// output: the port comparator reads B4 directly
		b.comparator(3, 1, 6, SOUTH, false);
		// indicator windows above the ring blocks
		b.lamp(3, 2, 3).lamp(5, 2, 3).lamp(5, 2, 5).lamp(3, 2, 5);
		// the gauge: a comparator reads B2 north into P; dust from P runs along the wall top
		b.comparator(5, 1, 2, NORTH, false);
		b.set(5, 1, 1, BRICK);                 // P = the held value
		b.dust(5, 2, 1);                       // d0
		b.dust(5, 2, 0).dust(6, 2, 0).dust(6, 2, 1).dust(6, 2, 2).dust(6, 2, 3);   // d1..d5
		b.dust(6, 2, 4).dust(6, 2, 5).dust(6, 2, 6).dust(5, 2, 6).dust(4, 2, 6);   // d6..d10
		b.lamp(5, 1, 0).lamp(6, 1, 2).lamp(6, 1, 5).lamp(4, 1, 6);                 // lit at 1, >4, >7, >10
		return b.kind("logic").truth("register")
				.port("in", "in", NORTH, 3, 1, 0, 4)
				.port("clk", "in", WEST, 0, 1, 3, 1)
				.port("out", "out", SOUTH, 3, 1, 6, 4)
				.weight("core", 1).weight("residential", 0).weight("forge", 0).weight("plaza", 0).weight("ram", 8).weight("storage", 0)
				.cost(32, 110, 0);
	}

	/**
	 * The Clock Tower (design doc 5.1: "a tall pulsing spire; the city's heartbeat"). In the base
	 * chamber a torch-repeater loop: the torch on block B feeds four repeaters at delay 4 that lead
	 * back into B, period 2 x (16 + 1) redstone ticks = 68 game ticks; the output taps the loop
	 * dust. Above B a torch ladder climbs the spire: torch on B, block on the torch, torch on the
	 * block, and so on, each level inverting the one below, with lamps set into the shaft beside
	 * every block. The beat runs up the tower as alternating bands of light, every other band in
	 * step with the clock. Torches toggle every 34 ticks, well under burn-out.
	 */
	static CellBuilder clockTower() {
		CellBuilder b = shell("clock_tower", 11);
		b.set(3, 1, 2, BRICK);                 // B
		b.wallTorch(4, 1, 2, EAST);            // T hangs on B
		b.repeater(4, 1, 3, SOUTH, 4);         // R0 reads T
		b.repeater(4, 1, 4, SOUTH, 4);         // R1
		b.dust(4, 1, 5).dust(3, 1, 5);         // the turn
		b.repeater(3, 1, 4, NORTH, 4);         // R3 reads dust
		b.repeater(3, 1, 3, NORTH, 4);         // R4 -> B
		b.lamp(2, 1, 2).lamp(3, 1, 1);         // beat with B, at eye level in the chamber
		// the chamber: walls with a window band, a brick roof the shaft rises through
		b.walls(0, 1, 0, 6, 1, 6, BRICK);
		b.walls(0, 2, 0, 6, 2, 6, GLASS);
		b.walls(0, 3, 0, 6, 3, 6, BRICK);
		b.fill(0, 4, 0, 6, 4, 6, BRICK);
		b.set(3, 1, 0, "minecraft:air").set(3, 2, 0, "minecraft:air");   // a doorway on the north face
		b.repeater(3, 1, 6, SOUTH, 1);         // out port taps the dust, set in the south wall
		// the spire: a 3x3 shaft around the torch ladder
		for (int y = 2; y <= 9; y++) {
			b.walls(2, y, 1, 4, y, 3, BRICK);
			if (y % 2 == 0) {
				b.torch(3, y, 2);                                    // T1, T2, ... on the block below
			} else {
				b.set(3, y, 2, BRICK);                               // X1, X2, ... powered by the torch below
				b.lamp(2, y, 2).lamp(4, y, 2).lamp(3, y, 1).lamp(3, y, 3);
			}
		}
		b.fill(2, 10, 1, 4, 10, 3, "minecraft:stone_brick_slab");
		b.set(3, 10, 2, "minecraft:glowstone");
		// Zero weights: the city has one clock, placed by the planner next to the core.
		return b.kind("logic").truth("clock")
				.port("out", "out", SOUTH, 3, 1, 6, 1)
				.weight("core", 0).weight("residential", 0).weight("forge", 0).weight("plaza", 0)
				.cost(36, 220, 0);
	}

	/**
	 * The drawbridge (design doc 5.1: "a bridge that rises and falls with the program"). A water
	 * channel crosses the cell between two banks; a plank deck with a slime spine spans it. The
	 * input runs under the north bank into a pier in the channel; the pier powers a sticky piston
	 * under the deck, which lifts the whole deck one block, and drops it when the signal goes.
	 * Lanterns on the bank corners. The channel is walled at both ends so it never floods a
	 * neighbour.
	 */
	static CellBuilder drawbridge() {
		CellBuilder b = shell("drawbridge", 5);
		// banks: north z 0..2, south z 6, two high
		b.fill(0, 1, 0, 6, 2, 2, BRICK);
		b.fill(0, 1, 6, 6, 2, 6, BRICK);
		// the channel z 3..5 at y 1, walled at both ends, water throughout except the pier and the piston
		b.fill(0, 1, 3, 0, 1, 5, BRICK).fill(6, 1, 3, 6, 1, 5, BRICK);
		b.fill(1, 1, 3, 5, 1, 5, BlockSpec.of("minecraft:water", "level", "0"));
		b.set(3, 1, 3, "minecraft:chiseled_stone_bricks");   // the pier, powered by the input
		b.stickyPiston(3, 1, 4, UP);
		// the deck: planks either side of a slime spine, so one piston moves all nine blocks
		b.fill(2, 2, 3, 4, 2, 3, PLANKS).fill(2, 2, 5, 4, 2, 5, PLANKS);
		b.fill(2, 2, 4, 4, 2, 4, "minecraft:slime_block");
		// the input, under the north bank: port repeater, dust, repeater into the pier
		b.repeater(3, 1, 0, SOUTH, 1);
		b.dust(3, 1, 1);
		b.repeater(3, 1, 2, SOUTH, 1);
		// lanterns on the bank corners
		for (int[] c : new int[][] {{0, 0}, {6, 0}, {0, 6}, {6, 6}}) {
			b.set(c[0], 3, c[1], "minecraft:oak_fence");
			b.set(c[0], 4, c[1], BlockSpec.of("minecraft:lantern", "hanging", "false"));
		}
		return b.kind("actuator").truth("actuator")
				.port("in", "in", NORTH, 3, 1, 0, 1)
				.weight("core", 1).weight("residential", 2).weight("forge", 0).weight("plaza", 4).weight("ram", 0).weight("storage", 1)
				.cost(10, 90, 24);
	}

	/**
	 * A warehouse (design doc 5.1: "a warehouse the city draws from"): brick walls, a plank roof,
	 * barrels along the back wall, a doorway on the north face, and a crane on the roof with a
	 * barrel hanging from its chain. Collectors unload here; Builders fetch here.
	 */
	static CellBuilder storageCell() {
		CellBuilder b = shell("storage_cell", 8);
		b.walls(0, 1, 0, 6, 3, 6, BRICK);
		b.fill(0, 4, 0, 6, 4, 6, PLANKS);
		b.set(3, 1, 0, "minecraft:air").set(3, 2, 0, "minecraft:air");
		b.set(2, 2, 0, GLASS).set(4, 2, 0, GLASS).set(0, 2, 3, GLASS).set(6, 2, 3, GLASS);
		for (int x : new int[] {1, 2, 4, 5}) {
			b.facing(x, 1, 5, "minecraft:barrel", UP);
		}
		b.facing(1, 1, 4, "minecraft:barrel", UP).facing(5, 1, 4, "minecraft:barrel", UP);
		b.facing(1, 2, 5, "minecraft:barrel", UP).facing(5, 2, 5, "minecraft:barrel", UP);
		b.set(3, 3, 3, BlockSpec.of("minecraft:lantern", "hanging", "true"));
		// the crane: a log mast on the roof corner, a beam out over the yard, a chain and a barrel
		b.fill(5, 5, 5, 5, 7, 5, BlockSpec.of("minecraft:oak_log", "axis", "y"));
		b.set(4, 7, 5, BlockSpec.of("minecraft:oak_log", "axis", "x")).set(3, 7, 5, BlockSpec.of("minecraft:oak_log", "axis", "x"));
		b.set(2, 7, 5, "minecraft:oak_fence");
		b.set(2, 6, 5, BlockSpec.of("minecraft:chain", "axis", "y"));
		b.facing(2, 5, 5, "minecraft:barrel", UP);
		return b.kind("storage").truth("none")
				.weight("core", 2).weight("residential", 1).weight("forge", 1).weight("plaza", 1).weight("ram", 0).weight("storage", 8)
				.cost(0, 100, 60);
	}
}
