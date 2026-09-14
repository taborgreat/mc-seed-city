package net.tabor.seedcity.client;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.tabor.seedcity.SeedCity;
import net.tabor.seedcity.extra.RedstoneRatEntity;

public final class RedstoneRatRenderer extends MobRenderer<RedstoneRatEntity,LivingEntityRenderState,RedstoneRatModel> {
    public static final ModelLayerLocation LAYER=new ModelLayerLocation(SeedCity.id("redstone_rat"),"main");
    private static final Identifier TEXTURE=SeedCity.id("textures/entity/redstone_rat.png");
    public RedstoneRatRenderer(EntityRendererProvider.Context context) {
        super(context,new RedstoneRatModel(context.bakeLayer(LAYER)),.2F);
        addLayer(new EyesLayer<LivingEntityRenderState,RedstoneRatModel>(this) {
            @Override public RenderType renderType() { return RenderTypes.eyes(SeedCity.id("textures/entity/redstone_rat_glow.png")); }
        });
    }
    @Override public Identifier getTextureLocation(LivingEntityRenderState state) { return TEXTURE; }
    @Override public LivingEntityRenderState createRenderState() { return new LivingEntityRenderState(); }
    @Override public void extractRenderState(RedstoneRatEntity entity, LivingEntityRenderState state, float partialTicks) {
        super.extractRenderState(entity,state,partialTicks);
        ShowcaseAnimation.apply(entity,state);
    }
}
