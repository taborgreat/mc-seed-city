package net.tabor.seedcity.client;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;

/** Explicit exhibit names opt into looping poses; normal NPCs are never overridden. */
public final class ShowcaseAnimation {
    private ShowcaseAnimation() {}
    public static void apply(LivingEntity entity, LivingEntityRenderState state) {
        if (entity.getCustomName() == null) return;
        String name = entity.getCustomName().getString();
        if (!name.startsWith("SC:")) return;
        String pose = name.substring(3);
        float t = state.ageInTicks;
        boolean walk = pose.equals("walk") || pose.equals("run");
        state.walkAnimationPos = t * (pose.equals("run") ? .85F : .55F);
        state.walkAnimationSpeed = walk ? .65F : 0;
        state.yRot = pose.equals("look") ? (float)Math.sin(t * .035F) * 25 : 0;
        state.xRot = pose.equals("look") ? -50 + (float)Math.sin(t*.04F)*12 : 0;
        if (state instanceof BuilderRenderState b) {
            b.building = pose.equals("build"); b.carrying = pose.equals("carry");
            b.grounded = !pose.equals("fly"); b.flightSpeed = b.grounded ? 0 : .18F;
        }
        if (state instanceof RectifierRenderState r) {
            r.repairing = pose.equals("repair"); r.grounded = !pose.equals("fly");
            r.flightSpeed = r.grounded ? 0 : .18F;
            r.capeFlap = .04F + (walk ? .07F : .02F) * (float)Math.sin(t*.16F);
            r.capeSide = .025F * (float)Math.sin(t*.1F);
        }
        if (state instanceof CollectorRenderState c) {
            c.attackTime = pose.equals("mine") || pose.equals("chop") ? (t % 6)/6 : 0;
            c.lanternPitch = (walk ? .15F : .03F) * (float)Math.sin(t*.22F);
            c.lanternRoll = (walk ? .09F : .015F) * (float)Math.sin(t*.17F);
        }
        if (state instanceof SentinelRenderState s) {
            s.alertness = pose.equals("sleep") ? 0 : pose.equals("alert") ? 7 : 15;
            s.jogging = pose.equals("run"); s.attackTime = pose.equals("attack") ? (t%20)/20 : 0;
        }
    }
}
