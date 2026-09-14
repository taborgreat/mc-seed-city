package net.tabor.seedcity.core;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * The Reader wall (design doc 13): the city's console. A floating text display inside the Core
 * chamber showing the program state, its registers, and any card error with its line number.
 */
public final class ReaderWall {
	private ReaderWall() {
	}

	private static String tag(BlockPos seed) {
		return "seedcity_reader_" + seed.getX() + "_" + seed.getY() + "_" + seed.getZ();
	}

	public static void update(ServerLevel level, BlockPos seed, List<String> lines) {
		String tag = tag(seed);
		AABB box = new AABB(seed).inflate(4.0);
		List<Display.TextDisplay> found = level.getEntities(EntityTypes.TEXT_DISPLAY, box, d -> d.entityTags().contains(tag));
		Display.TextDisplay display;
		if (found.isEmpty()) {
			display = EntityTypes.TEXT_DISPLAY.spawn(level, seed.above(1), EntitySpawnReason.MOB_SUMMONED);
			if (display == null) {
				return;
			}
			display.setPos(seed.getX() + 0.5, seed.getY() + 1.9, seed.getZ() - 1.3);
			display.addTag(tag);
			display.setBillboardConstraints(Display.BillboardConstraints.CENTER);
			display.setLineWidth(240);
		} else {
			display = found.getFirst();
		}
		display.setText(Component.literal(String.join("\n", lines)));
	}

	public static void remove(ServerLevel level, BlockPos seed) {
		String tag = tag(seed);
		for (Display.TextDisplay d : level.getEntities(EntityTypes.TEXT_DISPLAY, new AABB(seed).inflate(4.0), d -> d.entityTags().contains(tag))) {
			d.discard();
		}
	}
}
