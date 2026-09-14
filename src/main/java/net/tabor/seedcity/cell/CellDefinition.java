package net.tabor.seedcity.cell;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.Identifier;
import net.minecraft.util.GsonHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The sidecar JSON of a cell (design doc 22.1). The only thing the grammar and the verifier read.
 *
 * @param settleTicks game ticks to wait after driving inputs before sampling outputs; optional
 *                    "settle" field, default 40
 * @param fault       optional "fault" field: the one block the planner may leave out to plant a
 *                    Fault Cell (doc 5.1, 7); null when the cell cannot be faulted
 * @param loot        optional "loot" field: loot table id every chest in the cell is filled from
 */
public record CellDefinition(
		Identifier id,
		CellKind kind,
		Vec3i size,
		List<Port> ports,
		String truth,
		Map<String, Integer> weights,
		Map<String, Integer> cost,
		boolean setpiece,
		int settleTicks,
		BlockPos fault,
		Identifier loot
) {
	public boolean faultable() {
		return fault != null;
	}

	public static final int DEFAULT_SETTLE_TICKS = 40;

	public CellDefinition {
		ports = List.copyOf(ports);
		weights = Map.copyOf(weights);
		cost = Map.copyOf(cost);
	}

	public List<Port> inputs() {
		return ports.stream().filter(p -> p.dir() == PortDir.IN).toList();
	}

	public List<Port> outputs() {
		return ports.stream().filter(p -> p.dir() == PortDir.OUT).toList();
	}

	public Port port(String name) {
		for (Port p : ports) {
			if (p.name().equals(name)) {
				return p;
			}
		}
		return null;
	}

	public int weight(String district) {
		return weights.getOrDefault(district, 0);
	}

	public static CellDefinition parse(JsonObject json) throws CellFormatException {
		try {
			Identifier id = Identifier.parse(GsonHelper.getAsString(json, "id"));
			CellKind kind = CellKind.parse(GsonHelper.getAsString(json, "kind"));
			Vec3i size = vec(GsonHelper.getAsJsonArray(json, "size"), "size");
			if (size.getX() <= 0 || size.getY() <= 0 || size.getZ() <= 0) {
				throw new CellFormatException("size must be positive, got " + size);
			}
			List<Port> ports = new ArrayList<>();
			Set<String> names = new HashSet<>();
			if (json.has("ports")) {
				for (JsonElement e : GsonHelper.getAsJsonArray(json, "ports")) {
					Port p = parsePort(GsonHelper.convertToJsonObject(e, "port"), size);
					if (!names.add(p.name())) {
						throw new CellFormatException("duplicate port name " + p.name());
					}
					ports.add(p);
				}
			}
			String truth = GsonHelper.getAsString(json, "truth", "none");
			Map<String, Integer> weights = intMap(json, "weights");
			Map<String, Integer> cost = intMap(json, "cost");
			boolean setpiece = GsonHelper.getAsBoolean(json, "setpiece", true);
			int settle = GsonHelper.getAsInt(json, "settle", DEFAULT_SETTLE_TICKS);
			if (kind == CellKind.DECOR && !ports.isEmpty()) {
				throw new CellFormatException("decor cells must have no ports");
			}
			BlockPos fault = null;
			if (json.has("fault")) {
				Vec3i f = vec(GsonHelper.getAsJsonArray(json, "fault"), "fault");
				fault = new BlockPos(f.getX(), f.getY(), f.getZ());
				if (f.getX() < 0 || f.getY() < 0 || f.getZ() < 0 || f.getX() >= size.getX() || f.getY() >= size.getY() || f.getZ() >= size.getZ()) {
					throw new CellFormatException("fault " + fault + " outside size " + size);
				}
			}
			Identifier loot = json.has("loot") ? Identifier.parse(GsonHelper.getAsString(json, "loot")) : null;
			return new CellDefinition(id, kind, size, ports, truth, weights, cost, setpiece, settle, fault, loot);
		} catch (JsonSyntaxException | IllegalStateException | IllegalArgumentException e) {
			throw new CellFormatException(e.getMessage(), e);
		}
	}

	private static Port parsePort(JsonObject o, Vec3i size) throws CellFormatException {
		String name = GsonHelper.getAsString(o, "name");
		PortDir dir = PortDir.parse(GsonHelper.getAsString(o, "dir"));
		String faceName = GsonHelper.getAsString(o, "face");
		Direction face = Direction.byName(faceName);
		if (face == null) {
			throw new CellFormatException("port " + name + ": unknown face " + faceName);
		}
		Vec3i v = vec(GsonHelper.getAsJsonArray(o, "pos"), "port " + name + " pos");
		BlockPos pos = new BlockPos(v.getX(), v.getY(), v.getZ());
		int bits = GsonHelper.getAsInt(o, "bits");
		if (bits != 1 && bits != 4) {
			throw new CellFormatException("port " + name + ": bits must be 1 or 4");
		}
		if (pos.getX() < 0 || pos.getY() < 0 || pos.getZ() < 0
				|| pos.getX() >= size.getX() || pos.getY() >= size.getY() || pos.getZ() >= size.getZ()) {
			throw new CellFormatException("port " + name + ": pos " + pos + " outside size " + size);
		}
		boolean onFace = switch (face) {
			case NORTH -> pos.getZ() == 0;
			case SOUTH -> pos.getZ() == size.getZ() - 1;
			case WEST -> pos.getX() == 0;
			case EAST -> pos.getX() == size.getX() - 1;
			case UP -> pos.getY() == size.getY() - 1;
			case DOWN -> pos.getY() == 0;
		};
		if (!onFace) {
			throw new CellFormatException("port " + name + ": pos " + pos + " is not on face " + faceName);
		}
		return new Port(name, dir, face, pos, bits);
	}

	private static Vec3i vec(JsonArray a, String what) throws CellFormatException {
		if (a.size() != 3) {
			throw new CellFormatException(what + " must have 3 ints");
		}
		return new Vec3i(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt());
	}

	private static Map<String, Integer> intMap(JsonObject json, String key) {
		Map<String, Integer> m = new LinkedHashMap<>();
		if (json.has(key)) {
			JsonObject o = GsonHelper.getAsJsonObject(json, key);
			for (Map.Entry<String, JsonElement> e : o.entrySet()) {
				m.put(e.getKey(), e.getValue().getAsInt());
			}
		}
		return m;
	}
}
