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
import net.tabor.seedcity.core.SeedBlock;
import net.tabor.seedcity.verify.ProbeBlock;

/** Block and block-item registration. Kept in one place so the registry order is obvious. */
public final class SeedCityBlocks {
	public static Block PROBE;
	public static Block SEED;
	public static Block COLLECTOR_LIGHT;
	public static Item SEED_ITEM;

	private SeedCityBlocks() {
	}

	public static void init() {
		ResourceKey<Block> lampKey=ResourceKey.create(Registries.BLOCK,SeedCity.id("collector_light"));
		COLLECTOR_LIGHT=Registry.register(BuiltInRegistries.BLOCK,lampKey,
				new net.tabor.seedcity.entity.CollectorLightBlock(BlockBehaviour.Properties.of().setId(lampKey)
						.noCollision().noOcclusion().noLootTable().replaceable().strength(-1)
						.lightLevel(s->12).isRedstoneConductor((s,l,p)->false)));
		ResourceKey<Block> probeKey = ResourceKey.create(Registries.BLOCK, SeedCity.id("probe"));
		// Not a redstone conductor: otherwise a neighbour's dust or diode behind the probe would be
		// relayed through it to the port under test, and the probe could never hold the port at 0.
		PROBE = Registry.register(BuiltInRegistries.BLOCK, probeKey,
				new ProbeBlock(BlockBehaviour.Properties.of().setId(probeKey).strength(-1.0F, 3600000.0F).noLootTable()
						.noOcclusion().isRedstoneConductor((state, level, pos) -> false)));

		ResourceKey<Block> seedKey = ResourceKey.create(Registries.BLOCK, SeedCity.id("seed"));
		SEED = Registry.register(BuiltInRegistries.BLOCK, seedKey,
				new SeedBlock(BlockBehaviour.Properties.of().setId(seedKey).mapColor(MapColor.COLOR_RED).strength(3.0F, 6.0F).lightLevel(s -> 7)));
		ResourceKey<Item> seedItemKey = ResourceKey.create(Registries.ITEM, SeedCity.id("seed"));
		SEED_ITEM = Registry.register(BuiltInRegistries.ITEM, seedItemKey,
				new BlockItem(SEED, new Item.Properties().setId(seedItemKey).useBlockDescriptionPrefix()));
	}
}
