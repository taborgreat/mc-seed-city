package net.tabor.seedcity.entity;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.tabor.seedcity.SeedCity;

/** The five mobs are registered here. Builder, Warden and Sentinel so far; Courier and Collector later. */
public final class SeedCityEntities {
	public static EntityType<BuilderEntity> BUILDER;
	public static EntityType<WardenEntity> WARDEN;
	public static EntityType<SentinelEntity> SENTINEL;
	public static EntityType<CourierEntity> COURIER;

	private SeedCityEntities() {
	}

	public static void init() {
		ResourceKey<EntityType<?>> builderKey = ResourceKey.create(Registries.ENTITY_TYPE, SeedCity.id("builder"));
		BUILDER = Registry.register(BuiltInRegistries.ENTITY_TYPE, builderKey,
				EntityType.Builder.of(BuilderEntity::new, MobCategory.MISC).sized(0.6F, 0.7F).clientTrackingRange(10).build(builderKey));
		FabricDefaultAttributeRegistry.register(BUILDER, BuilderEntity.createAttributes());

		ResourceKey<EntityType<?>> wardenKey = ResourceKey.create(Registries.ENTITY_TYPE, SeedCity.id("warden"));
		WARDEN = Registry.register(BuiltInRegistries.ENTITY_TYPE, wardenKey,
				EntityType.Builder.of(WardenEntity::new, MobCategory.MISC).sized(1.4F, 2.7F).clientTrackingRange(10).build(wardenKey));
		FabricDefaultAttributeRegistry.register(WARDEN, WardenEntity.createAttributes());

		ResourceKey<EntityType<?>> sentinelKey = ResourceKey.create(Registries.ENTITY_TYPE, SeedCity.id("sentinel"));
		SENTINEL = Registry.register(BuiltInRegistries.ENTITY_TYPE, sentinelKey,
				EntityType.Builder.of(SentinelEntity::new, MobCategory.MONSTER).sized(0.6F, 1.95F).clientTrackingRange(8).build(sentinelKey));
		FabricDefaultAttributeRegistry.register(SENTINEL, SentinelEntity.createAttributes());

		ResourceKey<EntityType<?>> courierKey = ResourceKey.create(Registries.ENTITY_TYPE, SeedCity.id("courier"));
		COURIER = Registry.register(BuiltInRegistries.ENTITY_TYPE, courierKey,
				EntityType.Builder.of(CourierEntity::new, MobCategory.MISC).sized(0.4F, 0.6F).clientTrackingRange(10).build(courierKey));
		FabricDefaultAttributeRegistry.register(COURIER, CourierEntity.createAttributes());
	}
}
