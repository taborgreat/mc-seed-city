package net.tabor.seedcity.entity;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.world.entity.PathfinderMob;

/** Unbound exhibit workers can roam the demo courtyard without manufacturing city tasks. */
public final class ShowcaseRoaming {
    private ShowcaseRoaming() {}
    public static void init() {
        ServerTickEvents.END_LEVEL_TICK.register(level -> {
            if (level.getGameTime() % 60 != 0) return;
            for (var entity : level.getAllEntities()) {
                if (!(entity instanceof PathfinderMob mob) || !entity.entityTags().contains("seedcity_showcase_roam")) continue;
                long seed = entity.getUUID().getLeastSignificantBits() ^ (level.getGameTime()/60);
                var random = new java.util.Random(seed);
                double x = 5 + random.nextInt(24), z = 6 + random.nextInt(22);
                double y = entity instanceof BuilderEntity ? 66 + random.nextDouble()*2 : 65;
                mob.getNavigation().moveTo(x,y,z,entity instanceof WardenEntity ? WardenEntity.WALK_SPEED : .8);
            }
        });
    }
}
