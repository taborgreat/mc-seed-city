package net.tabor.seedcity.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.tabor.seedcity.SeedCity;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The placed-by-player flag (design doc 20.1, 24): positions where a player put a block, kept
 * per level. Builders will not plan over them and Collectors will not mine them, so a city can
 * never eat a base. A position is recorded when a player uses a block item on the world and
 * forgotten when the block there is broken; a stale mark on air counts for nothing.
 */
public final class PlayerBlocks extends SavedData {
	public static final Codec<PlayerBlocks> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.listOf().fieldOf("placed").forGetter(p -> List.copyOf(p.placed))
	).apply(i, PlayerBlocks::new));
	public static final SavedDataType<PlayerBlocks> TYPE = new SavedDataType<>(SeedCity.id("player_blocks"), PlayerBlocks::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	private final Set<Long> placed = new HashSet<>();

	public PlayerBlocks() {
	}

	private PlayerBlocks(List<Long> loaded) {
		placed.addAll(loaded);
	}

	public static PlayerBlocks get(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(TYPE);
	}

	public static void init() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level instanceof ServerLevel server && player.getItemInHand(hand).getItem() instanceof BlockItem) {
				BlockPos clicked = hit.getBlockPos();
				BlockPos target = server.getBlockState(clicked).canBeReplaced() ? clicked : clicked.relative(hit.getDirection());
				get(server).mark(target);
			}
			return InteractionResult.PASS;
		});
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (level instanceof ServerLevel server) {
				get(server).unmark(pos);
			}
		});
	}

	public void mark(BlockPos pos) {
		if (placed.add(pos.asLong())) {
			setDirty();
		}
	}

	public void unmark(BlockPos pos) {
		if (placed.remove(pos.asLong())) {
			setDirty();
		}
	}

	/** True for a block a player placed and that is still standing. */
	public static boolean placedByPlayer(ServerLevel level, BlockPos pos) {
		PlayerBlocks pb = get(level);
		if (pb.placed.isEmpty() || !pb.placed.contains(pos.asLong())) {
			return false;
		}
		BlockState s = level.getBlockState(pos);
		if (s.isAir()) {
			pb.unmark(pos);
			return false;
		}
		return true;
	}

	public int size() {
		return placed.size();
	}
}
