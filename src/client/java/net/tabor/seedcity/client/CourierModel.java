package net.tabor.seedcity.client;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public final class CourierModel extends EntityModel<LivingEntityRenderState> {
    private final ModelPart body,head,rightLeg,leftLeg,rightArm,leftArm;
    public CourierModel(ModelPart root) {
        super(root);
        body=root.getChild("body"); head=body.getChild("head");
        rightLeg=body.getChild("right_leg"); leftLeg=body.getChild("left_leg");
        rightArm=body.getChild("right_arm"); leftArm=body.getChild("left_arm");
    }
    @Override
    public void setupAnim(LivingEntityRenderState state) {
        super.setupAnim(state);
        body.xScale=body.yScale=body.zScale=.45F;
        float amount=Math.min(state.walkAnimationSpeed*4,1);
        float stride=(float)Math.sin(state.walkAnimationPos*1.8F)*.5F*amount;
        body.y-=Math.abs((float)Math.sin(state.walkAnimationPos*1.8F))*.10F*amount;
        rightLeg.xRot=stride; leftLeg.xRot=-stride;
        rightArm.xRot=-stride*.5F; leftArm.xRot=stride*.5F;
        head.yRot=Math.max(-.61F,Math.min(.61F,state.yRot*(float)Math.PI/180));
        head.xRot=Math.max(-.96F,Math.min(.35F,state.xRot*(float)Math.PI/180));
    }
}
