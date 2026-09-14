package net.tabor.seedcity.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.tabor.seedcity.entity.SeedCityEntities;

public final class CollectorClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(mc->{
            var root=net.tabor.seedcity.client.CollectorMesh.createLayer().bakeRoot();
            var model=new net.tabor.seedcity.client.CollectorModel(root);
            var state=new net.tabor.seedcity.client.CollectorRenderState();
            state.attackArm=net.minecraft.world.entity.HumanoidArm.RIGHT;state.attackTime=.3F;
            model.setupAnim(state);
            var torso=root.getChild("body").getChild("torso");var arm=torso.getChild("right_arm");
            if(Math.abs(torso.yRot)<.01F||Math.abs(arm.yRot)<.02F||Math.abs(arm.zRot)<.1F)
                throw new AssertionError("Player-style strike must turn the torso and swing across multiple axes");
            state.attackTime=0;state.walkAnimationSpeed=0;model.setupAnim(state);
            if(torso.yRot!=0||arm.yRot!=0||arm.zRot!=0) throw new AssertionError("Swing must settle back to idle");
        });
        try(var world=context.worldBuilder().create()) {
            var server=world.getServer();
            server.runCommand("time set noon");server.runCommand("weather clear");
            server.runCommand("fill -10 63 -10 10 64 10 minecraft:smooth_stone");
            server.runCommand("fill -10 65 -10 10 73 10 minecraft:air");
            server.runCommand("gamemode creative @a");server.runCommand("tp @p -2.5 65 -5 -26.565 2");
            server.runCommand("summon seedcity:collector 0 65 0 {NoAI:1b,Rotation:[180f,0f]}");
            world.getConnection().waitForClientboundEntityUpdates(SeedCityEntities.COLLECTOR);
            context.runOnClient(mc->{if(!mc.gui.hud.isHidden())mc.gui.hud.toggle();mc.options.fov().set(40);});
            world.getConnection().waitForChunksRender();context.waitTicks(15);
            context.takeScreenshot(TestScreenshotOptions.of("collector-pickaxe").withSize(1280,900));
            server.runCommand("item replace entity @e[type=seedcity:collector,limit=1] weapon.mainhand with minecraft:iron_axe");
            context.waitTicks(10);
            context.takeScreenshot(TestScreenshotOptions.of("collector-axe").withSize(1280,900));
            server.runCommand("fill -7 65 -7 7 70 7 minecraft:stone hollow");
            server.runCommand("fill -6 65 -6 6 65 6 minecraft:air");
            server.runCommand("item replace entity @e[type=seedcity:collector,limit=1] weapon.mainhand with minecraft:iron_pickaxe");
            server.runCommand("time set midnight");
            context.waitTicks(30);
            context.takeScreenshot(TestScreenshotOptions.of("collector-cave-light").withSize(1280,900));
        }
    }
}
