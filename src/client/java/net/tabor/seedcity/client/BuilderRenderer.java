package net.tabor.seedcity.client;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.tabor.seedcity.SeedCity;
import net.tabor.seedcity.entity.BuilderEntity;

/** Custom Builder mesh with selectively emissive eyes, chest rune and schematic. */
public final class BuilderRenderer extends MobRenderer<BuilderEntity, BuilderRenderState, BuilderModel> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(SeedCity.id("builder"), "main");
    private static final Identifier TEXTURE = SeedCity.id("textures/entity/builder.png");
    private static final Identifier GLOW = SeedCity.id("textures/entity/builder_glow.png");

    public BuilderRenderer(EntityRendererProvider.Context context) {
        super(context, new BuilderModel(context.bakeLayer(LAYER)), 0.45F);
        addLayer(new EyesLayer<BuilderRenderState, BuilderModel>(this) {
            @Override
            public RenderType renderType() { return RenderTypes.eyes(GLOW); }
        });
    }

    @Override
    public Identifier getTextureLocation(BuilderRenderState state) { return TEXTURE; }

    @Override
    public BuilderRenderState createRenderState() { return new BuilderRenderState(); }

    @Override
    public void extractRenderState(BuilderEntity entity, BuilderRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.building = entity.isBuilding();
        state.carrying = entity.isCarryingMaterials();
        state.flightSpeed = (float) entity.getDeltaMovement().length();
        state.grounded = entity.onGround();
        ShowcaseAnimation.apply(entity,state);
    }
}
