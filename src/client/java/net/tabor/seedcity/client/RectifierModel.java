package net.tabor.seedcity.client;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;

/** Heavy repair guardian; head follows the mob's existing look control. */
public final class RectifierModel extends EntityModel<RectifierRenderState> {
    private final ModelPart body, head, rightArm, leftArm, rightLeg, leftLeg, lantern, hammer, forearm, toolForearm, cape, tabard;

    public RectifierModel(ModelPart root) {
        super(root);
        body=root.getChild("body"); cape=body.getChild("cape"); head=body.getChild("head");
        tabard=body.getChild("tabard");
        rightArm=body.getChild("right_arm"); leftArm=body.getChild("left_arm");
        rightLeg=body.getChild("right_leg"); leftLeg=body.getChild("left_leg");
        forearm=rightArm.getChild("right_forearm"); lantern=forearm.getChild("lantern"); toolForearm=leftArm.getChild("left_forearm"); hammer=toolForearm.getChild("hammer");
    }

    @Override
    public void setupAnim(RectifierRenderState state) {
        super.setupAnim(state);
        float t=state.ageInTicks, bob=(float)Math.sin(t*.075F);
        float flight=Math.min(state.flightSpeed*5,1);
        body.y+=bob*.14F; body.xRot=flight*.035F;
        head.yRot=state.yRot*((float)Math.PI/180F);
        head.xRot=Math.max(-.45F,Math.min(.6F,state.xRot*((float)Math.PI/180F)));
        rightArm.xRot=-.22F+bob*.015F; forearm.xRot=-.65F; rightArm.zRot=.035F;
        leftArm.xRot=-.15F; toolForearm.xRot=-.70F; leftArm.zRot=-.035F;
        rightLeg.xRot=.035F+flight*.1F+bob*.02F;
        leftLeg.xRot=.035F+flight*.1F-bob*.02F;
        if(state.grounded) {
            float stride=(float)Math.sin(state.walkAnimationPos*.6662F)
                    *Math.min(state.walkAnimationSpeed*1.8F,1)*.4F;
            rightLeg.xRot=stride; leftLeg.xRot=-stride; body.xRot=0;
            body.y=20-23*(float)Math.cos(stride)-4.5F*Math.abs((float)Math.sin(stride));
            rightArm.xRot=-.22F-stride*.10F; leftArm.xRot=stride*.45F;
        }
        if(state.repairing) {
            float p=(t%6F)/6F;
            float sweep=-(float)Math.sin(Math.sqrt(p)*Math.PI*2)*.12F;
            float stroke=(float)Math.sin((1-Math.pow(1-p,4))*Math.PI);
            body.yRot=sweep;
            head.yRot-=sweep;
            leftArm.xRot=-.15F-stroke*1.05F-(float)Math.sin(p*Math.PI)*.3F;
            leftArm.yRot=sweep*2;
            leftArm.zRot=-.035F+(float)Math.sin(p*Math.PI)*.25F;
            head.xRot=Math.max(head.xRot,.2F);
        }
        lantern.xRot=-rightArm.xRot-forearm.xRot+(float)Math.sin(t*.09F)*.04F;
        // Vanilla cloak motion with reduced lift for this heavy embroidered cloth.
        cape.xRot=(float)Math.toRadians(Math.max(1,Math.min(18,3+state.capeLean*.15F+state.capeFlap*.3F)));
        cape.zRot=(float)Math.toRadians(state.capeSide*.25F);
        cape.yRot=(float)Math.toRadians(-state.capeSide*.25F);
        // Waist-mounted cloth has its own soft sway, including a little idle motion.
        tabard.xRot=-(float)Math.toRadians(4+state.capeLean*.04F+Math.abs(state.capeFlap)*.12F)
                +(float)Math.sin(t*.12F)*.025F;
        tabard.zRot=(float)Math.toRadians(state.capeSide*.2F)+(float)Math.sin(t*.09F)*.045F;
        hammer.zRot=0;
        hammer.xRot=(float)Math.PI/2;
    }
}
