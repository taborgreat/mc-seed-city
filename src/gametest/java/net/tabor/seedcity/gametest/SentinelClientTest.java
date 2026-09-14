package net.tabor.seedcity.gametest;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.tabor.seedcity.entity.SeedCityEntities;
import net.tabor.seedcity.client.*;
public final class SentinelClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(mc->{
            var root=SentinelMesh.createLayer().bakeRoot();var model=new SentinelModel(root);
            var state=new SentinelRenderState();state.walkAnimationSpeed=1;state.walkAnimationPos=2;state.attackTime=.5F;
            model.setupAnim(state);
            var body=root.getChild("body");var torso=body.getChild("torso");var arm=torso.getChild("left_arm");
            if(body.getChild("right_leg").xRot!=0||arm.xRot!=0)throw new AssertionError("Dormant statue must not walk or strike");
            state.alertness=15;model.setupAnim(state);
            var forearm=arm.getChild("left_forearm");var spear=forearm.getChild("spear");
            if(Math.abs(spear.xRot+arm.xRot+forearm.xRot-(float)Math.PI/2)>.01F)throw new AssertionError("Spear must be level during forward thrust");
            state.alertness=0;model.setupAnim(state);
            if(Math.abs(spear.xRot+forearm.xRot)>.01F)throw new AssertionError("Spear must return upright after sleeping");
            state.alertness=8;state.attackTime=0;
            for(int tick=0;tick<80;tick++) {
                state.walkAnimationPos=tick*.2F;model.setupAnim(state);
                if(Math.abs(spear.xRot+arm.xRot+forearm.xRot)>.01F)throw new AssertionError("Walking must keep the shaft upright");
            }
        });
        try(var world=context.worldBuilder().create()) {
            var server=world.getServer();
            server.runCommand("time set noon");server.runCommand("weather clear");
            server.runCommand("fill -10 63 -10 10 64 10 minecraft:smooth_stone");
            server.runCommand("fill -10 65 -10 10 73 10 minecraft:air");
            server.runCommand("gamemode creative @a");server.runCommand("tp @p -3 65 -6 -26.565 -7");
            server.runCommand("summon seedcity:sentinel 0 65 0 {NoAI:1b,Alertness:15,Rotation:[180f,0f]}");
            world.getConnection().waitForClientboundEntityUpdates(SeedCityEntities.SENTINEL);
            context.runOnClient(mc->{if(!mc.gui.hud.isHidden())mc.gui.hud.toggle();mc.options.fov().set(45);});
            world.getConnection().waitForChunksRender();context.waitTicks(15);
            context.runOnClient(mc->{
                var list=mc.level.getEntitiesOfClass(net.tabor.seedcity.entity.SentinelEntity.class,mc.player.getBoundingBox().inflate(15));
                if(list.isEmpty()||list.getFirst().alertness()!=15)throw new AssertionError("Alertness must synchronize to client");
            });
            context.takeScreenshot(TestScreenshotOptions.of("sentinel-hostile").withSize(1280,900));
            server.runCommand("data merge entity @e[type=seedcity:sentinel,limit=1] {Alertness:0}");
            context.waitTicks(15);
            context.runOnClient(mc->{
                var list=mc.level.getEntitiesOfClass(net.tabor.seedcity.entity.SentinelEntity.class,mc.player.getBoundingBox().inflate(15));
                if(list.getFirst().alertness()!=0)throw new AssertionError("Power-down must synchronize");
            });
            context.takeScreenshot(TestScreenshotOptions.of("sentinel-dormant").withSize(1280,900));
        }
    }
}
