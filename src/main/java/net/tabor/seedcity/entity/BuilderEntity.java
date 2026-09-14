package net.tabor.seedcity.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import net.tabor.seedcity.SeedCity;
import net.tabor.seedcity.SeedCityItems;
import net.tabor.seedcity.build.BuildTask;
import net.tabor.seedcity.cell.Cell;
import net.tabor.seedcity.cell.CellLibrary;
import net.tabor.seedcity.cell.Placement;
import net.tabor.seedcity.config.SeedCityConfig;
import net.tabor.seedcity.core.CityManager;
import net.tabor.seedcity.core.CityState;
import net.tabor.seedcity.verify.Verifier;

import java.util.Optional;

/**
 * The Builder (design doc 6, 24): lives on the frontier, takes the next BuildTask, fetches
 * material, flies to the site, places blocks at a fixed rate with sound, then hands the finished
 * cell to the city and moves on. It never decides what to build; the city does.
 */
public final class BuilderEntity extends FlyingCityMob {
	private enum Phase { IDLE, TO_STORAGE, WITHDRAW, TO_SITE, BUILD }

	private BuildTask task;
	private Phase phase = Phase.IDLE;
	private int timer;
	private int travelTicks;
	private Vec3 target;

	public BuilderEntity(EntityType<? extends BuilderEntity> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createFlyingAttributes(20.0);
	}

	public String status() {
		return phase + (task == null ? "" : " " + task.placement() + " " + (int) (task.progress() * 100) + "%");
	}

	/**
	 * Design doc 6.1: a player hands a Builder a blueprint and it builds that cell where the player
	 * stands, through the same task and verifier the city uses.
	 */
	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		Optional<String> cellName = SeedCityItems.blueprintCell(stack);
		if (cellName.isEmpty()) {
			return super.mobInteract(player, hand);
		}
		if (!(level() instanceof ServerLevel server)) {
			return InteractionResult.SUCCESS;
		}
		Optional<Cell> cell = CellLibrary.get(SeedCity.id(cellName.get()));
		if (cell.isEmpty()) {
			player.sendSystemMessage(Component.literal("No such cell: " + cellName.get()));
			return InteractionResult.FAIL;
		}
		if (task != null) {
			player.sendSystemMessage(Component.literal("This builder is busy (" + status() + ")."));
			return InteractionResult.CONSUME;
		}
		BlockPos feet = player.blockPosition();
		Placement p = new Placement(cell.get(), feet.offset(-3, -1, -3), Rotation.NONE);
		String result = acceptBlueprint(server, p);
		if (result == null) {
			stack.shrink(1);
			player.sendSystemMessage(Component.literal("Builder took the " + cellName.get() + " blueprint; building around you."));
			return InteractionResult.SUCCESS_SERVER;
		}
		player.sendSystemMessage(Component.literal(result));
		return InteractionResult.CONSUME;
	}

	/** Takes a free-standing build. Returns null on success, otherwise why not. */
	public String acceptBlueprint(ServerLevel level, Placement p) {
		if (task != null) {
			return "busy";
		}
		Optional<CityState> city = city(level);
		if (city.isEmpty()) {
			return "this builder belongs to no city";
		}
		if (!city.get().buildableAt(level, p)) {
			return "no room here: the floor must be solid and the space above clear";
		}
		task = new BuildTask(p, null);
		begin(Phase.TO_SITE, Vec3.atCenterOf(task.nextPos().above(2)));
		return null;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		try {
			work(level);
		} catch (Exception e) {
			SeedCity.LOGGER.error("Builder {} failed while {}; re-queuing", getUUID(), phase, e);
			dropTask(level);
		}
	}

	private void work(ServerLevel level) {
		SeedCityConfig cfg = city(level).map(CityState::cfg).orElse(SeedCityConfig.get());
		switch (phase) {
			case IDLE -> {
				if (++timer < 20) {
					if (cityPos != null) {
						flyToward(Vec3.atCenterOf(cityPos.above(5)), 1.0, 3.0);
					}
					return;
				}
				timer = 0;
				Optional<CityState> city = city(level);
				if (city.isEmpty() || city.get().frozen()) {
					return;
				}
				Optional<BuildTask> next = city.get().claimTask(level, getUUID());
				if (next.isEmpty()) {
					return;
				}
				task = next.get();
				CityManager.get(level).touch();
				if (cfg.unlimitedMaterials) {
					begin(Phase.TO_SITE, Vec3.atCenterOf(task.nextPos().above(2)));
				} else {
					begin(Phase.TO_STORAGE, Vec3.atCenterOf(city.get().storageTarget(blockPosition())));
				}
			}
			case TO_STORAGE -> {
				if (travel(cfg)) {
					phase = Phase.WITHDRAW;
					timer = 20;
				}
			}
			case WITHDRAW -> {
				if (--timer <= 0) {
					begin(Phase.TO_SITE, Vec3.atCenterOf(task.nextPos().above(2)));
				}
			}
			case TO_SITE -> {
				if (travel(cfg)) {
					phase = Phase.BUILD;
					timer = 0;
				}
			}
			case BUILD -> build(level, cfg);
		}
	}

	private void begin(Phase next, Vec3 to) {
		phase = next;
		target = to;
		travelTicks = 0;
		timer = 0;
	}

	/** Flies toward the target; true when close enough. Gives up after the configured time. */
	private boolean travel(SeedCityConfig cfg) {
		travelTicks++;
		if (flyToward(target, cfg.builderSpeed, 2.5)) {
			return true;
		}
		if (travelTicks > cfg.abandonSeconds * 20) {
			dropTask((ServerLevel) level());
		}
		return false;
	}

	private void build(ServerLevel level, SeedCityConfig cfg) {
		BlockPos next = task.nextPos();
		Vec3 stand = Vec3.atCenterOf(next).add(0, 2, 0);
		if (position().distanceTo(stand) > 4.0) {
			flyToward(stand, cfg.builderSpeed, 4.0);
		} else {
			getNavigation().stop();
		}
		getLookControl().setLookAt(next.getX() + 0.5, next.getY() + 0.5, next.getZ() + 0.5);
		if (++timer < cfg.ticksPerBlock()) {
			return;
		}
		timer = 0;
		if (task.step(level)) {
			finish(level);
		}
	}

	/** Hands the completed cell to the city, which queues it for verification, and goes back to the frontier. */
	private void finish(ServerLevel level) {
		BuildTask done = task;
		task = null;
		if (done.slot() == null) {
			// a player blueprint: verify on the spot, off the city grid
			Optional<CityState> c = city(level);
			c.ifPresent(city -> city.noteBlueprintResult("verifying " + done.placement()));
			Verifier.verify(level, done.placement(), java.util.List.of(), result -> {
				SeedCity.LOGGER.info("Blueprint {}: {}", done.placement(), result);
				c.ifPresent(city -> city.noteBlueprintResult(result.toString()));
			});
		} else {
			city(level).ifPresent(c -> c.onBuildComplete(done));
		}
		CityManager.get(level).touch();
		phase = Phase.IDLE;
		timer = 0;
	}

	private void dropTask(ServerLevel level) {
		if (task != null) {
			BuildTask t = task;
			task = null;
			city(level).ifPresent(c -> c.abandon(t));
		}
		getNavigation().stop();
		phase = Phase.IDLE;
		timer = 0;
	}
}
