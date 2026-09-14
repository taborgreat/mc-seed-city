package net.tabor.seedcity;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.tabor.seedcity.entity.SeedCityEntities;

/** Creative spawn eggs use ordinary entity spawning, without assigning city tasks. */
public final class SeedCityItems {
    private SeedCityItems() {}

    public static void init() {
        egg("builder", SeedCityEntities.BUILDER);
        egg("rectifier", SeedCityEntities.WARDEN);
        egg("courier", SeedCityEntities.COURIER);
        egg("collector", SeedCityEntities.COLLECTOR);
        egg("sentinel", SeedCityEntities.SENTINEL);
        egg("redstone_rat", SeedCityEntities.REDSTONE_RAT);
    }

    private static void egg(String name, EntityType<?> type) {
        var key = ResourceKey.create(Registries.ITEM, SeedCity.id(name + "_spawn_egg"));
        var item = Registry.register(BuiltInRegistries.ITEM, key,
                new SpawnEggItem(new Item.Properties().setId(key).spawnEgg(type)));
        CreativeModeTabEvents.modifyOutputEvent(ResourceKey.create(Registries.CREATIVE_MODE_TAB,
                Identifier.withDefaultNamespace("spawn_eggs"))).register(entries -> entries.accept(item));
    }
}
