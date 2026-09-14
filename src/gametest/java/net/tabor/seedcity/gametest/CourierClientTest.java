package net.tabor.seedcity.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.tabor.seedcity.client.CourierMesh;
import net.tabor.seedcity.client.CourierModel;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.tabor.seedcity.entity.SeedCityEntities;

public final class CourierClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(mc->{
            var root=CourierMesh.createLayer().bakeRoot(); var model=new CourierModel(root);
            var state=new LivingEntityRenderState(); state.walkAnimationPos=1; state.walkAnimationSpeed=.5F;
            model.setupAnim(state); var body=root.getChild("body");
            if(body.getChild("right_leg").xRot*body.getChild("left_leg").xRot>=0)
                throw new AssertionError("Scurry must alternate feet");
            state.walkAnimationSpeed=0; model.setupAnim(state);
            if(body.y!=24 || body.xScale!=.45F) throw new AssertionError("Idle Courier must be tiny and grounded");
            state.xRot=-50; state.yRot=25; model.setupAnim(state);
            if(body.getChild("head").xRot>-.8F || body.getChild("head").yRot<.4F)
                throw new AssertionError("Courier must look up toward a nearby player");
        });
        try(var world=context.worldBuilder().create()) {
            var server=world.getServer();
            server.runCommand("time set noon"); server.runCommand("weather clear");
            server.runCommand("fill -8 63 -8 8 64 8 minecraft:smooth_stone");
            server.runCommand("fill -8 65 -8 8 70 8 minecraft:air");
            server.runCommand("setblock 0 65 1 minecraft:stone_slab[type=top]");
            server.runCommand("setblock -1 65 1 minecraft:stone");
            server.runCommand("setblock 1 65 1 minecraft:stone");
            server.runCommand("gamemode creative @a");
            server.runCommand("tp @p -1.6 64 -3 -28 8");
            server.runCommand("summon seedcity:courier 0 65 0 {NoAI:1b,Rotation:[180f,0f]}");
            world.getConnection().waitForClientboundEntityUpdates(SeedCityEntities.COURIER);
            context.runOnClient(mc->{if(!mc.gui.hud.isHidden()) mc.gui.hud.toggle();mc.options.fov().set(35);});
            world.getConnection().waitForChunksRender(); context.waitTicks(10);
            context.takeScreenshot(TestScreenshotOptions.of("courier-day").withSize(1280,900));
            // Normal standing player eye height, close enough to see the upward look.
            server.runCommand("tp @p 0 65 -1.6 0 42");
            server.runCommand("data merge entity @e[type=seedcity:courier,limit=1] {NoAI:0b}");
            context.runOnClient(mc->mc.options.fov().set(70));
            context.waitTicks(60);
            context.takeScreenshot(TestScreenshotOptions.of("courier-player-height").withSize(1280,900));
        }
    }
}
