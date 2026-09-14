package net.tabor.seedcity.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.tabor.seedcity.entity.SeedCityEntities;

/** Client-only entrypoint: entity renderers, the Reader wall screen, and nothing that touches CityState. */
public final class SeedCityClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
        ModelLayerRegistry.registerModelLayer(RedstoneRatRenderer.LAYER,RedstoneRatMesh::createLayer);
        EntityRendererRegistry.register(SeedCityEntities.REDSTONE_RAT,RedstoneRatRenderer::new);
		ModelLayerRegistry.registerModelLayer(CollectorRenderer.LAYER,CollectorMesh::createLayer);
		EntityRendererRegistry.register(SeedCityEntities.COLLECTOR,CollectorRenderer::new);
		ModelLayerRegistry.registerModelLayer(CourierRenderer.LAYER, CourierMesh::createLayer);
		EntityRendererRegistry.register(SeedCityEntities.COURIER, CourierRenderer::new);
		ClientTickEvents.END_CLIENT_TICK.register(RectifierCapeMotion::tick);
        ClientTickEvents.END_CLIENT_TICK.register(CollectorLanternMotion::tick);
		ModelLayerRegistry.registerModelLayer(BuilderRenderer.LAYER, BuilderMesh::createLayer);
		EntityRendererRegistry.register(SeedCityEntities.BUILDER, BuilderRenderer::new);
		ModelLayerRegistry.registerModelLayer(RectifierRenderer.LAYER, RectifierMesh::createLayer);
		EntityRendererRegistry.register(SeedCityEntities.WARDEN, RectifierRenderer::new);
		ModelLayerRegistry.registerModelLayer(SentinelRenderer.LAYER, SentinelMesh::createLayer);
		EntityRendererRegistry.register(SeedCityEntities.SENTINEL, SentinelRenderer::new);
	}
}
