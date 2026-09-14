package net.tabor.seedcity.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.tabor.seedcity.entity.SeedCityEntities;

/** Client-only entrypoint: entity renderers, the Reader wall screen, and nothing that touches CityState. */
public final class SeedCityClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(SeedCityEntities.BUILDER, BuilderRenderer::new);
		EntityRendererRegistry.register(SeedCityEntities.WARDEN, WardenRenderer::new);
		EntityRendererRegistry.register(SeedCityEntities.SENTINEL, SentinelRenderer::new);
		EntityRendererRegistry.register(SeedCityEntities.COURIER, CourierRenderer::new);
	}
}
