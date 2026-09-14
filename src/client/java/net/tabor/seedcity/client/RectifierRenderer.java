package net.tabor.seedcity.client;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.tabor.seedcity.SeedCity;
import net.tabor.seedcity.entity.WardenEntity;

/** Rectifier appearance; the saved entity remains seedcity:warden. */
public final class RectifierRenderer extends MobRenderer<WardenEntity, RectifierRenderState, RectifierModel> {
    public static final ModelLayerLocation LAYER=new ModelLayerLocation(SeedCity.id("rectifier"),"main");
    private static final Identifier TEXTURE=SeedCity.id("textures/entity/rectifier.png");
    private static final Identifier GLOW=SeedCity.id("textures/entity/rectifier_glow.png");
    public RectifierRenderer(EntityRendererProvider.Context context) {
        super(context,new RectifierModel(context.bakeLayer(LAYER)),.7F);
        addLayer(new EyesLayer<RectifierRenderState,RectifierModel>(this) {
            @Override public RenderType renderType() { return RenderTypes.eyes(GLOW); }
        });
    }
    @Override public Identifier getTextureLocation(RectifierRenderState state) { return TEXTURE; }
    @Override public RectifierRenderState createRenderState() { return new RectifierRenderState(); }
    @Override public void extractRenderState(WardenEntity entity,RectifierRenderState state,float partialTicks) {
        super.extractRenderState(entity,state,partialTicks);
        state.repairing=entity.isRepairing(); state.grounded=entity.onGround();
        state.flightSpeed=(float)entity.getDeltaMovement().length();
        RectifierCapeMotion.extract(entity,state,partialTicks);
        ShowcaseAnimation.apply(entity,state);
    }
}
