package net.tabor.seedcity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;

import net.minecraft.world.entity.HumanoidArm;

public final class CollectorModel extends EntityModel<CollectorRenderState> implements ArmedModel<CollectorRenderState> {
    private final ModelPart body,torso,head,rightArm,leftArm,rightLeg,leftLeg,lantern;
    public CollectorModel(ModelPart root) {
        super(root); body=root.getChild("body");torso=body.getChild("torso");head=torso.getChild("head");lantern=torso.getChild("lantern");
        rightArm=torso.getChild("right_arm");leftArm=torso.getChild("left_arm");
        rightLeg=body.getChild("right_leg");leftLeg=body.getChild("left_leg");
    }
    @Override public void setupAnim(CollectorRenderState state) {
        super.setupAnim(state); body.xScale=body.yScale=body.zScale=.8F;
        lantern.xRot=state.lanternPitch;lantern.zRot=state.lanternRoll;
        float gait=(float)Math.cos(state.walkAnimationPos*.6662F)*state.walkAnimationSpeed;
        rightLeg.xRot=gait*1.4F;leftLeg.xRot=-gait*1.4F;rightArm.xRot=-gait;leftArm.xRot=gait;
        head.yRot=Math.max(-.65F,Math.min(.65F,state.yRot*(float)Math.PI/180));
        head.xRot=Math.max(-.35F,Math.min(.45F,state.xRot*(float)Math.PI/180));
        if(state.attackTime>0) {
            // Minecraft HumanoidModel attack curve: wind-up, cross-body strike, follow-through.
            float p=state.attackTime;
            float twist=(float)Math.sin(Math.sqrt(p)*Math.PI*2)*.2F;
            boolean left=state.attackArm==HumanoidArm.LEFT;
            if(left) twist=-twist;
            torso.yRot=twist; head.yRot-=twist;
            leftArm.xRot+=twist;
            ModelPart arm=left?leftArm:rightArm;
            float eased=1-(float)Math.pow(1-p,4);
            arm.xRot-=(float)Math.sin(eased*Math.PI)*1.2F
                    +(float)Math.sin(p*Math.PI)*(.7F-head.xRot)*.75F;
            arm.yRot+=twist*2;
            arm.zRot-=(float)Math.sin(p*Math.PI)*.4F;
        }
    }
    @Override public void translateToHand(CollectorRenderState state,HumanoidArm side,PoseStack stack) {
        body.translateAndRotate(stack);
        torso.translateAndRotate(stack);
        (side==HumanoidArm.RIGHT?rightArm:leftArm).translateAndRotate(stack);
        // Vanilla item layer anchors ten model units below the arm pivot.
        stack.translate(side==HumanoidArm.RIGHT?1F/16:-1F/16,1.5F/16,0);
    }
}
