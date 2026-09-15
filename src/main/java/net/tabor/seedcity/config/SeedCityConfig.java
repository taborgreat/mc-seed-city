package net.tabor.seedcity.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.tabor.seedcity.SeedCity;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Server config (design doc 9, 26): containment caps and rates. Loaded once from
 * {@code config/seedcity.json}; missing keys keep their defaults, a missing file is written out.
 * Tests may swap the active instance with {@link #override(SeedCityConfig)}.
 */
public final class SeedCityConfig {
	/** Slots (7-block cells) the city may grow from the core in any direction. */
	public int maxRadiusSlots = 6;
	public int maxBuilders = 6;
	/** A new builder is spawned for every this many built cells, up to maxBuilders. */
	public int cellsPerBuilder = 4;
	/** Blocks a builder places per second (doc 24: default 4). */
	public int blocksPerSecond = 4;
	public double builderSpeed = 1.0;
	/** Seconds a builder waits on an unreachable target before re-queuing the task (doc 24: 30). */
	public int abandonSeconds = 30;
	/**
	 * Material the Seed starts the city with: enough for the root and the first ring, and short of
	 * wood from the start, so Collectors are out within the first minute and growth is paced by what
	 * they bring home.
	 */
	public int initialRedstone = 1200;
	public int initialStone = 4000;
	public int initialWood = 120;
	public boolean unlimitedMaterials = false;
	/** Hard chunk cap: growth stops once the city footprint spans this many chunks. */
	public int maxChunks = 64;
	/** Testing only: when non-zero, every new city uses this seed instead of deriving one. */
	public long citySeedOverride = 0;
	/** Fault Cells planted per city (doc 7: every generated city ships with faults). */
	public int faultsPerCity = 1;
	/** Live cells the clock must reach before the planner plants the first fault. */
	public int faultAfterLiveCells = 6;
	/** Seconds after a Warden dies before the Core sends another (doc 24: 5 minutes). */
	public int wardenRespawnSeconds = 300;
	/** Ticks between a Warden's integrity sweeps of its district (doc 24: 100). */
	public int wardenSweepTicks = 100;
	/** Spawn a Sentinel on every built register block. */
	public boolean sentinelsOnRegisters = true;
	/** Redstone Rats kept near each city (extra, outside the five mobs); 0 disables them. */
	public int maxRedstoneRats = 2;
	/** Couriers a city may have in flight at once. */
	public int maxCouriers = 4;
	/** Angular sectors the city is zoned into (Forge, RAM, Storage, residential, plaza), seed-shuffled. */
	public int sectors = 5;
	/** Terrain (Phase 5): a slot is unbuildable when the ground under it varies by more than this. */
	public int slopeLimit = 3;
	/** A slot joins a neighbour's level (so their ports mate) when the ground is within this of it. */
	public int terrainStep = 2;
	/** How far below a floor the Builder will lay foundation before calling the drop too steep. */
	public int foundationDepth = 6;
	/** Width of the earth apron banked against the city's open edges; 0 turns blending off. */
	public int apronWidth = 3;
	/** Collectors (doc 24: max_collectors default 4). */
	public int maxCollectors = 4;
	/** Blocks a Collector mines per trip before hauling the load home. */
	public int collectorLoad = 4;
	/** Ticks a Collector spends breaking one block. */
	public int collectorTicksPerBlock = 30;
	/** How far beyond the city's radius Collectors will go for material. */
	public int collectorRange = 48;
	/** Ledger units the city tries to keep in stock per currency; below it Collectors are sent out. */
	public int reserveRedstone = 120;
	public int reserveStone = 400;
	public int reserveWood = 120;
	/** L3: seconds the card slot must stay empty with an idle frontier before the city dreams a card (doc 25: 10 minutes). */
	public int dreamAfterSeconds = 600;
	/** L3: how long a dream runs before the city dreams another, if still idle and undisturbed. */
	public int dreamLengthSeconds = 1800;
	/** Floating labels over every cell (name, district, clocked, port values); /seedcity labels toggles per city. */
	public boolean cellLabels = true;
	/** A new city with nothing in its reader dreams its first card at once, so it always has a purpose. */
	public boolean dreamAtStart = true;

	private static SeedCityConfig current = new SeedCityConfig();
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public static SeedCityConfig get() {
		return current;
	}

	public static void override(SeedCityConfig config) {
		current = config;
	}

	public static void load() {
		Path file = FabricLoader.getInstance().getConfigDir().resolve("seedcity.json");
		try {
			if (Files.exists(file)) {
				try (Reader r = Files.newBufferedReader(file)) {
					SeedCityConfig loaded = GSON.fromJson(r, SeedCityConfig.class);
					if (loaded != null) {
						current = loaded;
					}
				}
			} else {
				Files.createDirectories(file.getParent());
				Files.writeString(file, GSON.toJson(current));
			}
		} catch (IOException | RuntimeException e) {
			SeedCity.LOGGER.error("Could not read {}; using defaults", file, e);
			current = new SeedCityConfig();
		}
		current.clamp();
	}

	private void clamp() {
		maxRadiusSlots = Math.max(1, maxRadiusSlots);
		maxBuilders = Math.max(1, maxBuilders);
		cellsPerBuilder = Math.max(1, cellsPerBuilder);
		blocksPerSecond = Math.max(1, blocksPerSecond);
		abandonSeconds = Math.max(1, abandonSeconds);
		maxChunks = Math.max(1, maxChunks);
	}

	public int ticksPerBlock() {
		return Math.max(1, 20 / blocksPerSecond);
	}
}
