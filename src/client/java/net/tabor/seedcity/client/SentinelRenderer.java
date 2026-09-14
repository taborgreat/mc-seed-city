package net.tabor.seedcity.client;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.tabor.seedcity.SeedCity;
import net.tabor.seedcity.entity.SentinelEntity;
public final class SentinelRenderer extends MobRenderer<SentinelEntity,SentinelRenderState,SentinelModel> {
    public static final ModelLayerLocation LAYER=new ModelLayerLocation(SeedCity.id("sentinel"),"main");
    private static final Identifier TEXTURE=SeedCity.id("textures/entity/sentinel.png");
    private static final Identifier[] GLOW=new Identifier[16];
    static {for(int i=0;i<16;i++)GLOW[i]=SeedCity.id("textures/entity/sentinel_glow_"+i+".png");}
    public SentinelRenderer(EntityRendererProvider.Context context) {
        super(context,new SentinelModel(context.bakeLayer(LAYER)),.6F);
        addLayer(new EyesLayer<SentinelRenderState,SentinelModel>(this) {
            @Override public RenderType renderType(){return RenderTypes.eyes(GLOW[15]);}
            @Override public void submit(PoseStack pose,SubmitNodeCollector collector,int light,SentinelRenderState state,float yaw,float pitch) {
                collector.order(1).submitModel(getParentModel(),state,pose,RenderTypes.eyes(GLOW[Math.clamp(state.alertness,0,15)]),light,OverlayTexture.NO_OVERLAY,state.outlineColor,null);
            }
        });
    }
    @Override public Identifier getTextureLocation(SentinelRenderState state){return TEXTURE;}
    @Override public SentinelRenderState createRenderState(){return new SentinelRenderState();}
    @Override public void extractRenderState(SentinelEntity entity,SentinelRenderState state,float partialTicks) {
        super.extractRenderState(entity,state,partialTicks);
        state.alertness=entity.alertness();state.attackTime=entity.getAttackAnim(partialTicks);
        state.jogging=entity.isSprinting();
        ShowcaseAnimation.apply(entity,state);
    }
}
