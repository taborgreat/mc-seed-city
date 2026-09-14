package net.tabor.seedcity.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.tabor.seedcity.SeedCity;
import net.tabor.seedcity.config.SeedCityConfig;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** All cities in a level, persisted with the level. Ticks each city once a second. */
public final class CityManager extends SavedData {
	public static final Codec<CityManager> CODEC = RecordCodecBuilder.create(i -> i.group(
			CityState.CODEC.listOf().fieldOf("cities").forGetter(m -> new ArrayList<>(m.cities.values()))
	).apply(i, CityManager::new));
	/** Command storage has the most inert data fixers; our tag carries no vanilla structures. */
	public static final SavedDataType<CityManager> TYPE = new SavedDataType<>(SeedCity.id("cities"), CityManager::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	private final Map<BlockPos, CityState> cities = new LinkedHashMap<>();

	public CityManager() {
	}

	private CityManager(List<CityState> loaded) {
		for (CityState c : loaded) {
			cities.put(c.seedPos(), c);
		}
	}

	public static CityManager get(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(TYPE);
	}

	public static void init() {
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			CityManager m = get(level);
			if (m.cities.isEmpty()) {
				return;
			}
			boolean second = level.getGameTime() % 20 == 0;
			for (CityState c : new ArrayList<>(m.cities.values())) {
				try {
					if (!level.isPositionEntityTicking(c.seedPos())) {
						continue;
					}
					c.tickProgram(level);
					if (second) {
						c.upkeep(level);
					}
				} catch (Exception e) {
					SeedCity.LOGGER.error("City {} tick failed", c.seedPos().toShortString(), e);
				}
			}
			if (second) {
				m.setDirty();
			}
		});
	}

	/** Roots a new city at a powered Seed. Idempotent. */
	public Optional<CityState> activate(ServerLevel level, BlockPos seedPos) {
		return activate(level, seedPos, null);
	}

	/** Roots a city with its own config (tests and tools); null uses the global config. */
	public Optional<CityState> activate(ServerLevel level, BlockPos seedPos, SeedCityConfig cfg) {
		if (cities.containsKey(seedPos)) {
			return Optional.of(cities.get(seedPos));
		}
		SeedCityConfig effective = cfg != null ? cfg : SeedCityConfig.get();
		CityState city = CityState.create(level.getSeed(), seedPos, effective);
		if (cfg != null) {
			city.overrideConfig(cfg);
		}
		cities.put(seedPos, city);
		city.spawnBuilder(level);
		setDirty();
		SeedCity.LOGGER.info("City rooted at {} (seed {})", seedPos.toShortString(), Long.toHexString(city.citySeed()));
		return Optional.of(city);
	}

	public void freeze(BlockPos seedPos) {
		CityState c = cities.get(seedPos);
		if (c != null) {
			c.freeze();
			setDirty();
		}
	}

	public Optional<CityState> city(BlockPos seedPos) {
		return Optional.ofNullable(cities.get(seedPos));
	}

	public Optional<CityState> nearest(BlockPos pos) {
		CityState best = null;
		double bestDist = Double.MAX_VALUE;
		for (CityState c : cities.values()) {
			double d = c.seedPos().distSqr(pos);
			if (d < bestDist) {
				bestDist = d;
				best = c;
			}
		}
		return Optional.ofNullable(best);
	}

	public Collection<CityState> all() {
		return cities.values();
	}

	/** Marks a city dirty after a state change made outside upkeep. */
	public void touch() {
		setDirty();
	}
}
