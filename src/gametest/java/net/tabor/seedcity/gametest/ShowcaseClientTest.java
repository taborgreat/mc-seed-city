package net.tabor.seedcity.gametest;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.tabor.seedcity.entity.SeedCityEntities;
public final class ShowcaseClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try(var world=context.worldBuilder().create()) {
            var server=world.getServer();
            server.runCommand("function seedcity:showcase/build");
            world.getConnection().waitForClientboundEntityUpdates(SeedCityEntities.REDSTONE_RAT);
            server.runCommand("tp @p 35 89 -24 0 40");
            server.runCommand("gamemode spectator @a");
            context.runOnClient(mc->{if(!mc.gui.hud.isHidden())mc.gui.hud.toggle();mc.options.fov().set(75);});
            world.getConnection().waitForChunksRender();context.waitTicks(60);
            context.takeScreenshot(TestScreenshotOptions.of("mob-showcase-v1").withSize(1600,1000));
            server.runCommand("tp @p 54 70 -3 0 25");
            world.getConnection().waitForChunksRender();context.waitTicks(20);
            context.takeScreenshot(TestScreenshotOptions.of("showcase-animations").withSize(1600,1000));
            server.runCommand("tp @p 16 73 35 0 28");
            world.getConnection().waitForChunksRender();context.waitTicks(20);
            context.takeScreenshot(TestScreenshotOptions.of("showcase-creeper-cage").withSize(1600,1000));
            server.runCommand("gamemode creative @a");server.runCommand("tp @a 36 65 0 0 0");
        }
    }
}
