package net.tabor.seedcity.client;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.tabor.seedcity.SeedCity;
import net.tabor.seedcity.entity.CollectorEntity;

public final class CollectorRenderer extends MobRenderer<CollectorEntity,CollectorRenderState,CollectorModel> {
    public static final ModelLayerLocation LAYER=new ModelLayerLocation(SeedCity.id("collector"),"main");
    private final ItemModelResolver items;
    public CollectorRenderer(EntityRendererProvider.Context context) {
        super(context,new CollectorModel(context.bakeLayer(LAYER)),.45F);items=context.getItemModelResolver();
        addLayer(new ItemInHandLayer<>(this));
        addLayer(new EyesLayer<CollectorRenderState,CollectorModel>(this) {
            @Override public RenderType renderType() { return RenderTypes.eyes(SeedCity.id("textures/entity/collector_glow.png")); }
        });
    }
    @Override public Identifier getTextureLocation(CollectorRenderState state) { return SeedCity.id("textures/entity/collector.png"); }
    @Override public CollectorRenderState createRenderState() { return new CollectorRenderState(); }
    @Override public void extractRenderState(CollectorEntity entity,CollectorRenderState state,float partialTick) {
        super.extractRenderState(entity,state,partialTick);
        CollectorLanternMotion.extract(entity,state,partialTick);
        ArmedEntityRenderState.extractArmedEntityRenderState(entity,state,items,partialTick);
        ShowcaseAnimation.apply(entity,state);
    }
}
