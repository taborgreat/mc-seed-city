package net.tabor.seedcity.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.level.Level;

/** Cat AI is intentional: vanilla creepers' Cat.class avoidance includes this mob. */
public final class RedstoneRatEntity extends Cat {
    public RedstoneRatEntity(EntityType<? extends RedstoneRatEntity> type,Level level) {
        super(type,level);setPersistenceRequired();ambientSoundTime=-2400;
    }
    @Override public int getAmbientSoundInterval() { return 2400; }
    @Override protected net.minecraft.sounds.SoundEvent getAmbientSound() { return net.minecraft.sounds.SoundEvents.SILVERFISH_AMBIENT; }
    @Override protected net.minecraft.sounds.SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource source) { return net.minecraft.sounds.SoundEvents.SILVERFISH_HURT; }
    @Override protected net.minecraft.sounds.SoundEvent getDeathSound() { return net.minecraft.sounds.SoundEvents.SILVERFISH_DEATH; }
    @Override protected float getSoundVolume() { return .25F; }
    @Override public EntityDimensions getDefaultDimensions(Pose pose) {
        return EntityDimensions.scalable(.55F,.3F).scale(isBaby()?.5F:1F);
    }
    @Override public boolean canMate(Animal other) {
        return false;
    }
    @Override public Cat getBreedOffspring(ServerLevel level,AgeableMob partner) {
        return null;
    }
    @Override protected void registerGoals() {
        goalSelector.addGoal(0,new net.minecraft.world.entity.ai.goal.FloatGoal(this));
        goalSelector.addGoal(4,new net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal(this,1));
        goalSelector.addGoal(6,new net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal(this,.8));
        goalSelector.addGoal(7,new net.minecraft.world.entity.ai.goal.LookAtPlayerGoal(this,net.minecraft.world.entity.player.Player.class,6));
        goalSelector.addGoal(8,new net.minecraft.world.entity.ai.goal.RandomLookAroundGoal(this));
    }
    @Override public int getMaxHeadXRot(){return 65;}
    @Override public net.minecraft.world.InteractionResult mobInteract(net.minecraft.world.entity.player.Player player,net.minecraft.world.InteractionHand hand) {
        return net.minecraft.world.InteractionResult.PASS;
    }
    @Override public boolean isFood(net.minecraft.world.item.ItemStack stack){return false;}
}
