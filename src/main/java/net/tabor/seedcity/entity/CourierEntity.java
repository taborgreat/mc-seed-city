package net.tabor.seedcity.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.tabor.seedcity.SeedCity;
import net.tabor.seedcity.cell.Placement;
import net.tabor.seedcity.core.CityState;

import java.util.Optional;

/**
 * The Courier (design doc 6, 17, 24): the physical form of OUT and IN across districts. Picks a
 * value up at the Core, flies to the port and drives it, or reads a port and flies the value back.
 * Latency is flight time; if it never arrives, the value never arrives.
 */
public final class CourierEntity extends FlyingCityMob {
	private enum Phase { IDLE, TO_TARGET, RETURN }

	private Phase phase = Phase.IDLE;
	private CityState.Mail mail;
	private Vec3 target;
	private int travelTicks;
	private int carried = -1;

	public CourierEntity(EntityType<? extends CourierEntity> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createFlyingAttributes(12.0).add(net.minecraft.world.entity.ai.attributes.Attributes.FLYING_SPEED, 0.45);
	}

	public boolean idle() {
		return phase == Phase.IDLE;
	}

	@Override
	public int getMaxHeadXRot() {
		return 55;
	}

	@Override
	public int getMaxHeadYRot() {
		return 35;
	}

	public String status() {
		return "courier " + phase + (mail == null ? "" : " " + (mail.read() ? "reading " : "delivering " + mail.value() + " to ") + mail.slot() + "." + mail.port());
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		try {
			work(level);
		} catch (Exception e) {
			SeedCity.LOGGER.error("Courier {} failed while {}", getUUID(), phase, e);
			finish(level, -1);
		}
	}

	private void work(ServerLevel level) {
		Optional<CityState> city = city(level);
		if (city.isEmpty()) {
			return;
		}
		CityState c = city.get();
		switch (phase) {
			case IDLE -> {
				if (cityPos != null) {
					flyToward(Vec3.atCenterOf(cityPos.above(5)), 1.0, 2.5);
				}
				if (tickCount % 10 != 0) {
					return;
				}
				Optional<CityState.Mail> next = c.takeMail();
				if (next.isEmpty()) {
					return;
				}
				mail = next.get();
				Optional<Placement.WorldPort> wp = c.worldPort(mail.slot(), mail.port());
				if (wp.isEmpty()) {
					finish(level, -1);
					return;
				}
				target = Vec3.atCenterOf(wp.get().outside().above(1));
				travelTicks = 0;
				carried = mail.read() ? -1 : mail.value();
				setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.PAPER));
				phase = Phase.TO_TARGET;
			}
			case TO_TARGET -> {
				travelTicks++;
				if (flyToward(target, c.cfg().builderSpeed, 2.5)) {
					if (mail.read()) {
						carried = Math.max(0, c.readPort(mail.slot(), mail.port()));
					} else {
						c.driveTerminal(level, mail.slot(), mail.port(), mail.value());
					}
					target = Vec3.atCenterOf(c.seedPos().above(5));
					travelTicks = 0;
					phase = Phase.RETURN;
				} else if (travelTicks > c.cfg().abandonSeconds * 20) {
					SeedCity.LOGGER.warn("Courier could not reach {}; the value does not arrive", mail.slot());
					finish(level, -1);
				}
			}
			case RETURN -> {
				travelTicks++;
				if (flyToward(target, c.cfg().builderSpeed, 3.0) || travelTicks > c.cfg().abandonSeconds * 20) {
					finish(level, carried);
				}
			}
		}
	}

	private void finish(ServerLevel level, int value) {
		if (mail != null && mail.read()) {
			city(level).ifPresent(c -> c.deliverInbound(mail.ticket(), value));
		}
		mail = null;
		carried = -1;
		setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		phase = Phase.IDLE;
	}
}
