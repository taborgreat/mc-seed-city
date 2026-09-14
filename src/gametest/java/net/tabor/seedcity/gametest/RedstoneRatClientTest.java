package net.tabor.seedcity.gametest;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.tabor.seedcity.entity.SeedCityEntities;
public final class RedstoneRatClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try(var world=context.worldBuilder().create()) {
            var server=world.getServer();server.runCommand("time set noon");server.runCommand("weather clear");
            server.runCommand("fill -10 63 -10 10 64 10 minecraft:smooth_stone");
            server.runCommand("fill -10 65 -10 10 72 10 minecraft:air");
            server.runCommand("gamemode creative @a");server.runCommand("tp @p -1.5 65 -3 -26.565 22");
            server.runCommand("summon seedcity:redstone_rat 0 65 0 {NoAI:1b,Rotation:[180f,0f]}");
            world.getConnection().waitForClientboundEntityUpdates(SeedCityEntities.REDSTONE_RAT);
            context.runOnClient(mc->{if(!mc.gui.hud.isHidden())mc.gui.hud.toggle();mc.options.fov().set(40);});
            world.getConnection().waitForChunksRender();context.waitTicks(15);
            context.takeScreenshot(TestScreenshotOptions.of("redstone-rat").withSize(1280,900));
        }
    }
}
