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

	/** A paved square with four lantern posts. No ports: the grammar's fallback that always fits. */
	static CellBuilder decorPlaza() {
		CellBuilder b = shell("decor_plaza", 5);
		for (int[] c : new int[][] {{1, 1}, {5, 1}, {1, 5}, {5, 5}}) {
			b.set(c[0], 1, c[1], "minecraft:oak_fence").set(c[0], 2, c[1], "minecraft:oak_fence");
			b.set(c[0], 3, c[1], BlockSpec.of("minecraft:lantern", "hanging", "false"));
		}
		b.set(3, 0, 3, "minecraft:chiseled_stone_bricks");
		b.fill(2, 0, 2, 4, 0, 2, BRICK).fill(2, 0, 4, 4, 0, 4, BRICK).set(2, 0, 3, BRICK).set(4, 0, 3, BRICK);
		return b.kind("decor").truth("none")
				.weight("core", 1).weight("residential", 2).weight("forge", 0).weight("plaza", 2).weight("ram", 1).weight("storage", 1)
				.cost(0, 30, 8);
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

	/** A 1-bit street: repeater in, dust, repeater out. Carries the clock across the city. */
	static CellBuilder wireSegment() {
		CellBuilder b = shell("wire_segment", 4);
		b.repeater(3, 1, 0, SOUTH, 1);
		for (int z = 1; z <= 5; z++) {
			b.dust(3, 1, z);
		}
		b.repeater(3, 1, 6, SOUTH, 1);
		b.fill(2, 1, 0, 2, 1, 6, BRICK).fill(4, 1, 0, 4, 1, 6, BRICK);
		b.fill(2, 2, 0, 4, 2, 6, GLASS);
		return b.kind("logic").truth("passthrough")
				.port("in", "in", NORTH, 3, 1, 0, 1)
				.port("out", "out", SOUTH, 3, 1, 6, 1)
				.fault(3, 1, 3)
				.weight("core", 3).weight("residential", 4).weight("forge", 4).weight("plaza", 3).weight("ram", 3).weight("storage", 3)
				.cost(6, 50, 0);
	}

	/** A 1-bit fan-out: one input from the north, outputs south, east and west. */
	static CellBuilder junction() {
		CellBuilder b = shell("junction", 4);
		b.repeater(3, 1, 0, SOUTH, 1);
		b.dust(3, 1, 1).dust(3, 1, 2).dust(3, 1, 3).dust(3, 1, 4).dust(3, 1, 5);
		b.dust(2, 1, 3).dust(1, 1, 3).dust(4, 1, 3).dust(5, 1, 3);
		b.repeater(0, 1, 3, WEST, 1);
		b.repeater(6, 1, 3, EAST, 1);
		b.repeater(3, 1, 6, SOUTH, 1);
		for (int[] c : new int[][] {{1, 1}, {5, 1}, {1, 5}, {5, 5}}) {
			b.fill(c[0], 1, c[1], c[0], 3, c[1], BRICK);
		}
		return b.kind("logic").truth("passthrough")
				.port("in", "in", NORTH, 3, 1, 0, 1)
				.port("out_s", "out", SOUTH, 3, 1, 6, 1)
				.port("out_w", "out", WEST, 0, 1, 3, 1)
				.port("out_e", "out", EAST, 6, 1, 3, 1)
				.fault(3, 1, 3)
				.weight("core", 3).weight("residential", 2).weight("forge", 3).weight("plaza", 3).weight("ram", 2).weight("storage", 2)
				.cost(10, 50, 0);
	}

	private static CellBuilder shell(String id, int height) {
		CellBuilder b = new CellBuilder(id, 7, height, 7);
		b.fill(0, 0, 0, 6, 0, 6, STONE);
		return b;
	}

	/** Seven comparators in a row: a 4-bit street. Strength in equals strength out. */
	static CellBuilder busSegment() {
		CellBuilder b = shell("bus_segment", 4);
		for (int z = 0; z <= 6; z++) {
			b.comparator(3, 1, z, SOUTH, false);
		}
		b.fill(2, 1, 0, 2, 1, 6, BRICK).fill(4, 1, 0, 4, 1, 6, BRICK);
		b.fill(2, 2, 0, 4, 2, 6, GLASS);
		return b.kind("logic").truth("passthrough")
				.port("in", "in", NORTH, 3, 1, 0, 4)
				.port("out", "out", SOUTH, 3, 1, 6, 4)
				.fault(3, 1, 3)
				.weight("core", 2).weight("residential", 1).weight("forge", 6).weight("plaza", 1).weight("ram", 5).weight("storage", 1)
				.cost(14, 60, 0);
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
	 * Four comparators and four blocks in a ring hold a strength losslessly. C4 (west edge) is in
	 * subtract mode with clk on its side: clk high breaks the loop. The input comparator Cin is in
	 * subtract mode with NOT clk on its side: clk high lets the new value in. Result: a latch that
	 * follows in while clk is high and holds while clk is low. Lamps above the ring blocks light
	 * whenever the held value is non-zero: the vault's indicator windows.
	 *
	 * <p>Timing, in redstone ticks after a clk edge: C4 reopens at 3, Cin closes at 4 (make before
	 * break, so the ring is never undriven while holding). A change on in reaches the ring at 3, so
	 * in must not change in the same redstone tick as a falling clk edge (hold time 1 rt). The
	 * register truth model never changes both in one step; card execution honours the same rule.
	 */
	static CellBuilder registerBlock() {
		CellBuilder b = shell("register_block", 5);
		// perimeter walls, windows, roof
		b.walls(0, 1, 0, 6, 1, 6, BRICK);
		b.walls(0, 2, 0, 6, 2, 6, GLASS);
		b.walls(0, 3, 0, 6, 3, 6, BRICK);
		b.fill(0, 4, 0, 6, 4, 6, BRICK);
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
		return b.kind("logic").truth("register")
				.port("in", "in", NORTH, 3, 1, 0, 4)
				.port("clk", "in", WEST, 0, 1, 3, 1)
				.port("out", "out", SOUTH, 3, 1, 6, 4)
				.weight("core", 1).weight("residential", 0).weight("forge", 0).weight("plaza", 0).weight("ram", 8).weight("storage", 0)
				.cost(24, 80, 0);
	}

	/**
	 * Torch-repeater loop: torch on block B feeds four repeaters at delay 4 that lead back into B.
	 * Period 2 x (16 + 1) redstone ticks = 68 game ticks. The output taps the loop dust. A lamp
	 * above B beats with it; the spire is the landmark.
	 */
	static CellBuilder clockTower() {
		CellBuilder b = shell("clock_tower", 9);
		b.set(3, 1, 2, BRICK);                 // B
		b.wallTorch(4, 1, 2, EAST);            // T hangs on B
		b.repeater(4, 1, 3, SOUTH, 4);         // R0 reads T
		b.repeater(4, 1, 4, SOUTH, 4);         // R1
		b.dust(4, 1, 5).dust(3, 1, 5);         // the turn
		b.repeater(3, 1, 4, NORTH, 4);         // R3 reads dust
		b.repeater(3, 1, 3, NORTH, 4);         // R4 -> B
		b.repeater(3, 1, 6, SOUTH, 1);         // out port taps the dust
		b.lamp(3, 2, 2);                       // beats with B
		// spire: glass ring around the lamp, brick shaft above, lamp cap
		b.walls(2, 2, 1, 4, 2, 3, GLASS);
		b.walls(2, 3, 1, 4, 7, 3, BRICK);
		b.fill(2, 8, 1, 4, 8, 3, BRICK);
		b.set(3, 8, 2, "minecraft:glowstone");
		// Zero weights: the city has one clock, placed by the planner next to the core.
		return b.kind("logic").truth("clock")
				.port("out", "out", SOUTH, 3, 1, 6, 1)
				.weight("core", 0).weight("residential", 0).weight("forge", 0).weight("plaza", 0)
				.cost(20, 120, 0);
	}

	/**
	 * Input dust climbs onto three blocks; dust on top powers the blocks, the blocks power the
	 * three sticky pistons south of them, and the pistons lift a plank deck one block.
	 */
	static CellBuilder drawbridge() {
		CellBuilder b = shell("drawbridge", 5);
		b.repeater(3, 1, 0, SOUTH, 1);
		b.dust(3, 1, 1).dust(3, 1, 2);
		b.fill(2, 1, 3, 4, 1, 3, BRICK);
		b.dust(2, 2, 3).dust(3, 2, 3).dust(4, 2, 3);
		for (int x = 2; x <= 4; x++) {
			b.stickyPiston(x, 1, 4, UP);
			b.set(x, 2, 4, PLANKS);
		}
		// abutments either side of the deck
		b.fill(1, 1, 4, 1, 2, 4, BRICK).fill(5, 1, 4, 5, 2, 4, BRICK);
		return b.kind("actuator").truth("actuator")
				.port("in", "in", NORTH, 3, 1, 0, 1)
				.weight("core", 1).weight("residential", 2).weight("forge", 0).weight("plaza", 4).weight("ram", 0).weight("storage", 1)
				.cost(8, 60, 12);
	}

	/** A warehouse: brick walls, plank roof, barrels along the back wall, a doorway on the north face. */
	static CellBuilder storageCell() {
		CellBuilder b = shell("storage_cell", 5);
		b.walls(0, 1, 0, 6, 3, 6, BRICK);
		b.fill(0, 4, 0, 6, 4, 6, PLANKS);
		b.set(3, 1, 0, "minecraft:air").set(3, 2, 0, "minecraft:air");
		b.set(2, 2, 0, GLASS).set(4, 2, 0, GLASS);
		for (int x : new int[] {1, 2, 4, 5}) {
			b.facing(x, 1, 5, "minecraft:barrel", UP);
		}
		b.facing(1, 1, 4, "minecraft:barrel", UP).facing(5, 1, 4, "minecraft:barrel", UP);
		b.set(3, 3, 3, BlockSpec.of("minecraft:lantern", "hanging", "true"));
		return b.kind("storage").truth("none")
				.weight("core", 2).weight("residential", 1).weight("forge", 1).weight("plaza", 1).weight("ram", 0).weight("storage", 8)
				.cost(0, 90, 40);
	}
}
