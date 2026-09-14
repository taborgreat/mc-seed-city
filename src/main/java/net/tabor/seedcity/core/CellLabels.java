package net.tabor.seedcity.core;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.tabor.seedcity.cell.Placement;
import net.tabor.seedcity.cell.PortDir;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Debug labels: a floating text over every built cell naming it the way cards do
 * (drawbridge.2), its district, whether the clock reaches it, and the live value on each of
 * its ports. Toggled per city with {@code /seedcity labels}; refreshed once a second. For
 * learning to read the machine, not for the finished look.
 */
public final class CellLabels {
	private CellLabels() {
	}

	private static String tag(BlockPos seed) {
		return "seedcity_label_" + seed.getX() + "_" + seed.getY() + "_" + seed.getZ();
	}

	public static void update(ServerLevel level, CityState city) {
		String tag = tag(city.seedPos());
		Set<CityState.SlotKey> clocked = city.clockedSlots(true);
		for (CityState.Slot s : city.slots()) {
			if (s.status != CityState.SlotStatus.BUILT && s.status != CityState.SlotStatus.FAULT) {
				continue;
			}
			Optional<Placement> p = city.placement(s);
			if (p.isEmpty()) {
				continue;
			}
			BoundingBox box = p.get().footprint();
			BlockPos centre = box.getCenter();
			String slotTag = tag + "_" + s.key.x() + "_" + s.key.z();
			List<Display.TextDisplay> found = level.getEntities(EntityTypes.TEXT_DISPLAY, new AABB(centre).inflate(4.0), d -> d.entityTags().contains(slotTag));
			Display.TextDisplay display;
			if (found.isEmpty()) {
				display = EntityTypes.TEXT_DISPLAY.spawn(level, centre, EntitySpawnReason.MOB_SUMMONED);
				if (display == null) {
					continue;
				}
				display.setPos(centre.getX() + 0.5, box.maxY() + 1.6, centre.getZ() + 0.5);
				display.addTag(tag);
				display.addTag(slotTag);
				display.setBillboardConstraints(Display.BillboardConstraints.CENTER);
				display.setLineWidth(200);
			} else {
				display = found.getFirst();
			}
			StringBuilder text = new StringBuilder();
			text.append(s.cell.getPath()).append('.').append(s.ordinal).append('\n');
			text.append(city.district(s.key)).append(" · ");
			text.append(s.status == CityState.SlotStatus.FAULT ? "FAULT" : clocked.contains(s.key) ? "clocked" : "quiet");
			StringBuilder ports = new StringBuilder();
			for (Placement.WorldPort wp : p.get().ports()) {
				int v = wp.port().dir() == PortDir.OUT
						? level.getSignal(wp.pos(), wp.face().getOpposite())
						: level.getSignal(wp.pos(), wp.face());
				ports.append(ports.isEmpty() ? "" : "  ").append(wp.port().dir() == PortDir.OUT ? "out " : "in ").append(wp.port().name()).append('=').append(v);
			}
			if (!ports.isEmpty()) {
				text.append('\n').append(ports);
			}
			display.setText(Component.literal(text.toString()));
		}
	}

	public static void remove(ServerLevel level, CityState city) {
		String tag = tag(city.seedPos());
		AABB box = new AABB(city.seedPos()).inflate(city.cfg().maxRadiusSlots * CityState.SLOT + 16.0);
		for (Display.TextDisplay d : level.getEntities(EntityTypes.TEXT_DISPLAY, box, d -> d.entityTags().contains(tag))) {
			d.discard();
		}
	}
}
