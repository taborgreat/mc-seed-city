package net.tabor.seedcity.card;

import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.reloader.SimpleReloadListener;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.tabor.seedcity.SeedCity;

import java.io.BufferedReader;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * The L3 fragment library (design doc 14, 21): card snippets in {@code data/<ns>/fragments/*.frag}
 * that the city recombines into its own programs. Placeholders: {@code $A1..} actuator input
 * ports, {@code $S1..} sensor output ports, {@code $R1..} registers, {@code $N1..} numbers 1-15,
 * {@code $L1..} labels. Hand-authored; see docs/fragments.md.
 */
public final class FragmentLibrary extends SimpleReloadListener<Map<String, String>> {
	public static final Identifier ID = SeedCity.id("fragments");
	private static volatile Map<String, String> fragments = Map.of();

	public static void init() {
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(ID, new FragmentLibrary());
	}

	/** Name to text, in name order. */
	public static Map<String, String> all() {
		return fragments;
	}

	public static List<String> names() {
		return List.copyOf(fragments.keySet());
	}

	/** Tests and tools may install a library without a resource reload. */
	public static void install(Map<String, String> set) {
		fragments = Map.copyOf(new TreeMap<>(set));
	}

	@Override
	protected Map<String, String> prepare(PreparableReloadListener.SharedState state) {
		ResourceManager rm = state.resourceManager();
		Map<String, String> out = new TreeMap<>();
		for (Map.Entry<Identifier, Resource> e : rm.listResources("fragments", id -> id.getPath().endsWith(".frag")).entrySet()) {
			String path = e.getKey().getPath();
			String name = path.substring("fragments/".length(), path.length() - ".frag".length());
			try (BufferedReader r = e.getValue().openAsReader()) {
				out.put(name, r.lines().collect(Collectors.joining("\n")));
			} catch (Exception ex) {
				SeedCity.LOGGER.error("Fragment {} unreadable: {}", e.getKey(), ex.toString());
			}
		}
		return out;
	}

	@Override
	protected void apply(Map<String, String> prepared, PreparableReloadListener.SharedState state) {
		fragments = Map.copyOf(new TreeMap<>(prepared));
		SeedCity.LOGGER.info("Fragment library: {} fragments", fragments.size());
	}
}
