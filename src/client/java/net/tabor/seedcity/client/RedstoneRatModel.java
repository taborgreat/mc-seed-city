package net.tabor.seedcity.client;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
public final class RedstoneRatModel extends EntityModel<LivingEntityRenderState> {
    private final ModelPart head;
    private final ModelPart[] paws=new ModelPart[4];
    private final ModelPart[] segments=new ModelPart[7],tail=new ModelPart[3];
    public RedstoneRatModel(ModelPart root) {
        super(root);var body=root.getChild("body");head=body.getChild("head");
        for(int i=0;i<7;i++)segments[i]=(i<2?head:body).getChild("segment_"+i);
        int index=0;for(String side:new String[]{"left","right"})for(String end:new String[]{"front","back"})paws[index++]=body.getChild("paw_"+side+"_"+end);
        for(int i=0;i<3;i++)tail[i]=body.getChild("tail_"+i);
    }
    @Override public void setupAnim(LivingEntityRenderState state) {
        super.setupAnim(state);
        float strength=.08F+Math.clamp(state.walkAnimationSpeed*3,0,.92F);
        float phase=state.walkAnimationPos*1.2F+state.ageInTicks*.08F;
        float moving=Math.clamp(state.walkAnimationSpeed*3,0,1);
        head.yRot=Math.clamp(state.yRot*(float)Math.PI/180,-.45F,.45F);
        head.xRot=Math.clamp(state.xRot*(float)Math.PI/180,-1.13F,.25F);
        // The complete head follows the body wave while preserving independent player tracking.
        head.yRot+=(float)Math.cos(phase)*.055F*moving;
        head.x+=(float)Math.sin(phase)*.10F*moving;
        head.xRot+=(float)Math.sin(phase*2)*.025F*moving;
        for(int i=2;i<7;i++) {
            segments[i].yRot=(float)Math.cos(phase-i*.65F)*.07F*(1+Math.abs(i-2)*.35F)*strength;
            segments[i].x+=(float)Math.sin(phase-i*.65F)*.14F*strength;
        }
        for(int i=0;i<4;i++) {
            double pawPhase=phase*2+(i==0||i==3?0:Math.PI);
            float step=(float)Math.sin(pawPhase);
            float lift=Math.max(0,(-(float)Math.cos(pawPhase)-.3F)/.7F);
            // Moderate fore/aft steps with a planted stance between brief recovery lifts.
            paws[i].z+=step*.36F*moving;
            paws[i].y-=lift*.18F*moving;
        }
        for(int i=0;i<3;i++) {
            tail[i].x+=(float)Math.sin(phase-4.5F-i*.5F)*(.30F+i*.18F)*strength;
            tail[i].yRot=(float)Math.cos(phase-4.5F-i*.5F)*.20F*strength;
        }
    }
}
