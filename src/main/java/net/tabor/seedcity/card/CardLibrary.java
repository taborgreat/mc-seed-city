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
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Built-in cards from {@code data/<ns>/cards/<name>.card}: examples players can copy into a book. */
public final class CardLibrary extends SimpleReloadListener<Map<String, String>> {
	public static final Identifier ID = SeedCity.id("cards");
	private static volatile Map<String, String> cards = Map.of();

	public static void init() {
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(ID, new CardLibrary());
	}

	public static Optional<String> get(String name) {
		return Optional.ofNullable(cards.get(name));
	}

	public static Collection<String> names() {
		return cards.keySet();
	}

	@Override
	protected Map<String, String> prepare(PreparableReloadListener.SharedState state) {
		ResourceManager rm = state.resourceManager();
		Map<String, String> out = new TreeMap<>();
		for (Map.Entry<Identifier, Resource> e : rm.listResources("cards", id -> id.getPath().endsWith(".card")).entrySet()) {
			String path = e.getKey().getPath();
			String name = path.substring("cards/".length(), path.length() - ".card".length());
			try (BufferedReader r = e.getValue().openAsReader()) {
				out.put(name, r.lines().collect(Collectors.joining("\n")));
			} catch (Exception ex) {
				SeedCity.LOGGER.error("Card {} unreadable: {}", e.getKey(), ex.toString());
			}
		}
		return out;
	}

	@Override
	protected void apply(Map<String, String> prepared, PreparableReloadListener.SharedState state) {
		cards = Map.copyOf(prepared);
		SeedCity.LOGGER.info("Card library: {} cards", cards.size());
	}
}
