package net.tabor.seedcity.core;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The Card Reader (design doc 13): the one place a program enters the city. Right-click with a
 * written book or a book and quill to insert it as a card; right-click with an empty hand to
 * eject the card, which returns the city to its default.
 */
public final class CardReaderBlock extends Block {
	public static final MapCodec<CardReaderBlock> CODEC = simpleCodec(CardReaderBlock::new);

	public CardReaderBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	/** The text of a book item, pages joined by newlines; empty when the item is not a book. */
	public static Optional<String> bookText(ItemStack stack) {
		WrittenBookContent written = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
		if (written != null) {
			List<String> pages = new ArrayList<>();
			for (Filterable<Component> p : written.pages()) {
				pages.add(p.raw().getString());
			}
			return Optional.of(String.join("\n", pages));
		}
		WritableBookContent writable = stack.get(DataComponents.WRITABLE_BOOK_CONTENT);
		if (writable != null) {
			List<String> pages = new ArrayList<>();
			for (Filterable<String> p : writable.pages()) {
				pages.add(p.raw());
			}
			return Optional.of(String.join("\n", pages));
		}
		return Optional.empty();
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		Optional<String> text = bookText(stack);
		if (text.isEmpty()) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (!(level instanceof ServerLevel server)) {
			return InteractionResult.SUCCESS;
		}
		Optional<CityState> city = CityManager.get(server).nearest(pos);
		if (city.isEmpty()) {
			player.sendOverlayMessage(Component.literal("This reader belongs to no city."));
			return InteractionResult.FAIL;
		}
		ItemStack card = stack.copyWithCount(1);
		CityState.CardResult result = city.get().insertCard(server, text.get(), card);
		if (result.accepted()) {
			stack.shrink(1);
			ItemStack previous = result.ejected();
			if (!previous.isEmpty() && !player.getInventory().add(previous)) {
				player.drop(previous, false);
			}
		}
		player.sendSystemMessage(Component.literal(result.message()));
		CityManager.get(server).touch();
		return InteractionResult.SUCCESS_SERVER;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!(level instanceof ServerLevel server)) {
			return InteractionResult.SUCCESS;
		}
		Optional<CityState> city = CityManager.get(server).nearest(pos);
		if (city.isEmpty()) {
			return InteractionResult.PASS;
		}
		ItemStack card = city.get().ejectCard(server);
		if (card.isEmpty()) {
			player.sendSystemMessage(Component.literal("No card in the reader. " + city.get().programSummary()));
			return InteractionResult.CONSUME;
		}
		if (!player.getInventory().add(card)) {
			player.drop(card, false);
		}
		player.sendSystemMessage(Component.literal("Card ejected; the city runs its default."));
		CityManager.get(server).touch();
		return InteractionResult.SUCCESS_SERVER;
	}
}
