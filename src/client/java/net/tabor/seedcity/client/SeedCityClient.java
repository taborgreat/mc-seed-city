package net.tabor.seedcity.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.tabor.seedcity.entity.SeedCityEntities;
import net.tabor.seedcity.extra.RedstoneRats;

/**
 * Client-only entrypoint: the mob models and renderers, and nothing that touches CityState.
 * Meshes and textures were drawn by Mcdrizzy (PR #1); each model layer is registered before
 * its renderer bakes it. The Warden of the design doc wears the Rectifier art.
 */
public final class SeedCityClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ModelLayerRegistry.registerModelLayer(BuilderRenderer.LAYER, BuilderMesh::createLayer);
		EntityRendererRegistry.register(SeedCityEntities.BUILDER, BuilderRenderer::new);
		ModelLayerRegistry.registerModelLayer(RectifierRenderer.LAYER, RectifierMesh::createLayer);
		EntityRendererRegistry.register(SeedCityEntities.WARDEN, RectifierRenderer::new);
		ModelLayerRegistry.registerModelLayer(SentinelRenderer.LAYER, SentinelMesh::createLayer);
		EntityRendererRegistry.register(SeedCityEntities.SENTINEL, SentinelRenderer::new);
		ModelLayerRegistry.registerModelLayer(CourierRenderer.LAYER, CourierMesh::createLayer);
		EntityRendererRegistry.register(SeedCityEntities.COURIER, CourierRenderer::new);
		ModelLayerRegistry.registerModelLayer(CollectorRenderer.LAYER, CollectorMesh::createLayer);
		EntityRendererRegistry.register(SeedCityEntities.COLLECTOR, CollectorRenderer::new);
		ClientTickEvents.END_CLIENT_TICK.register(CollectorLanternMotion::tick);
		// the Rectifier's cape lags behind it like a player cloak; all of that state is client-side
		ClientTickEvents.END_CLIENT_TICK.register(RectifierCapeMotion::tick);
		// extra, outside the five city mobs
		ModelLayerRegistry.registerModelLayer(RedstoneRatRenderer.LAYER, RedstoneRatMesh::createLayer);
		EntityRendererRegistry.register(RedstoneRats.TYPE, RedstoneRatRenderer::new);
	}
}
