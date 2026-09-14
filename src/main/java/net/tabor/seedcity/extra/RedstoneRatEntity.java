package net.tabor.seedcity.extra;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The Redstone Rat (Mcdrizzy, PR #1): a small scavenger that lives around a city and keeps
 * creepers away from it. It is a Cat underneath on purpose: vanilla creepers already flee cats,
 * so the deterrent needs no hook into the creeper; the mixin only makes it stronger. No taming,
 * no breeding, no sitting: the rat is scenery with one job.
 */
public final class RedstoneRatEntity extends Cat {
	public RedstoneRatEntity(EntityType<? extends RedstoneRatEntity> type, Level level) {
		super(type, level);
		setPersistenceRequired();
		ambientSoundTime = -2400;
	}

	@Override
	public int getAmbientSoundInterval() {
		return 2400;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.SILVERFISH_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.SILVERFISH_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.SILVERFISH_DEATH;
	}

	@Override
	protected float getSoundVolume() {
		return 0.25F;
	}

	@Override
	public EntityDimensions getDefaultDimensions(Pose pose) {
		return EntityDimensions.scalable(0.55F, 0.3F).scale(isBaby() ? 0.5F : 1F);
	}

	@Override
	public boolean canMate(Animal other) {
		return false;
	}

	@Override
	public Cat getBreedOffspring(ServerLevel level, AgeableMob partner) {
		return null;
	}

	@Override
	protected void registerGoals() {
		// deliberately not the cat's goals: no sitting, following, tempting or bed-stealing
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(4, new MoveTowardsRestrictionGoal(this, 1));
		goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
		goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 6));
		goalSelector.addGoal(8, new RandomLookAroundGoal(this));
	}

	@Override
	public int getMaxHeadXRot() {
		return 65;
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		return InteractionResult.PASS;
	}

	@Override
	public boolean isFood(ItemStack stack) {
		return false;
	}
}
