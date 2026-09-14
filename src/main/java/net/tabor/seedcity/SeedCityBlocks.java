package net.tabor.seedcity;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.tabor.seedcity.core.CardReaderBlock;
import net.tabor.seedcity.core.SeedBlock;
import net.tabor.seedcity.verify.ProbeBlock;

/** Block and block-item registration. Kept in one place so the registry order is obvious. */
public final class SeedCityBlocks {
	/** Verifier-only directional signal source. */
	public static Block PROBE;
	/** Core-owned directional signal source: how a program reaches a port until buses exist. */
	public static Block TERMINAL;
	public static Block SEED;
	public static Block CARD_READER;
	public static Item SEED_ITEM;

	private SeedCityBlocks() {
	}

	public static void init() {
		// Not redstone conductors: otherwise a neighbour's dust or diode behind them would be
		// relayed through to the port, and they could never hold a port at 0.
		ResourceKey<Block> probeKey = ResourceKey.create(Registries.BLOCK, SeedCity.id("probe"));
		PROBE = Registry.register(BuiltInRegistries.BLOCK, probeKey,
				new ProbeBlock(BlockBehaviour.Properties.of().setId(probeKey).strength(-1.0F, 3600000.0F).noLootTable()
						.noOcclusion().isRedstoneConductor((state, level, pos) -> false)));

		ResourceKey<Block> terminalKey = ResourceKey.create(Registries.BLOCK, SeedCity.id("terminal"));
		TERMINAL = Registry.register(BuiltInRegistries.BLOCK, terminalKey,
				new ProbeBlock(BlockBehaviour.Properties.of().setId(terminalKey).strength(-1.0F, 3600000.0F).noLootTable()
						.noOcclusion().lightLevel(s -> 6).isRedstoneConductor((state, level, pos) -> false)));

		ResourceKey<Block> seedKey = ResourceKey.create(Registries.BLOCK, SeedCity.id("seed"));
		SEED = Registry.register(BuiltInRegistries.BLOCK, seedKey,
				new SeedBlock(BlockBehaviour.Properties.of().setId(seedKey).mapColor(MapColor.COLOR_RED).strength(3.0F, 6.0F).lightLevel(s -> 7)));
		ResourceKey<Item> seedItemKey = ResourceKey.create(Registries.ITEM, SeedCity.id("seed"));
		SEED_ITEM = Registry.register(BuiltInRegistries.ITEM, seedItemKey,
				new BlockItem(SEED, new Item.Properties().setId(seedItemKey).useBlockDescriptionPrefix()));

		ResourceKey<Block> readerKey = ResourceKey.create(Registries.BLOCK, SeedCity.id("card_reader"));
		CARD_READER = Registry.register(BuiltInRegistries.BLOCK, readerKey,
				new CardReaderBlock(BlockBehaviour.Properties.of().setId(readerKey).mapColor(MapColor.WOOD).strength(2.0F, 6.0F)));
	}
}
