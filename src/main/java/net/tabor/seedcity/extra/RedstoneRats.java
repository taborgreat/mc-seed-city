package net.tabor.seedcity.extra;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.phys.AABB;
import net.tabor.seedcity.SeedCity;

/**
 * Everything the Redstone Rat needs to exist, in one place, outside the five city mobs of the
 * design doc: its entity type and the city-local population rule. The city calls
 * {@link #trySpawn} from upkeep; nothing here reads or writes city state, and the rat never
 * changes a block. {@code maxRedstoneRats = 0} in the config turns it off.
 */
public final class RedstoneRats {
	public static final int PERIOD_TICKS = 1200;
	public static final int RANGE = 24;
	public static EntityType<RedstoneRatEntity> TYPE;

	private RedstoneRats() {
	}

	public static void init() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, SeedCity.id("redstone_rat"));
		TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
				EntityType.Builder.of(RedstoneRatEntity::new, MobCategory.CREATURE).sized(0.55F, 0.3F).clientTrackingRange(8).build(key));
		FabricDefaultAttributeRegistry.register(TYPE, Cat.createAttributes());
	}

	/**
	 * Spawns one rat near the seed if fewer than {@code cap} live within range. The search order
	 * is fixed so a city's rats appear in the same places each time. Never loads chunks; true
	 * when a rat was placed.
	 */
	public static boolean trySpawn(ServerLevel level, BlockPos seed, int cap) {
		cap = Math.clamp(cap, 0, 8);
		if (cap == 0 || TYPE == null) {
			return false;
		}
		for (int x = (seed.getX() - RANGE) >> 4; x <= (seed.getX() + RANGE) >> 4; x++) {
			for (int z = (seed.getZ() - RANGE) >> 4; z <= (seed.getZ() + RANGE) >> 4; z++) {
				if (!level.hasChunk(x, z)) {
					return false;
				}
			}
		}
		AABB area = new AABB(seed).inflate(RANGE);
		if (level.getEntities(TYPE, area, RedstoneRatEntity::isAlive).size() >= cap) {
			return false;
		}
		for (int radius : new int[] {6, 10, 14}) {
			for (int[] offset : new int[][] {{radius, 0}, {0, radius}, {-radius, 0}, {0, -radius}}) {
				for (int dy = 6; dy >= -6; dy--) {
					BlockPos pos = seed.offset(offset[0], dy, offset[1]);
					if (!level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)
							|| !level.getFluidState(pos).isEmpty()) {
						continue;
					}
					AABB bounds = new AABB(pos.getX() + 0.225, pos.getY(), pos.getZ() + 0.225,
							pos.getX() + 0.775, pos.getY() + 0.3, pos.getZ() + 0.775);
					if (!level.noCollision(bounds)) {
						continue;
					}
					RedstoneRatEntity rat = TYPE.spawn(level, pos, EntitySpawnReason.EVENT);
					if (rat != null) {
						rat.setHomeTo(seed, RANGE);
						return true;
					}
				}
			}
		}
		return false;
	}
}
