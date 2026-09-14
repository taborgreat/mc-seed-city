package net.tabor.seedcity;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import net.tabor.seedcity.cell.CellLibrary;
import net.tabor.seedcity.command.SeedCityCommands;
import net.tabor.seedcity.config.SeedCityConfig;
import net.tabor.seedcity.core.CityManager;
import net.tabor.seedcity.entity.SeedCityEntities;
import net.tabor.seedcity.verify.Verifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Seed City entrypoint. A working redstone city grows itself from a seed, is built and
 * maintained by mobs, and can be steered by the player.
 *
 * <p>Spec of record: {@code Seed_City_Design_Doc.pdf} at the repo root. Part III wins.
 * Every piece of code belongs to exactly one of the five interfaces (Cell, Grammar,
 * BuildTask, Verify, Card); see the package-info of each sub-package.
 */
public final class SeedCity implements ModInitializer {
	public static final String MOD_ID = "seedcity";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		SeedCityConfig.load();
		SeedCityBlocks.init();
		SeedCityItems.init();
		SeedCityEntities.init();
		CellLibrary.init();
		net.tabor.seedcity.card.CardLibrary.init();
		Verifier.init();
		CityManager.init();
		SeedCityCommands.init();
		LOGGER.info("Seed City loaded");
	}
}
