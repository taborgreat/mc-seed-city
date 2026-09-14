package net.tabor.seedcity.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** Small ground messenger. Port binding/delivery belongs to the future Phase 4 task layer. */
public final class CourierEntity extends PathfinderMob {
    public CourierEntity(EntityType<? extends CourierEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 12)
                .add(Attributes.MOVEMENT_SPEED, .32).add(Attributes.FOLLOW_RANGE, 24);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new CourierNavigation(this, level);
    }

    @Override
    public int getMaxHeadXRot() { return 55; }

    @Override
    public int getMaxHeadYRot() { return 35; }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 5, .12F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }
}
