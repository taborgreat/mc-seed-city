package net.tabor.seedcity;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.CustomData;
import net.tabor.seedcity.entity.SeedCityEntities;
import net.tabor.seedcity.extra.RedstoneRats;

import java.util.Optional;

/**
 * Items that are not block items. The Blueprint: hand it to a Builder and it builds that cell
 * where you stand. Spawn eggs for the mobs: ordinary spawning, no city assignment (a mob from
 * an egg belongs to no city until one adopts it).
 */
public final class SeedCityItems {
	public static Item BLUEPRINT;

	private SeedCityItems() {
	}

	public static void init() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, SeedCity.id("blueprint"));
		BLUEPRINT = Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key).stacksTo(16)));
		egg("builder", SeedCityEntities.BUILDER);
		egg("rectifier", SeedCityEntities.WARDEN);
		egg("courier", SeedCityEntities.COURIER);
		egg("sentinel", SeedCityEntities.SENTINEL);
		egg("collector", SeedCityEntities.COLLECTOR);
		egg("redstone_rat", RedstoneRats.TYPE);
	}

	private static void egg(String name, EntityType<?> type) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, SeedCity.id(name + "_spawn_egg"));
		Item item = Registry.register(BuiltInRegistries.ITEM, key, new SpawnEggItem(new Item.Properties().setId(key).spawnEgg(type)));
		CreativeModeTabEvents.modifyOutputEvent(ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("spawn_eggs")))
				.register(entries -> entries.accept(item));
	}

	/** A blueprint for one cell type (id path such as {@code register_block}). */
	public static ItemStack blueprint(String cell) {
		ItemStack stack = new ItemStack(BLUEPRINT);
		CompoundTag tag = new CompoundTag();
		tag.putString("cell", cell);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		stack.set(DataComponents.CUSTOM_NAME, Component.literal("Blueprint: " + cell));
		return stack;
	}

	public static Optional<String> blueprintCell(ItemStack stack) {
		if (!stack.is(BLUEPRINT)) {
			return Optional.empty();
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return Optional.empty();
		}
		return data.copyTag().getString("cell");
	}
}
