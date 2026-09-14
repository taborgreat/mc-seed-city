package net.tabor.seedcity.mixin;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Level;
import net.tabor.seedcity.extra.AvoidRedstoneRatGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gives every creeper a high-priority flight from Redstone Rats (Mcdrizzy, PR #1). The one mixin
 * in the mod: there is no Fabric event for a vanilla mob's goal list.
 */
@Mixin(Creeper.class)
public abstract class CreeperRatAvoidanceMixin extends PathfinderMob {
	protected CreeperRatAvoidanceMixin(EntityType<? extends PathfinderMob> type, Level level) {
		super(type, level);
	}

	@Inject(method = "registerGoals", at = @At("TAIL"))
	private void seedcity$avoidRat(CallbackInfo ci) {
		goalSelector.addGoal(1, new AvoidRedstoneRatGoal((Creeper) (Object) this));
	}
}
