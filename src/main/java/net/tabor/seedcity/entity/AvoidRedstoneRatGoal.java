package net.tabor.seedcity.entity;

import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.monster.Creeper;

/** Rat-specific avoidance outranks ordinary chasing and automatic swelling. */
public final class AvoidRedstoneRatGoal extends AvoidEntityGoal<RedstoneRatEntity> {
    private final Creeper creeper;
    public AvoidRedstoneRatGoal(Creeper creeper) {
        super(creeper, RedstoneRatEntity.class, 10.0F, 1.0, 1.2);
        this.creeper=creeper;
    }
    @Override public void start() {
        super.start();
        if (!creeper.isIgnited()) creeper.setSwellDir(-1);
    }
    @Override public void tick() {
        super.tick();
        if (!creeper.isIgnited()) creeper.setSwellDir(-1);
    }
}
