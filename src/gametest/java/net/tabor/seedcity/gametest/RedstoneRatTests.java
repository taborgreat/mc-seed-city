package net.tabor.seedcity.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.tabor.seedcity.extra.RedstoneRatEntity;
import net.tabor.seedcity.extra.RedstoneRats;

/**
 * The Redstone Rat extra (Mcdrizzy, PR #1): a creeper flees a rat even with a player in reach
 * and stops swelling; city spawning honours the cap and the off switch.
 */
public final class RedstoneRatTests {
	@GameTest(structure = "seedcity:boat", maxTicks = 180)
	public void creeperFleesRat(GameTestHelper h) {
		for (int x = 1; x < 35; x++) {
			for (int z = 1; z < 35; z++) {
				h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
				for (int y = 2; y < 5; y++) {
					h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
		RedstoneRatEntity rat = h.spawn(RedstoneRats.TYPE, new BlockPos(17, 2, 17));
		rat.setNoAi(true);
		Creeper creeper = h.spawn(EntityTypes.CREEPER, new BlockPos(20, 2, 17));
		var player = h.makeMockPlayer(GameType.SURVIVAL);
		player.setPos(creeper.getX() + 2, creeper.getY(), creeper.getZ());
		creeper.setTarget(player);
		creeper.setSwellDir(1);
		h.runAtTickTime(20, () -> {
			h.assertTrue(creeper.isAlive(), "Rat must interrupt natural swelling near a player");
			h.assertTrue(creeper.getSwellDir() < 0, "Rat avoidance must outrank the swelling goal");
		});
		h.assertTrue(rat.getAmbientSoundInterval() == 2400, "Rat ambience must wait about two minutes");
		h.assertFalse(rat.isFood(new ItemStack(Items.COD)), "Rat must not accept taming food");
		h.succeedWhen(() -> {
			h.assertTrue(h.getTick() > 20, "Wait for fuse-priority check");
			h.assertTrue(creeper.isAlive() && creeper.distanceToSqr(rat) > 100, "Creeper must flee the rat beyond ten blocks even with a player target");
		});
	}

	@GameTest(structure = "seedcity:boat", maxTicks = 40)
	public void spawningHonorsCapAndDisable(GameTestHelper h) {
		BlockPos seed = h.absolutePos(new BlockPos(20, 2, 20));
		for (int x = 0; x < 42; x++) {
			for (int z = 0; z < 42; z++) {
				h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
			}
		}
		// the test loads its area explicitly; production spawning never loads chunks
		for (int x = (seed.getX() - RedstoneRats.RANGE) >> 4; x <= (seed.getX() + RedstoneRats.RANGE) >> 4; x++) {
			for (int z = (seed.getZ() - RedstoneRats.RANGE) >> 4; z <= (seed.getZ() + RedstoneRats.RANGE) >> 4; z++) {
				h.getLevel().getChunk(x, z);
			}
		}
		h.assertFalse(RedstoneRats.trySpawn(h.getLevel(), seed, 0), "Zero cap disables spawning");
		h.assertTrue(RedstoneRats.trySpawn(h.getLevel(), seed, 1), "Safe city floor should spawn a rat");
		h.assertFalse(RedstoneRats.trySpawn(h.getLevel(), seed, 1), "Local cap must prevent another rat");
		h.succeed();
	}
}
