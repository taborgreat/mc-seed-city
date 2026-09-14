package net.tabor.seedcity.cellgen;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Places blocks into a fixed footprint and emits a structure NBT plus its sidecar JSON.
 *
 * <p>Conventions (see cells/README.md): repeaters and comparators are declared by the direction
 * they OUTPUT to; the generator sets the vanilla {@code facing} property (which points at the
 * input). Wall torches are declared by the direction they point, away from their supporting block.
 */
public final class CellBuilder {
	/** Minecraft 26.2 world data version, from version.json in the client jar. */
	public static final int DATA_VERSION = 4903;

	public record Port(String name, String dir, Dir face, int x, int y, int z, int bits) {
	}

	private final String id;
	private final int sx, sy, sz;
	private final BlockSpec[][][] grid;
	private final List<Port> ports = new ArrayList<>();
	private String kind = "logic";
	private String truth = "none";
	private final Map<String, Integer> weights = new LinkedHashMap<>();
	private final Map<String, Integer> cost = new LinkedHashMap<>();
	private boolean setpiece = true;
	private int[] fault;
	private String loot;

	public CellBuilder(String id, int sx, int sy, int sz) {
		this.id = id;
		this.sx = sx;
		this.sy = sy;
		this.sz = sz;
		this.grid = new BlockSpec[sx][sy][sz];
	}

	public String id() {
		return id;
	}

	// ---- metadata ---------------------------------------------------------------------------

	public CellBuilder kind(String k) {
		this.kind = k;
		return this;
	}

	public CellBuilder truth(String t) {
		this.truth = t;
		return this;
	}

	public CellBuilder weight(String district, int w) {
		weights.put(district, w);
		return this;
	}

	public CellBuilder cost(int redstone, int stone, int wood) {
		cost.put("redstone", redstone);
		cost.put("stone", stone);
		cost.put("wood", wood);
		return this;
	}

	public CellBuilder setpiece(boolean s) {
		this.setpiece = s;
		return this;
	}

	/** Loot table every chest in this cell is filled from when a Builder places it. */
	public CellBuilder loot(String lootTable) {
		this.loot = lootTable;
		return this;
	}

	/** The one block the planner may leave out to plant this cell as a Fault Cell. */
	public CellBuilder fault(int x, int y, int z) {
		check(x, y, z);
		this.fault = new int[] {x, y, z};
		return this;
	}

	/** Declares a port. {@code face} is the outward normal; pos must lie on that face. */
	public CellBuilder port(String name, String dir, Dir face, int x, int y, int z, int bits) {
		check(x, y, z);
		boolean onFace = switch (face) {
			case NORTH -> z == 0;
			case SOUTH -> z == sz - 1;
			case WEST -> x == 0;
			case EAST -> x == sx - 1;
			case UP -> y == sy - 1;
			case DOWN -> y == 0;
		};
		if (!onFace) {
			throw new IllegalArgumentException(id + ": port " + name + " not on face " + face);
		}
		ports.add(new Port(name, dir, face, x, y, z, bits));
		return this;
	}

	// ---- block placement --------------------------------------------------------------------

	private void check(int x, int y, int z) {
		if (x < 0 || y < 0 || z < 0 || x >= sx || y >= sy || z >= sz) {
			throw new IllegalArgumentException(id + ": (" + x + "," + y + "," + z + ") outside " + sx + "x" + sy + "x" + sz);
		}
	}

	public CellBuilder set(int x, int y, int z, BlockSpec b) {
		check(x, y, z);
		grid[x][y][z] = b;
		return this;
	}

	public CellBuilder set(int x, int y, int z, String block) {
		return set(x, y, z, BlockSpec.of(block));
	}

	public CellBuilder fill(int x0, int y0, int z0, int x1, int y1, int z1, String block) {
		return fill(x0, y0, z0, x1, y1, z1, BlockSpec.of(block));
	}

	public CellBuilder fill(int x0, int y0, int z0, int x1, int y1, int z1, BlockSpec b) {
		for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
			for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
				for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
					set(x, y, z, b);
				}
			}
		}
		return this;
	}

	/** Hollow box: the four vertical walls only, interior left untouched. */
	public CellBuilder walls(int x0, int y0, int z0, int x1, int y1, int z1, String block) {
		for (int x = x0; x <= x1; x++) {
			for (int y = y0; y <= y1; y++) {
				for (int z = z0; z <= z1; z++) {
					boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
					if (edge) {
						set(x, y, z, block);
					}
				}
			}
		}
		return this;
	}

	public BlockSpec get(int x, int y, int z) {
		check(x, y, z);
		return grid[x][y][z];
	}

	public CellBuilder dust(int x, int y, int z) {
		return set(x, y, z, BlockSpec.of("minecraft:redstone_wire",
				"north", "none", "south", "none", "east", "none", "west", "none", "power", "0"));
	}

	/** Repeater whose output goes toward {@code out}. Delay 1-4 redstone ticks. */
	public CellBuilder repeater(int x, int y, int z, Dir out, int delay) {
		return set(x, y, z, BlockSpec.of("minecraft:repeater",
				"facing", out.opposite().key(), "delay", Integer.toString(delay), "locked", "false", "powered", "false"));
	}

	/** Comparator whose output goes toward {@code out}. */
	public CellBuilder comparator(int x, int y, int z, Dir out, boolean subtract) {
		return set(x, y, z, BlockSpec.of("minecraft:comparator",
				"facing", out.opposite().key(), "mode", subtract ? "subtract" : "compare", "powered", "false"));
	}

	/** Standing redstone torch (on the block below). */
	public CellBuilder torch(int x, int y, int z) {
		return set(x, y, z, BlockSpec.of("minecraft:redstone_torch", "lit", "true"));
	}

	/** Wall torch pointing toward {@code facing}; it hangs on the block in the opposite direction. */
	public CellBuilder wallTorch(int x, int y, int z, Dir facing) {
		return set(x, y, z, BlockSpec.of("minecraft:redstone_wall_torch", "facing", facing.key(), "lit", "true"));
	}

	public CellBuilder lamp(int x, int y, int z) {
		return set(x, y, z, BlockSpec.of("minecraft:redstone_lamp", "lit", "false"));
	}

	public CellBuilder stickyPiston(int x, int y, int z, Dir facing) {
		return set(x, y, z, BlockSpec.of("minecraft:sticky_piston", "facing", facing.key(), "extended", "false"));
	}

	public CellBuilder piston(int x, int y, int z, Dir facing) {
		return set(x, y, z, BlockSpec.of("minecraft:piston", "facing", facing.key(), "extended", "false"));
	}

	public CellBuilder redstoneBlock(int x, int y, int z) {
		return set(x, y, z, "minecraft:redstone_block");
	}

	/** Convenience for facing-only block states (lanterns, glazed terracotta, etc). */
	public CellBuilder facing(int x, int y, int z, String block, Dir facing) {
		return set(x, y, z, BlockSpec.of(block, "facing", facing.key()));
	}

	// ---- dust connection pre-computation ----------------------------------------------------

	private static boolean connectsTo(BlockSpec b, Dir from) {
		if (b == null) {
			return false;
		}
		if (b.is("minecraft:redstone_wire") || b.is("minecraft:redstone_torch") || b.is("minecraft:redstone_wall_torch")
				|| b.is("minecraft:redstone_block") || b.is("minecraft:lever") || b.is("minecraft:comparator")
				|| b.name().contains("button") || b.name().contains("pressure_plate")) {
			return true;
		}
		if (b.is("minecraft:repeater")) {
			String f = b.prop("facing");
			boolean ns = f.equals("north") || f.equals("south");
			return ns == (from == Dir.NORTH || from == Dir.SOUTH);
		}
		if (b.is("minecraft:observer")) {
			return b.prop("facing").equals(from.key());
		}
		return false;
	}

	/**
	 * Fills in redstone_wire side properties the way vanilla would, so the placed structure does
	 * not depend on the post-placement shape update to look right. Vanilla still re-derives them.
	 */
	private void resolveDust() {
		Dir[] horiz = {Dir.NORTH, Dir.SOUTH, Dir.EAST, Dir.WEST};
		for (int x = 0; x < sx; x++) {
			for (int y = 0; y < sy; y++) {
				for (int z = 0; z < sz; z++) {
					BlockSpec b = grid[x][y][z];
					if (b == null || !b.is("minecraft:redstone_wire")) {
						continue;
					}
					Map<Dir, String> sides = new LinkedHashMap<>();
					int connected = 0;
					for (Dir d : horiz) {
						String v = "none";
						int nx = x + d.dx, nz = z + d.dz;
						boolean inside = nx >= 0 && nz >= 0 && nx < sx && nz < sz;
						BlockSpec side = inside ? grid[nx][y][nz] : null;
						boolean roofClear = y + 1 >= sy || grid[x][y + 1][z] == null;
						if (connectsTo(side, d)) {
							v = "side";
						} else if (inside && side != null && roofClear && y + 1 < sy && isDust(grid[nx][y + 1][nz])) {
							v = "up";
						} else if (inside && side == null && y - 1 >= 0 && isDust(grid[nx][y - 1][nz])) {
							v = "side";
						}
						if (!v.equals("none")) {
							connected++;
						}
						sides.put(d, v);
					}
					if (connected == 0) {
						for (Dir d : horiz) {
							sides.put(d, "side");
						}
					} else if (connected == 1) {
						for (Dir d : horiz) {
							if (!sides.get(d).equals("none")) {
								sides.put(d.opposite(), "side");
							}
						}
					}
					BlockSpec out = b;
					for (Map.Entry<Dir, String> e : sides.entrySet()) {
						out = out.with(e.getKey().key(), e.getValue());
					}
					grid[x][y][z] = out;
				}
			}
		}
	}

	private static boolean isDust(BlockSpec b) {
		return b != null && b.is("minecraft:redstone_wire");
	}

	// ---- output -----------------------------------------------------------------------------

	public Nbt.CompoundTag toStructureNbt() {
		resolveDust();
		Map<BlockSpec, Integer> palette = new LinkedHashMap<>();
		Nbt.ListTag blocks = new Nbt.ListTag();
		BlockSpec air = BlockSpec.of("minecraft:air");
		for (int x = 0; x < sx; x++) {
			for (int y = 0; y < sy; y++) {
				for (int z = 0; z < sz; z++) {
					BlockSpec b = grid[x][y][z] == null ? air : grid[x][y][z];
					int idx = palette.computeIfAbsent(b, k -> palette.size());
					blocks.add(new Nbt.CompoundTag().put("pos", Nbt.ints(x, y, z)).putInt("state", idx));
				}
			}
		}
		Nbt.ListTag pal = new Nbt.ListTag();
		palette.keySet().forEach(b -> pal.add(b.toNbt()));
		return new Nbt.CompoundTag()
				.put("size", Nbt.ints(sx, sy, sz))
				.put("palette", pal)
				.put("blocks", blocks)
				.put("entities", new Nbt.ListTag())
				.putInt("DataVersion", DATA_VERSION);
	}

	public String toSidecarJson() {
		StringBuilder sb = new StringBuilder();
		sb.append("{\n");
		sb.append("  \"id\": \"seedcity:").append(id).append("\",\n");
		sb.append("  \"kind\": \"").append(kind).append("\",\n");
		sb.append("  \"size\": [").append(sx).append(", ").append(sy).append(", ").append(sz).append("],\n");
		sb.append("  \"ports\": [");
		for (int i = 0; i < ports.size(); i++) {
			Port p = ports.get(i);
			sb.append(i == 0 ? "\n" : ",\n");
			sb.append("    { \"name\": \"").append(p.name()).append("\", \"dir\": \"").append(p.dir())
					.append("\", \"face\": \"").append(p.face().key()).append("\", \"pos\": [")
					.append(p.x()).append(", ").append(p.y()).append(", ").append(p.z())
					.append("], \"bits\": ").append(p.bits()).append(" }");
		}
		sb.append(ports.isEmpty() ? "],\n" : "\n  ],\n");
		sb.append("  \"truth\": \"").append(truth).append("\",\n");
		sb.append("  \"weights\": ").append(mapJson(weights)).append(",\n");
		sb.append("  \"cost\": ").append(mapJson(cost)).append(",\n");
		if (fault != null) {
			sb.append("  \"fault\": [").append(fault[0]).append(", ").append(fault[1]).append(", ").append(fault[2]).append("],\n");
		}
		if (loot != null) {
			sb.append("  \"loot\": \"").append(loot).append("\",\n");
		}
		sb.append("  \"setpiece\": ").append(setpiece).append("\n");
		sb.append("}\n");
		return sb.toString();
	}

	private static String mapJson(Map<String, Integer> m) {
		StringBuilder sb = new StringBuilder("{ ");
		boolean first = true;
		for (Map.Entry<String, Integer> e : m.entrySet()) {
			if (!first) {
				sb.append(", ");
			}
			first = false;
			sb.append('"').append(e.getKey()).append("\": ").append(e.getValue());
		}
		return sb.append(" }").toString();
	}

	public void writeTo(Path dir) throws IOException {
		Files.createDirectories(dir);
		try (OutputStream out = Files.newOutputStream(dir.resolve(id + ".nbt"))) {
			Nbt.writeCompressed(toStructureNbt(), out);
		}
		Files.writeString(dir.resolve(id + ".json"), toSidecarJson(), StandardCharsets.UTF_8);
	}
}
