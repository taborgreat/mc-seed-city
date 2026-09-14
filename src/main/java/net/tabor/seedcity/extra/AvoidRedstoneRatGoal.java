package net.tabor.seedcity.extra;

import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.monster.Creeper;

/**
 * A creeper's flight from a rat (Mcdrizzy, PR #1). Vanilla creepers flee cats from six blocks
 * at a low priority; this starts at ten, outranks chasing, and unwinds a swell that had already
 * begun. A creeper lit by flint and steel still goes off.
 */
public final class AvoidRedstoneRatGoal extends AvoidEntityGoal<RedstoneRatEntity> {
	private final Creeper creeper;

	public AvoidRedstoneRatGoal(Creeper creeper) {
		super(creeper, RedstoneRatEntity.class, 10.0F, 1.0, 1.2);
		this.creeper = creeper;
	}

	@Override
	public void start() {
		super.start();
		if (!creeper.isIgnited()) {
			creeper.setSwellDir(-1);
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (!creeper.isIgnited()) {
			creeper.setSwellDir(-1);
		}
	}
}
