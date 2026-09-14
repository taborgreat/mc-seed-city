package net.tabor.seedcity.client;

import net.minecraft.client.model.animal.allay.AllayModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.AllayRenderState;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.tabor.seedcity.entity.CourierEntity;

/** Placeholder look for the Courier: an allay that visibly carries a page while on a delivery. */
public final class CourierRenderer extends MobRenderer<CourierEntity, AllayRenderState, AllayModel> {
	private static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/entity/allay/allay.png");

	public CourierRenderer(EntityRendererProvider.Context context) {
		super(context, new AllayModel(context.bakeLayer(ModelLayers.ALLAY)), 0.3F);
		addLayer(new ItemInHandLayer<>(this));
	}

	@Override
	public Identifier getTextureLocation(AllayRenderState state) {
		return TEXTURE;
	}

	@Override
	public AllayRenderState createRenderState() {
		return new AllayRenderState();
	}

	@Override
	public void extractRenderState(CourierEntity entity, AllayRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		ArmedEntityRenderState.extractArmedEntityRenderState(entity, state, this.itemModelResolver, partialTicks);
		state.isDancing = false;
		state.isSpinning = false;
		state.spinningProgress = 0;
		state.holdingAnimationProgress = entity.getMainHandItem().isEmpty() ? 0 : 1;
	}

	@Override
	protected int getBlockLightLevel(CourierEntity entity, BlockPos pos) {
		return 15;
	}
}
