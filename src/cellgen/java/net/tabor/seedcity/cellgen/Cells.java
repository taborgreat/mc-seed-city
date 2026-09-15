package net.tabor.seedcity.cellgen;

import java.util.List;

import static net.tabor.seedcity.cellgen.Dir.DOWN;
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
				aluSub(), aluNot(), aluOr(), vault(), busStreet(), busEnd(), busBranch(), ramVault(1), ramVault(2),
				gardenLane(), gatehouse(), footfallPlaza(), cityWall(), cityWallCorner(), riverBridge(),
				lampTower(), decoderPlaza(), shrine());
	}

	/**
	 * The shrine (design doc 5.1: "a shrine that wakes something when the lights align"): four
	 * pillars around an altar with a bell. The vault's lock (input minus a constant 14) leaves 1
	 * only at exactly 15; that 1 powers the altar block, which rings the bell and lights the
	 * lamps set in the floor around it. A program has to reach 15 to ring it.
	 */
	static CellBuilder shrine() {
		CellBuilder b = shell("shrine", 7);
		b.fill(0, 1, 0, 6, 1, 6, PAVING);
		for (int[] c : new int[][] {{1, 1}, {5, 1}, {1, 5}, {5, 5}}) {
			b.fill(c[0], 2, c[1], c[0], 4, c[1], "minecraft:polished_deepslate");
			b.set(c[0], 5, c[1], BlockSpec.of("minecraft:lantern", "hanging", "false"));
		}
		b.fill(1, 5, 1, 5, 5, 5, "minecraft:polished_deepslate");   // the roof over the altar
		b.set(3, 6, 3, "minecraft:glowstone");
		// the lock: in - 14, so only 15 leaves anything
		b.comparator(3, 1, 0, SOUTH, false);
		b.comparator(3, 1, 1, SOUTH, true);
		b.torch(0, 1, 1);
		b.dust(1, 1, 1).dust(2, 1, 1);
		b.set(3, 1, 2, "minecraft:chiseled_stone_bricks");      // the altar block, powered at 15
		b.set(3, 2, 2, BlockSpec.of("minecraft:bell", "attachment", "floor", "facing", "north", "powered", "false"));
		b.lamp(2, 1, 2).lamp(4, 1, 2).lamp(3, 1, 3);
		return b.door(NORTH).kind("actuator").truth("actuator")
				.port("in", "in", NORTH, 3, 1, 0, 4)
				.weight("core", 1).weight("residential", 2).weight("forge", 0).weight("plaza", 3).weight("ram", 1).weight("storage", 1)
				.cost(16, 140, 0);
	}

	/**
	 * The lamp tower (the first display): fifteen lamps spiral up a mast and the input value
	 * lights that many of them. The port feeds lamp 1 through a comparator; dust on lamp 1
	 * carries the value and climbs a spiral stair whose steps are the lamps, losing one per step,
	 * so lamp k is lit exactly when the value is at least k. Readable from across the city.
	 */
	static CellBuilder lampTower() {
		CellBuilder b = shell("lamp_tower", 18);
		b.fill(0, 1, 0, 6, 1, 6, PAVING);
		b.comparator(3, 1, 0, SOUTH, false);          // in
		b.comparator(3, 1, 1, SOUTH, false);          // into lamp 1, which it powers strongly
		int[][] ring = {{3, 2}, {4, 2}, {4, 3}, {4, 4}, {3, 4}, {2, 4}, {2, 3}, {2, 2}};
		for (int k = 1; k <= 15; k++) {
			int[] p = ring[(k - 1) % 8];
			b.lamp(p[0], k, p[1]);                    // lamp k
			b.dust(p[0], k + 1, p[1]);                // dust k on it: value - (k - 1)
		}
		b.fill(3, 2, 3, 3, 16, 3, BRICK);             // the mast the lamps wind around
		b.set(3, 17, 3, "minecraft:glowstone");
		lanternPosts(b, 2);
		return b.kind("actuator").truth("actuator")
				.port("in", "in", NORTH, 3, 1, 0, 4)
				.weight("core", 2).weight("residential", 2).weight("forge", 1).weight("plaza", 3).weight("ram", 2).weight("storage", 2)
				.cost(60, 120, 0);
	}

	/**
	 * The decoder plaza (design doc 5.1: "a plaza where one of several doors opens per cycle"):
	 * two slots long. The input value runs down a dust snake along the east side; two detectors
	 * on the west, built like the RAM vault's address decoders, light and drive an exit each:
	 * {@code out1} when the value is exactly 1, {@code out8} when it is exactly 8. A program
	 * steering a value through the plaza opens one door at a time.
	 */
	static CellBuilder decoderPlaza() {
		CellBuilder b = new CellBuilder("decoder_plaza", 7, 4, 14);
		b.fill(0, 0, 0, 6, 0, 13, STONE);
		b.fill(0, 1, 0, 6, 1, 13, PAVING);
		b.comparator(3, 1, 0, SOUTH, false);          // in
		b.comparator(3, 1, 1, SOUTH, false);
		b.set(3, 1, 2, BRICK);                        // B = value
		b.comparator(4, 1, 2, EAST, false);
		b.set(5, 1, 2, BRICK);                        // the snake starts under B'
		for (int z = 3; z <= 12; z++) {
			b.dust(5, 1, z);                          // value - (z - 3)
			b.set(5, 2, z, GRATE);
		}
		for (int z : new int[] {3, 10}) {             // detectors: exactly z - 2
			b.repeater(4, 1, z, WEST, 1);
			b.set(3, 1, z, BRICK);                    // T: at least z-2
			b.repeater(4, 1, z + 1, WEST, 1);
			b.set(3, 1, z + 1, BRICK);                // b: at least z-1
			b.dust(2, 1, z + 1);                      // 15 beside b
			b.set(2, 2, z + 1, GRATE);
			b.comparator(2, 1, z, WEST, true);        // T - b
			b.set(1, 1, z, BRICK);                    // the exit's block, lit above
			b.lamp(1, 2, z);
			b.repeater(0, 1, z, WEST, 1);             // the exit
		}
		lanternPosts(b, 2);
		b.set(0, 2, 13, "minecraft:oak_fence").set(0, 3, 13, BlockSpec.of("minecraft:lantern", "hanging", "false"));
		b.set(6, 2, 13, "minecraft:oak_fence").set(6, 3, 13, BlockSpec.of("minecraft:lantern", "hanging", "false"));
		return b.kind("logic").truth("decoder")
				.port("in", "in", NORTH, 3, 1, 0, 4)
				.port("out1", "out", WEST, 0, 1, 3, 1)
				.port("out8", "out", WEST, 0, 1, 10, 1)
				.weight("core", 1).weight("residential", 2).weight("forge", 2).weight("plaza", 4).weight("ram", 2).weight("storage", 1)
				.cost(40, 160, 4);
	}

	/**
	 * The city wall: a two-thick rampart along the cell's south edge with a walkway behind the
	 * parapet and merlons on top. Everything north of it is structure void, so the land inside the
	 * wall stays land. Stands only on the perimeter with its back to the world (sidecar {@code backs}).
	 */
	static CellBuilder cityWall() {
		CellBuilder b = new CellBuilder("city_wall", 7, 6, 7);
		b.fill(0, 0, 0, 6, 5, 4, "minecraft:structure_void");
		b.fill(0, 0, 5, 6, 0, 6, STONE);
		rampart(b, 0, 6, 5, 6, true);
		return b.back(SOUTH).kind("decor").truth("none")
				.weight("core", 1).weight("residential", 1).weight("forge", 1).weight("plaza", 1).weight("ram", 1).weight("storage", 1)
				.cost(0, 160, 0);
	}

	/** The wall's corner: ramparts along the south and east edges meeting in a tower stub. */
	static CellBuilder cityWallCorner() {
		CellBuilder b = new CellBuilder("city_wall_corner", 7, 6, 7);
		b.fill(0, 0, 0, 4, 5, 4, "minecraft:structure_void");
		b.fill(0, 0, 5, 6, 0, 6, STONE).fill(5, 0, 0, 6, 0, 6, STONE);
		rampart(b, 0, 6, 5, 6, true);
		b.fill(6, 1, 0, 6, 4, 6, BRICK).fill(5, 1, 0, 5, 3, 6, BRICK);
		for (int z = 0; z <= 6; z += 2) {
			b.set(6, 5, z, BRICK);
		}
		b.fill(5, 4, 5, 6, 5, 6, BRICK).set(5, 6 - 1, 5, BlockSpec.of("minecraft:lantern", "hanging", "false"));
		return b.back(SOUTH, EAST).kind("decor").truth("none")
				.weight("core", 1).weight("residential", 1).weight("forge", 1).weight("plaza", 1).weight("ram", 1).weight("storage", 1)
				.cost(0, 260, 0);
	}

	/** A rampart along z=z1 (the outer face) with a walkway ledge at z=z0, x from x0 to x1, merlons on top. */
	private static void rampart(CellBuilder b, int x0, int x1, int z0, int z1, boolean merlons) {
		b.fill(x0, 1, z1, x1, 4, z1, BRICK);
		b.fill(x0, 1, z0, x1, 3, z0, BRICK);
		if (merlons) {
			for (int x = x0; x <= x1; x += 2) {
				b.set(x, 5, z1, BRICK);
			}
		}
	}

	/**
	 * The river bridge: the drawbridge's deck and banks, but the channel under the deck is structure
	 * void, so it stands over real water (the survey admits it only there, sidecar {@code wet}) and
	 * the river keeps flowing under it. The piers stand in the water; the site work founds them
	 * down to the bed. Rises with its input like the drawbridge.
	 */
	static CellBuilder riverBridge() {
		CellBuilder b = shell("river_bridge", 5);
		b.fill(0, 1, 0, 6, 2, 2, BRICK);                        // north bank
		b.fill(0, 1, 6, 6, 2, 6, BRICK);                        // south bank
		b.fill(0, 1, 3, 0, 1, 5, BRICK).fill(6, 1, 3, 6, 1, 5, BRICK);   // side piers
		b.fill(1, 0, 3, 5, 1, 5, "minecraft:structure_void");   // the river, and its bed, are left alone
		b.set(3, 1, 3, "minecraft:chiseled_stone_bricks");      // the pier the input powers
		b.stickyPiston(3, 1, 4, UP);
		b.fill(2, 2, 3, 4, 2, 3, PLANKS).fill(2, 2, 5, 4, 2, 5, PLANKS);
		b.fill(2, 2, 4, 4, 2, 4, "minecraft:slime_block");
		b.repeater(3, 1, 0, SOUTH, 1);
		b.dust(3, 1, 1);
		b.repeater(3, 1, 2, SOUTH, 1);
		b.fill(3, 2, 0, 3, 2, 2, GRATE);
		for (int[] c : new int[][] {{0, 0}, {6, 0}, {0, 6}, {6, 6}}) {
			b.set(c[0], 3, c[1], "minecraft:oak_fence");
			b.set(c[0], 4, c[1], BlockSpec.of("minecraft:lantern", "hanging", "false"));
		}
		return b.street().door(NORTH, SOUTH).wet().kind("actuator").truth("actuator")
				.port("in", "in", NORTH, 3, 1, 0, 1)
				.weight("core", 2).weight("residential", 2).weight("forge", 2).weight("plaza", 2).weight("ram", 2).weight("storage", 2)
				.cost(10, 120, 24);
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
		return b.door(NORTH).kind("actuator").truth("actuator").loot("minecraft:chests/simple_dungeon")
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
		// local y=1 is ground level everywhere in the city: the chamber floor is paving there, the
		// Seed stands on it at (3,2,3), and the bus gate keeps its lane heights (select y=1, data y=3)
		CellBuilder b = shell("core", 6);
		b.fill(0, 1, 0, 6, 1, 6, PAVING);
		b.set(3, 2, 3, "minecraft:structure_void");
		for (int[] c : new int[][] {{1, 1}, {5, 1}, {1, 5}, {5, 5}}) {
			b.fill(c[0], 2, c[1], c[0], 4, c[1], "minecraft:chiseled_stone_bricks");
		}
		b.fill(0, 5, 0, 6, 5, 6, BRICK);
		b.set(3, 5, 3, GLASS);
		b.set(3, 4, 3, BlockSpec.of("minecraft:lantern", "hanging", "true"));
		b.set(2, 1, 3, "minecraft:chiseled_stone_bricks").set(4, 1, 3, "minecraft:chiseled_stone_bricks")
				.set(3, 1, 2, "minecraft:chiseled_stone_bricks").set(3, 1, 4, "minecraft:chiseled_stone_bricks");
		// the Card Reader: a slot on the south side of the chamber, facing the Seed
		b.set(3, 2, 5, "seedcity:card_reader");
		// the bus gate in the east wall (docs/city-as-computer.md): the Core drives the select lane
		// and the data lane through its own terminal blocks, and reads the return lane back
		b.set(5, 1, 3, BlockSpec.of("seedcity:terminal", "facing", "east", "power", "0"));
		b.comparator(6, 1, 3, EAST, false);                         // sel_out
		b.fill(5, 2, 2, 6, 2, 4, "minecraft:chiseled_stone_bricks");  // a ledge for the upper lanes
		b.set(5, 3, 4, BlockSpec.of("seedcity:terminal", "facing", "east", "power", "0"));
		b.comparator(6, 3, 4, EAST, false);                         // data_out
		b.comparator(6, 3, 2, WEST, false);                         // ret_in
		b.set(5, 3, 2, "minecraft:chiseled_stone_bricks");          // what came back, for the Core to read
		return b.kind("core").truth("none")
				.port("sel_out", "out", EAST, 6, 1, 3, 4)
				.port("data_out", "out", EAST, 6, 3, 4, 4)
				.port("ret_in", "in", EAST, 6, 3, 2, 4)
				.weight("core", 0).weight("residential", 0).weight("forge", 0).weight("plaza", 0)
				.cost(0, 40, 0);
	}

	/**
	 * A bus street (docs/city-as-computer.md): three lanes of comparators. The select lane runs
	 * under the pavement; the data lane out and the return lane back run on top under glass, so
	 * a value can be watched travelling down the street. Every lane is lossless.
	 */
	static CellBuilder busStreet() {
		CellBuilder b = shell("bus_street", 5);
		b.fill(0, 1, 0, 6, 1, 6, BRICK);
		b.fill(0, 2, 0, 6, 2, 6, "minecraft:polished_andesite");
		for (int z = 0; z <= 6; z++) {
			b.comparator(3, 1, z, SOUTH, false);     // select, southbound, under the street
			b.comparator(2, 3, z, SOUTH, false);     // data, southbound
			b.comparator(4, 3, z, NORTH, false);     // return, northbound
		}
		b.fill(1, 3, 0, 1, 3, 6, BRICK).fill(5, 3, 0, 5, 3, 6, BRICK);   // kerbs
		b.fill(2, 4, 0, 2, 4, 6, GLASS).fill(4, 4, 0, 4, 4, 6, GLASS);
		for (int[] c : new int[][] {{0, 0}, {6, 0}, {0, 6}, {6, 6}}) {
			b.set(c[0], 3, c[1], "minecraft:oak_fence");
			b.set(c[0], 4, c[1], BlockSpec.of("minecraft:lantern", "hanging", "false"));
		}
		return b.street().kind("logic").truth("lanes")
				.port("sel_in", "in", NORTH, 3, 1, 0, 4)
				.port("sel_out", "out", SOUTH, 3, 1, 6, 4)
				.port("data_in", "in", NORTH, 2, 3, 0, 4)
				.port("data_out", "out", SOUTH, 2, 3, 6, 4)
				.port("ret_in", "in", SOUTH, 4, 3, 6, 4)
				.port("ret_out", "out", NORTH, 4, 3, 0, 4)
				.fault(3, 1, 3)
				// a street crosses any district on its way to RAM, but prefers the core and the RAM sector
				.weight("core", 4).weight("residential", 1).weight("forge", 3).weight("plaza", 1).weight("ram", 6).weight("storage", 1)
				.cost(40, 80, 8).setpiece(false);
	}

	static final String MEZZ = "minecraft:polished_andesite";

	/**
	 * A RAM vault on the bus (docs/city-as-computer.md): 7 wide, 14 deep, two floors and a roof.
	 * The bus ends here. Ground floor: the address decoder. The select value arrives as a
	 * strongly powered block, a dust snake knocks one off per block, a repeater on the k-th dust
	 * says "at least k", and two subtract comparators pick out exactly the vault's read address
	 * (R) and exactly its write address (W). First floor: the data lane feeds a four-comparator
	 * ring (the same latch as the register vault) through a gate that opens while W is high; the
	 * ring value is read back onto the return lane through a gate that opens while R is high.
	 * The gate signals climb through torches in the mezzanine, each torch inverting the block it
	 * stands on, and NOT R crosses the first floor as a dust run at roof level.
	 *
	 * <p>Vault 1 reads at select 1 and writes at 6; vault 2 reads at 2 and writes at 8. The two
	 * differ only in where their decoder sits along the snake.
	 */
	static CellBuilder ramVault(int k) {
		int zT = k + 1;                                   // row of the read unit: reads at select zT-1
		int zW = zT + 5 + (zT % 2 == 0 ? 0 : 1);          // row of the write unit, odd, so the relay lands on rows 7 and 5
		int readAt = zT - 1;
		int writeAt = zW - 1;
		CellBuilder b = new CellBuilder("ram_vault_" + k, 7, 6, 14);
		b.fill(0, 0, 0, 6, 0, 13, STONE);
		b.walls(0, 1, 0, 6, 1, 13, BRICK);
		// the long sides are windows: the decoder snake and the relays run right behind them, and a
		// solid wall there would carry a neighbour's stray output straight into the circuit
		b.fill(0, 1, 1, 0, 1, 12, GLASS).fill(6, 1, 1, 6, 1, 12, GLASS);
		b.fill(0, 2, 0, 6, 2, 13, MEZZ);
		b.walls(0, 3, 0, 6, 3, 13, GLASS);
		b.walls(0, 4, 0, 6, 4, 13, BRICK);
		b.fill(0, 5, 0, 6, 5, 13, BRICK);
		b.set(3, 5, 6, "minecraft:glowstone").set(3, 5, 10, "minecraft:glowstone");
		// ---- ground floor: select in, the snake down the east side, the two decoders
		b.comparator(3, 1, 0, SOUTH, false);              // sel_in
		b.set(3, 1, 1, BRICK);                            // Bs = sel
		b.comparator(4, 1, 1, EAST, false);
		b.set(5, 1, 1, BRICK);                            // Bs' = sel; the snake starts under it
		for (int z = 2; z <= 12; z++) {
			b.dust(5, 1, z);                              // sel - (z-2)
		}
		// read unit: R = [sel >= readAt] - [sel >= readAt+1]
		b.repeater(4, 1, zT, WEST, 1);
		b.set(3, 1, zT, BRICK);                           // T
		b.repeater(4, 1, zT + 1, WEST, 1);
		b.set(3, 1, zT + 1, BRICK);                       // b
		b.dust(2, 1, zT + 1);                             // 15 beside b, into R's side
		b.comparator(2, 1, zT, WEST, true);               // R
		b.set(1, 1, zT, BRICK);                           // R block, under a torch
		// write unit: W = [sel >= writeAt] - [sel >= writeAt+1]
		b.repeater(4, 1, zW, WEST, 1);
		b.set(3, 1, zW, BRICK);
		b.repeater(4, 1, zW + 1, WEST, 1);
		b.set(3, 1, zW + 1, BRICK);
		b.dust(2, 1, zW + 1);
		b.comparator(2, 1, zW, WEST, true);               // W
		b.set(1, 1, zW, BRICK);                           // W' block
		for (int z = zW - 1; z >= 6; z -= 2) {            // W' relays north to (1,1,7) and (1,1,5)
			b.repeater(1, 1, z, NORTH, 1);
			b.set(1, 1, z - 1, BRICK);
		}
		// ---- mezzanine torches: each powers the block above with the inverse of the block below
		b.torch(1, 2, zT);                                // NOT R  -> (1,3,zT)
		b.torch(1, 2, 5);                                 // NOT W' -> (1,3,5)
		b.wallTorch(0, 1, 7, WEST);                       // hangs on (1,1,7) = W': lit = NOT W'
		b.set(0, 2, 7, MEZZ);                             // powered by that torch = NOT W'
		b.torch(0, 3, 7);                                 // lit = W'
		// ---- first floor: data in, the ring, the return
		b.comparator(2, 3, 0, SOUTH, false);              // data_in
		b.set(2, 3, 1, BRICK);
		b.comparator(1, 3, 1, WEST, false);
		b.set(0, 3, 1, BRICK);
		b.comparator(0, 3, 2, SOUTH, false).comparator(0, 3, 3, SOUTH, false);
		b.set(0, 3, 4, BRICK);
		b.comparator(1, 3, 4, EAST, false).comparator(2, 3, 4, EAST, false).comparator(3, 3, 4, EAST, false);
		b.set(4, 3, 4, BRICK);                            // Bd = data
		b.comparator(4, 3, 5, SOUTH, true);               // Cin, open while NOT W' is low
		b.set(1, 3, 5, BRICK);                            // NOT W'
		b.repeater(2, 3, 5, EAST, 1).repeater(3, 3, 5, EAST, 1);   // into Cin's side
		b.set(4, 3, 6, BRICK);                            // B1
		b.comparator(5, 3, 6, EAST, false);               // C1
		b.set(6, 3, 6, BRICK);                            // B2
		b.comparator(6, 3, 7, SOUTH, false);              // C2
		b.set(6, 3, 8, BRICK);                            // B3
		b.comparator(5, 3, 8, WEST, false);               // C3
		b.set(4, 3, 8, BRICK);                            // B4
		b.comparator(4, 3, 7, NORTH, true);               // C4, broken while W' is high
		b.repeater(1, 3, 7, EAST, 1);                     // reads the W' torch behind it
		b.set(2, 3, 7, BRICK);                            // W'
		b.repeater(3, 3, 7, EAST, 1);                     // into C4's side
		// the value: B2 read north, gated by NOT R, west to the return lane
		b.comparator(6, 3, 5, NORTH, false);
		b.set(6, 3, 4, BRICK);
		b.comparator(6, 3, 3, NORTH, true);               // gate: value - NOT R
		b.set(4, 3, 3, BRICK);                            // NOT R lands here from the roof-level dust
		b.repeater(5, 3, 3, EAST, 1);                     // into the gate's side
		b.set(6, 3, 2, BRICK);
		b.comparator(5, 3, 2, WEST, false);
		b.set(4, 3, 2, BRICK);
		b.comparator(4, 3, 1, NORTH, false);
		b.comparator(4, 3, 0, NORTH, false);              // ret_out
		// ---- NOT R: block over the torch, then dust along the roof level to (4,3,3)
		b.set(1, 3, zT, BRICK).set(2, 3, zT, BRICK).set(3, 3, zT, BRICK).set(3, 3, 3, BRICK);
		b.dust(1, 4, zT).dust(2, 4, zT).dust(3, 4, zT);
		if (zT != 3) {
			b.dust(3, 4, 3);
		}
		b.dust(4, 4, 3);
		// core weight: a wanted vault may stand right off the Core's east gate; otherwise vaults belong to RAM
		return b.kind("logic").truth("ram_" + readAt + "_" + writeAt)
				.port("sel_in", "in", NORTH, 3, 1, 0, 4)
				.port("data_in", "in", NORTH, 2, 3, 0, 4)
				.port("ret_out", "out", NORTH, 4, 3, 0, 4)
				.weight("core", 6).weight("residential", 0).weight("forge", 0).weight("plaza", 0).weight("ram", 8).weight("storage", 0)
				.cost(80, 260, 0);
	}

	/**
	 * A bus branch: the three lanes continue south and also turn east. Select and data fan out
	 * (data crosses over the return lane at roof level, on comparators standing on a return-lane
	 * block); the two returns merge into one block, which takes the stronger, so only the
	 * addressed vault is ever heard.
	 */
	static CellBuilder busBranch() {
		CellBuilder b = shell("bus_branch", 6);
		b.fill(0, 1, 0, 6, 1, 6, BRICK);
		b.fill(0, 2, 0, 6, 2, 6, MEZZ);
		b.fill(0, 3, 0, 6, 3, 6, BRICK);
		b.fill(0, 5, 0, 6, 5, 6, GLASS);
		// select: south lane with a block after the port, and an east tap off that block
		b.comparator(3, 1, 0, SOUTH, false);
		b.set(3, 1, 1, BRICK);
		for (int z = 2; z <= 6; z++) {
			b.comparator(3, 1, z, SOUTH, false);
		}
		b.comparator(4, 1, 1, EAST, false);
		b.set(5, 1, 1, BRICK);
		b.comparator(5, 1, 2, SOUTH, false);
		b.set(5, 1, 3, BRICK);
		b.comparator(6, 1, 3, EAST, false);               // sel_out_e
		// data: south lane, alternating comparators and blocks
		b.comparator(2, 3, 0, SOUTH, false);
		b.set(2, 3, 1, BRICK);
		b.comparator(2, 3, 2, SOUTH, false);
		b.set(2, 3, 3, BRICK);
		b.comparator(2, 3, 4, SOUTH, false);
		b.set(2, 3, 5, BRICK);
		b.comparator(2, 3, 6, SOUTH, false);              // data_out
		// data east: up from the block at (2,3,5), over the return lane on comparators, down onto (5,3,4)
		b.dust(2, 4, 5);
		b.comparator(3, 4, 5, EAST, false).comparator(4, 4, 5, EAST, false);
		b.set(5, 4, 5, BRICK);
		b.dust(5, 4, 4);
		b.comparator(6, 3, 4, EAST, false);               // data_out_e reads (5,3,4), powered by the dust on it
		// return: north lane with a block under the crossing and the merge block near the port
		b.comparator(4, 3, 6, NORTH, false);              // ret_in
		b.set(4, 3, 5, BRICK);
		b.comparator(4, 3, 4, NORTH, false).comparator(4, 3, 3, NORTH, false);
		b.set(4, 3, 2, BRICK);                            // merge
		b.comparator(4, 3, 1, NORTH, false);
		b.comparator(4, 3, 0, NORTH, false);              // ret_out
		b.comparator(6, 3, 2, WEST, false);               // ret_in_e
		b.comparator(5, 3, 2, WEST, false);               // into the merge block
		b.set(3, 4, 6, "minecraft:air").set(3, 4, 4, "minecraft:air");
		return b.street().kind("logic").truth("branch")
				.port("sel_in", "in", NORTH, 3, 1, 0, 4)
				.port("sel_out", "out", SOUTH, 3, 1, 6, 4)
				.port("sel_out_e", "out", EAST, 6, 1, 3, 4)
				.port("data_in", "in", NORTH, 2, 3, 0, 4)
				.port("data_out", "out", SOUTH, 2, 3, 6, 4)
				.port("data_out_e", "out", EAST, 6, 3, 4, 4)
				.port("ret_in", "in", SOUTH, 4, 3, 6, 4)
				.port("ret_in_e", "in", EAST, 6, 3, 2, 4)
				.port("ret_out", "out", NORTH, 4, 3, 0, 4)
				.weight("core", 3).weight("residential", 1).weight("forge", 2).weight("plaza", 1).weight("ram", 5).weight("storage", 1)
				.cost(60, 120, 8).setpiece(false);
	}

	/** The end of a bus: the data lane turns around into the return lane, the select lane stops. A turning circle. */
	static CellBuilder busEnd() {
		CellBuilder b = shell("bus_end", 5);
		b.fill(0, 1, 0, 6, 1, 6, BRICK);
		b.fill(0, 2, 0, 6, 2, 6, "minecraft:polished_andesite");
		b.comparator(3, 1, 0, SOUTH, false);
		b.set(3, 1, 1, BRICK);
		b.comparator(2, 3, 0, SOUTH, false);
		b.set(2, 3, 1, BRICK);
		b.comparator(3, 3, 1, EAST, false);
		b.set(4, 3, 1, BRICK);
		b.comparator(4, 3, 0, NORTH, false);
		b.fill(1, 3, 0, 1, 3, 1, BRICK).fill(5, 3, 0, 5, 3, 1, BRICK);
		b.fill(2, 4, 0, 4, 4, 1, GLASS);
		b.fill(2, 3, 3, 4, 3, 3, BRICK).set(3, 3, 4, BRICK).set(3, 4, 3, BlockSpec.of("minecraft:lantern", "hanging", "false"));
		return b.street().kind("logic").truth("loopback")
				.port("sel_in", "in", NORTH, 3, 1, 0, 4)
				.port("data_in", "in", NORTH, 2, 3, 0, 4)
				.port("ret_out", "out", NORTH, 4, 3, 0, 4)
				.weight("core", 2).weight("residential", 1).weight("forge", 2).weight("plaza", 1).weight("ram", 4).weight("storage", 1)
				.cost(12, 60, 4).setpiece(false);
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
		return b.street().kind("decor").truth("none")
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
		return b.street().kind("sensor").truth("sensor")
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
		return b.street().kind("logic").truth("passthrough")
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
		b.fill(0, 2, 3, 6, 2, 3, GRATE);
		b.set(3, 0, 3, "minecraft:chiseled_stone_bricks");
		b.repeater(3, 1, 0, SOUTH, 1);
		b.dust(3, 1, 1).dust(3, 1, 2).dust(3, 1, 3).dust(3, 1, 4).dust(3, 1, 5);
		b.dust(2, 1, 3).dust(1, 1, 3).dust(4, 1, 3).dust(5, 1, 3);
		b.repeater(0, 1, 3, WEST, 1);
		b.repeater(6, 1, 3, EAST, 1);
		b.repeater(3, 1, 6, SOUTH, 1);
		return b.street().kind("logic").truth("passthrough")
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
	/** An iron grate flush with the paving: you walk over the wire and see it below. Pistons and torches never touch it. */
	static final BlockSpec GRATE = BlockSpec.of("minecraft:iron_trapdoor", "half", "bottom", "facing", "north", "open", "false");
	static final String PAVING = "minecraft:stone_bricks";

	private static CellBuilder street(String id) {
		CellBuilder b = shell(id, 4);
		b.fill(0, 0, 0, 6, 0, 6, "minecraft:polished_andesite");
		b.fill(3, 0, 0, 3, 0, 6, BRICK);
		b.fill(0, 1, 0, 6, 1, 6, PAVING);            // a street you walk on
		b.fill(3, 1, 0, 3, 1, 6, "minecraft:air");   // the groove for the wire
		b.fill(3, 2, 0, 3, 2, 6, GRATE);             // grated over, flush with the paving
		lanternPosts(b, 2);
		return b;
	}

	/** Lantern posts on the four corners, standing on the paving at {@code y}. */
	private static void lanternPosts(CellBuilder b, int y) {
		for (int[] c : new int[][] {{0, 0}, {6, 0}, {0, 6}, {6, 6}}) {
			b.set(c[0], y, c[1], "minecraft:oak_fence");
			b.set(c[0], y + 1, c[1], BlockSpec.of("minecraft:lantern", "hanging", "false"));
		}
	}

	/**
	 * A garden lane: the street's middle three columns only; the four columns either side are
	 * structure void, so the land stays what it was (grass, flowers, a tree's edge) and the city
	 * breathes between its buildings. The wire is unchanged: a walkable groove under a grate.
	 */
	static CellBuilder gardenLane() {
		CellBuilder b = new CellBuilder("garden_lane", 7, 4, 7);
		b.fill(0, 0, 0, 1, 3, 6, "minecraft:structure_void").fill(5, 0, 0, 6, 3, 6, "minecraft:structure_void");
		b.fill(2, 0, 0, 4, 0, 6, STONE);
		b.fill(3, 0, 0, 3, 0, 6, BRICK);
		b.fill(2, 1, 0, 4, 1, 6, PAVING);
		b.fill(3, 1, 0, 3, 1, 6, "minecraft:air");
		b.fill(3, 2, 0, 3, 2, 6, GRATE);
		b.set(2, 2, 0, "minecraft:oak_fence").set(2, 3, 0, BlockSpec.of("minecraft:lantern", "hanging", "false"));
		b.set(4, 2, 6, "minecraft:oak_fence").set(4, 3, 6, BlockSpec.of("minecraft:lantern", "hanging", "false"));
		b.repeater(3, 1, 0, SOUTH, 1);
		for (int z = 1; z <= 5; z++) {
			b.dust(3, 1, z);
		}
		b.repeater(3, 1, 6, SOUTH, 1);
		return b.street().kind("logic").truth("passthrough")
				.port("in", "in", NORTH, 3, 1, 0, 1)
				.port("out", "out", SOUTH, 3, 1, 6, 1)
				.fault(3, 1, 3)
				.weight("core", 1).weight("residential", 5).weight("forge", 1).weight("plaza", 4).weight("ram", 1).weight("storage", 2)
				.cost(6, 30, 4);
	}

	/**
	 * The gatehouse (defence you can program): a three-wide arch over the street with a portcullis
	 * of iron bars in its south mouth. The control port on the west face runs up a dust stair in
	 * the west wall to the roof, where it powers three sticky pistons that drop the bars into the
	 * arch. Any value above 0 shuts the gate; 0 opens it.
	 */
	static CellBuilder gatehouse() {
		CellBuilder b = shell("gatehouse", 9);
		b.fill(2, 1, 0, 4, 1, 6, PAVING);                       // the walkway
		b.fill(0, 1, 0, 1, 6, 6, BRICK).fill(5, 1, 0, 6, 6, 6, BRICK);   // the two towers
		b.fill(2, 6, 0, 4, 6, 6, BRICK);                        // the roof over the arch
		b.fill(2, 5, 0, 4, 5, 5, BRICK);                        // the arch's vault
		// the portcullis: sticky pistons in the south mouth push iron bars down into the arch
		for (int x = 2; x <= 4; x++) {
			b.stickyPiston(x, 5, 6, DOWN);
			b.set(x, 4, 6, "minecraft:iron_bars");
		}
		// the control: a repeater in the west face, then a dust stair inside the west tower's window wall
		b.repeater(0, 1, 0, EAST, 1);
		b.fill(1, 1, 0, 1, 6, 6, GLASS);                        // the window wall the stair climbs
		int[][] stair = {{1, 0}, {2, 1}, {3, 2}, {4, 3}, {5, 4}, {6, 5}, {7, 6}};   // {y, z}: dust at (1,y,z) on a brick at (1,y-1,z)
		for (int[] s : stair) {
			if (s[0] > 1) {
				b.set(1, s[0] - 1, s[1], BRICK);
			}
			b.dust(1, s[0], s[1]);
		}
		b.dust(2, 7, 6).dust(3, 7, 6).dust(4, 7, 6);            // along the roof to the pistons' blocks
		lanternPosts(b, 7);
		return b.street().door(NORTH, SOUTH).kind("actuator").truth("actuator")
				.port("gate", "in", WEST, 0, 1, 0, 4)
				.weight("core", 3).weight("residential", 1).weight("forge", 0).weight("plaza", 2).weight("ram", 0).weight("storage", 1)
				.cost(24, 200, 12);
	}

	/**
	 * The footfall plaza (the sensor that lets a program answer to a player): three gold plates
	 * across the path; the blocks under them carry the plates' weight to a dust row and out
	 * through a comparator. One player reads 1; a crowd reads more.
	 */
	static CellBuilder footfallPlaza() {
		CellBuilder b = shell("footfall_plaza", 4);
		b.fill(0, 1, 0, 6, 1, 6, PAVING);
		for (int x = 2; x <= 4; x++) {
			b.set(x, 2, 4, "minecraft:light_weighted_pressure_plate");
			b.set(x, 1, 5, "minecraft:air");
			b.dust(x, 1, 5);
			b.set(x, 2, 5, GRATE);
		}
		b.set(3, 1, 6, "minecraft:air");
		b.comparator(3, 1, 6, SOUTH, false);
		b.set(3, 2, 6, GRATE);
		b.set(3, 2, 2, "minecraft:stone_brick_wall").set(3, 3, 2, BlockSpec.of("minecraft:lantern", "hanging", "false"));
		lanternPosts(b, 2);
		return b.street().door(NORTH, SOUTH).kind("sensor").truth("sensor")
				.port("out", "out", SOUTH, 3, 1, 6, 4)
				.weight("core", 1).weight("residential", 2).weight("forge", 0).weight("plaza", 4).weight("ram", 0).weight("storage", 1)
				.cost(8, 60, 2);
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
		return b.street().kind("logic").truth("passthrough")
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
		b.dust(3, 1, 3).dust(3, 1, 4).dust(3, 1, 5);
		b.repeater(3, 1, 6, SOUTH, 1);
		// the same profile as a street: paving beside the wire, a grate over the groove, so you walk
		// straight through; the lamp sits in the grate line over the torch, paving over the torch's block
		b.fill(0, 1, 0, 2, 1, 6, PAVING).fill(4, 1, 0, 6, 1, 6, PAVING);
		b.fill(3, 2, 0, 3, 2, 6, GRATE);
		b.set(3, 2, 1, PAVING);
		b.lamp(3, 2, 2);
		lanternPosts(b, 2);
		return b.street().kind("logic").truth("not")
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
				// zero weights: programs live in RAM vaults on the bus now; this one is for blueprints and hand-built circuits
				.weight("core", 0).weight("residential", 0).weight("forge", 0).weight("plaza", 0).weight("ram", 0).weight("storage", 0)
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
		CellBuilder b = shell("clock_tower", 21);
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
		// the spire: a 3x3 shaft around the torch ladder, tall enough to find from anywhere in the city
		for (int y = 2; y <= 19; y++) {
			b.walls(2, y, 1, 4, y, 3, BRICK);
			if (y % 2 == 0) {
				b.torch(3, y, 2);                                    // T1, T2, ... on the block below
			} else {
				b.set(3, y, 2, BRICK);                               // X1, X2, ... powered by the torch below
				b.lamp(2, y, 2).lamp(4, y, 2).lamp(3, y, 1).lamp(3, y, 3);
			}
		}
		b.fill(2, 20, 1, 4, 20, 3, "minecraft:stone_brick_slab");
		b.set(3, 20, 2, "minecraft:glowstone");
		// Zero weights: the city has one clock, placed by the planner next to the core.
		return b.door(NORTH).kind("logic").truth("clock")
				.port("out", "out", SOUTH, 3, 1, 6, 1)
				.weight("core", 0).weight("residential", 0).weight("forge", 0).weight("plaza", 0)
				.cost(60, 400, 0);
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
		// the input, in a groove through the north bank under glass, so the street's channel runs on to the water:
		// port repeater, dust, repeater into the pier
		b.repeater(3, 1, 0, SOUTH, 1);
		b.dust(3, 1, 1);
		b.repeater(3, 1, 2, SOUTH, 1);
		b.fill(3, 2, 0, 3, 2, 2, GRATE);
		// lanterns on the bank corners
		for (int[] c : new int[][] {{0, 0}, {6, 0}, {0, 6}, {6, 6}}) {
			b.set(c[0], 3, c[1], "minecraft:oak_fence");
			b.set(c[0], 4, c[1], BlockSpec.of("minecraft:lantern", "hanging", "false"));
		}
		// a pond bridge for plazas only: bridges over real water are the river bridge's job
		return b.street().door(NORTH, SOUTH).kind("actuator").truth("actuator")
				.port("in", "in", NORTH, 3, 1, 0, 1)
				.weight("core", 0).weight("residential", 0).weight("forge", 0).weight("plaza", 2).weight("ram", 0).weight("storage", 0)
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
		return b.door(NORTH).kind("storage").truth("none")
				.weight("core", 2).weight("residential", 1).weight("forge", 1).weight("plaza", 1).weight("ram", 0).weight("storage", 8)
				.cost(0, 100, 60);
	}
}
