package net.tabor.seedcity.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.tabor.seedcity.core.CityManager;
import net.tabor.seedcity.core.CityState;

import java.util.Optional;

/**
 * A city organ that hovers over the frontier: knows which city it belongs to, flies, never
 * despawns, and never takes fall damage. Builders and Wardens share this; the Sentinel walks.
 */
public abstract class FlyingCityMob extends PathfinderMob {
	protected BlockPos cityPos;

	protected FlyingCityMob(EntityType<? extends FlyingCityMob> type, Level level) {
		super(type, level);
		this.moveControl = new FlyingMoveControl<>(this, 20, true);
		setNoGravity(true);
		setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createFlyingAttributes(double health) {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, health)
				.add(Attributes.FLYING_SPEED, 0.3)
				.add(Attributes.MOVEMENT_SPEED, 0.3);
	}

	@Override
	protected PathNavigation createNavigation(Level level) {
		FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
		nav.setCanOpenDoors(false);
		nav.setCanFloat(true);
		return nav;
	}

	@Override
	public void travel(Vec3 input) {
		travelFlying(input, getSpeed());
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}

	@Override
	protected void checkFallDamage(double ya, boolean onGround, BlockState onState, BlockPos pos) {
	}

	/** A cell grows around the mob that builds it; being inside a wall for a moment must not hurt. */
	@Override
	public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
		return source.is(DamageTypes.IN_WALL) || super.isInvulnerableTo(level, source);
	}

	/**
	 * The hover spot for working on {@code pos}: two blocks above it, raised until both the feet
	 * and the head block are clear, but never above {@code top}. Keeps a tall mob out of the
	 * roof it placed a moment ago.
	 */
	protected Vec3 hoverAbove(BlockPos pos, int top) {
		int y = pos.getY() + 2;
		while (y < top && (level().getBlockState(new BlockPos(pos.getX(), y, pos.getZ())).canOcclude()
				|| level().getBlockState(new BlockPos(pos.getX(), y + 1, pos.getZ())).canOcclude())) {
			y++;
		}
		return Vec3.atCenterOf(new BlockPos(pos.getX(), y, pos.getZ()));
	}

	public BlockPos cityPos() {
		return cityPos;
	}

	public void setCity(BlockPos pos) {
		this.cityPos = pos;
	}

	protected Optional<CityState> city(ServerLevel level) {
		return cityPos == null ? Optional.empty() : CityManager.get(level).city(cityPos);
	}

	/** Re-issues navigation toward the target every few ticks; true when within {@code reach}. */
	protected boolean flyToward(Vec3 target, double speed, double reach) {
		if (position().distanceTo(target) < reach) {
			getNavigation().stop();
			return true;
		}
		if (tickCount % 10 == 0) {
			getNavigation().moveTo(target.x, target.y, target.z, speed);
		}
		return false;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		if (cityPos != null) {
			output.store("City", BlockPos.CODEC, cityPos);
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		cityPos = input.read("City", BlockPos.CODEC).orElse(null);
	}
}
