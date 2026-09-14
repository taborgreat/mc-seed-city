package net.tabor.seedcity.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.tabor.seedcity.client.RectifierMesh;
import net.tabor.seedcity.client.RectifierModel;
import net.tabor.seedcity.client.RectifierRenderState;
import net.tabor.seedcity.entity.SeedCityEntities;

/** Explicit opt-in client check: gradlew runClientGameTest. Needs a graphics device. */
public final class RectifierClientTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(mc -> {
            var root=RectifierMesh.createLayer().bakeRoot();
            var model=new RectifierModel(root);
            var state=new RectifierRenderState();
            state.ageInTicks=12;
            state.repairing=true;
            state.yRot=30;
            model.setupAnim(state);
            var body=root.getChild("body");
            float low=0,high=-10,side=0;
            for(int frame=0;frame<=24;frame++) {
                state.ageInTicks=frame*.25F;
                model.setupAnim(state);
                var arm=body.getChild("left_arm");
                low=Math.min(low,arm.xRot); high=Math.max(high,arm.xRot);
                side=Math.max(side,Math.abs(arm.yRot));
            }
            if(high-low<.8F || side<.1F) throw new AssertionError("Repair must have a full stroke, recovery and cross-body sweep");
            if(Math.abs(body.yRot+body.getChild("head").yRot-(float)Math.PI/6)>.001F)
                throw new AssertionError("Head must follow independent look direction");
            state.ageInTicks=12;
            state.repairing=false; state.grounded=true; state.walkAnimationSpeed=.6F; state.walkAnimationPos=2;
            model.setupAnim(state);
            if(body.getChild("right_leg").xRot*body.getChild("left_leg").xRot>=0)
                throw new AssertionError("Walking legs must alternate");
            state.capeLean=60; state.capeSide=12;
            model.setupAnim(state);
            float movingCape=body.getChild("cape").xRot;
            if(movingCape<=.1F || movingCape>Math.toRadians(18) || body.getChild("cape").zRot<=0)
                throw new AssertionError("Cape must lift and sway with movement state");
            state.capeLean=0; state.capeSide=0;
            state.walkAnimationSpeed=0;
            model.setupAnim(state);
            if(body.getChild("cape").xRot>=movingCape)
                throw new AssertionError("Cape must settle after motion ends");
            if(body.y!=-3) throw new AssertionError("Grounded boots must rest at floor level");
            float clothSway=body.getChild("tabard").zRot;
            state.ageInTicks+=20;
            model.setupAnim(state);
            if(Math.abs(body.getChild("tabard").zRot-clothSway)<.01F)
                throw new AssertionError("Front cloth must sway independently even at rest");
        });
        try (var world=context.worldBuilder().create()) {
            var server=world.getServer();
            server.runCommand("time set noon");
            server.runCommand("weather clear");
            server.runCommand("fill -12 63 -12 12 64 12 minecraft:smooth_stone");
            server.runCommand("fill -12 65 -12 12 75 12 minecraft:air");
            server.runCommand("gamemode creative @a");
            server.runCommand("tp @p -3 65 -6 -26.565 0");
            server.runCommand("summon seedcity:warden 0 65 0 {NoAI:1b,Rotation:[180f,0f]}");
            world.getConnection().waitForClientboundEntityUpdates(SeedCityEntities.WARDEN);
            context.runOnClient(mc -> {
                if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
                mc.options.fov().set(40);
            });
            world.getConnection().waitForChunksRender();
            context.waitTicks(10);
            context.takeScreenshot(TestScreenshotOptions.of("rectifier-day").withSize(1280,900));
            server.runCommand("tp @p 3 65 6 153.435 0");
            context.waitTicks(10);
            context.takeScreenshot(TestScreenshotOptions.of("rectifier-cape").withSize(1280,900));
            server.runCommand("tp @p -3 65 -6 -26.565 0");
            server.runCommand("time set midnight");
            context.waitTicks(10);
            context.takeScreenshot(TestScreenshotOptions.of("rectifier-night").withSize(1280,900));
        }
    }
}
