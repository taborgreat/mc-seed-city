package net.tabor.seedcity.client;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
/** Grounded statue, scanning, walking and short polearm strikes. */
public final class SentinelModel extends EntityModel<SentinelRenderState> {
    private final ModelPart torso,head,rightArm,leftArm,rightLeg,leftLeg,spear,forearm;
    public SentinelModel(ModelPart root) {
        super(root);
        var body=root.getChild("body");torso=body.getChild("torso");
        head=torso.getChild("head");rightArm=torso.getChild("right_arm");leftArm=torso.getChild("left_arm");
        forearm=leftArm.getChild("left_forearm");spear=forearm.getChild("spear");
        rightLeg=body.getChild("right_leg");leftLeg=body.getChild("left_leg");
    }
    @Override public void setupAnim(SentinelRenderState state) {
        super.setupAnim(state);
        forearm.xRot=-1.15F;spear.xRot=1.15F;
        if(state.alertness==0) {head.xRot=.32F;return;}
        float weight=Math.clamp(state.walkAnimationSpeed*2,0,1);
        float gait=(float)Math.cos(state.walkAnimationPos*(state.jogging?.60F:.48F))*weight;
        float stride=state.jogging?.42F:.28F;
        rightLeg.xRot=gait*stride;leftLeg.xRot=-gait*stride;
        rightArm.xRot=-gait*(state.jogging?.27F:.16F);leftArm.xRot=gait*.035F;
        spear.xRot=-(leftArm.xRot+forearm.xRot);
        float range=.12F+state.alertness/15F*.53F;
        head.yRot=Math.clamp(state.yRot*(float)Math.PI/180,-range,range);
        head.xRot=Math.clamp(state.xRot*(float)Math.PI/180,-.45F,.45F);
        if(state.attackTime>0) {
            float p=state.attackTime;
            float ready=smooth(0,.25F,p)*(1-smooth(.75F,1,p));
            float thrust=smooth(.25F,.42F,p)*(1-smooth(.55F,.85F,p));
            leftArm.xRot=-.5F*ready-.45F*thrust;
            forearm.xRot=-1.15F+.6F*thrust;
            // Keep the spear level throughout the jab; the elbow supplies extension.
            spear.xRot=ready*(float)Math.PI/2-leftArm.xRot-forearm.xRot;
            torso.yRot=-thrust*.06F;head.yRot+=thrust*.06F;
        }
    }
    private static float smooth(float a,float b,float value) {
        float t=Math.clamp((value-a)/(b-a),0,1);return t*t*(3-2*t);
    }
}
