package net.tabor.seedcity.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.tabor.seedcity.client.BuilderMesh;
import net.tabor.seedcity.client.BuilderModel;
import net.tabor.seedcity.client.BuilderRenderState;
import net.tabor.seedcity.entity.SeedCityEntities;

/** Explicit opt-in client check: gradlew runClientGameTest. Needs a graphics device. */
public final class BuilderClientTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(mc -> {
            var root=BuilderMesh.createLayer().bakeRoot();
            var model=new BuilderModel(root);
            var state=new BuilderRenderState();
            state.ageInTicks=12;
            state.building=true;
            state.carrying=true;
            model.setupAnim(state);
            var arm=root.getChild("body").getChild("right_arm");
            if (!arm.getChild("cargo").visible || arm.xRot>=0) throw new AssertionError("Builder work pose failed");
            state.carrying=false;
            state.building=false;
            model.setupAnim(state);
            if (arm.getChild("cargo").visible) throw new AssertionError("Idle Builder retained cargo");
            state.grounded=true;
            state.walkAnimationSpeed=0.6F;
            state.walkAnimationPos=2.0F;
            model.setupAnim(state);
            var body=root.getChild("body");
            if (body.getChild("right_leg").xRot * body.getChild("left_leg").xRot >= 0)
                throw new AssertionError("Walking legs must alternate");
            state.walkAnimationSpeed=0;
            model.setupAnim(state);
            if (body.y!=10 || body.getChild("right_leg").xRot!=0)
                throw new AssertionError("Grounded rest pose must plant its feet");
        });
        try (var world=context.worldBuilder().create()) {
            var server=world.getServer();
            server.runCommand("time set noon");
            server.runCommand("weather clear");
            server.runCommand("fill -12 63 -12 12 64 12 minecraft:smooth_stone");
            server.runCommand("fill -12 65 -12 12 75 12 minecraft:air");
            server.runCommand("gamemode creative @a");
            server.runCommand("tp @p -2 65 -4 -26.565 10");
            server.runCommand("summon seedcity:builder 0 65 0 {NoAI:1b,Rotation:[180f,0f]}");
            world.getConnection().waitForClientboundEntityUpdates(SeedCityEntities.BUILDER);
            context.runOnClient(mc -> {
                mc.gui.hud.toggle();
                mc.options.fov().set(40);
            });
            world.getConnection().waitForChunksRender();
            context.waitTicks(10);
            context.takeScreenshot(TestScreenshotOptions.of("builder-day").withSize(1280,900));
            server.runCommand("time set midnight");
            context.waitTicks(10);
            context.takeScreenshot(TestScreenshotOptions.of("builder-night").withSize(1280,900));
        }
    }
}
