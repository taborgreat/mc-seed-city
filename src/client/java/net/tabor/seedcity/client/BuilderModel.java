package net.tabor.seedcity.client;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;

/** Compact stone/copper worker. Flight remains the existing Builder navigation. */
public final class BuilderModel extends EntityModel<BuilderRenderState> {
    private final ModelPart body, head, rightArm, leftArm, rightLeg, leftLeg, cargo;

    public BuilderModel(ModelPart root) {
        super(root);
        body = root.getChild("body");
        head = body.getChild("head");
        rightArm = body.getChild("right_arm");
        leftArm = body.getChild("left_arm");
        rightLeg = body.getChild("right_leg");
        leftLeg = body.getChild("left_leg");
        cargo = rightArm.getChild("cargo");
    }

    @Override
    public void setupAnim(BuilderRenderState state) {
        super.setupAnim(state);
        float t = state.ageInTicks;
        float bob = (float) Math.sin(t * 0.10F);
        float flight = Math.min(state.flightSpeed * 5.0F, 1.0F);
        body.y += bob * 0.22F;
        body.xRot = flight * 0.08F;
        head.yRot = state.yRot * ((float) Math.PI / 180F);
        head.xRot = Math.max(-0.45F, Math.min(0.6F, state.xRot * ((float) Math.PI / 180F)));
        rightLeg.xRot = 0.08F + flight * 0.22F + bob * 0.04F;
        leftLeg.xRot = 0.08F + flight * 0.22F - bob * 0.04F;
        rightArm.zRot = 0.04F;
        leftArm.zRot = -0.05F;
        leftArm.xRot = 0.03F + bob * 0.035F;
        rightArm.xRot = state.carrying ? -0.32F : 0.03F + bob * 0.035F;
        cargo.visible = state.carrying;
        if (state.grounded) {
            float stride = (float) Math.sin(state.walkAnimationPos * 0.6662F)
                    * Math.min(state.walkAnimationSpeed * 1.8F, 1.0F) * 0.55F;
            rightLeg.xRot = stride;
            leftLeg.xRot = -stride;
            body.xRot = 0;
            // Keep the lowest boot corner on the floor, including the forward toe.
            body.y = 10 + 8 - 8 * (float) Math.cos(stride) - 3 * Math.abs((float) Math.sin(stride));
            if (!state.carrying) rightArm.xRot = -stride * 0.65F;
            leftArm.xRot = stride * 0.65F;
        }
        if (state.building) {
            float p = (t % 6F) / 6F;
            float sweep = (float)Math.sin(Math.sqrt(p) * Math.PI * 2) * .16F;
            float stroke = (float)Math.sin((1 - Math.pow(1-p, 4)) * Math.PI);
            body.yRot = sweep;
            head.yRot -= sweep;
            rightArm.xRot = -.12F - stroke * 1.2F
                    - (float)Math.sin(p*Math.PI) * (.7F-head.xRot) * .75F;
            rightArm.yRot = sweep * 2;
            rightArm.zRot = .04F - (float)Math.sin(p*Math.PI) * .4F;
            head.xRot = Math.max(head.xRot, 0.12F);
            leftArm.xRot = 0.03F;
        }
    }
}
