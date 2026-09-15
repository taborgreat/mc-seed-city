package net.tabor.seedcity.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.tabor.seedcity.SeedCityBlocks;
import net.tabor.seedcity.cell.Cell;
import net.tabor.seedcity.cell.CellLibrary;
import net.tabor.seedcity.cell.Placement;
import net.tabor.seedcity.cell.Port;
import net.tabor.seedcity.config.SeedCityConfig;
import net.tabor.seedcity.core.CityManager;
import net.tabor.seedcity.core.CityState;
import net.tabor.seedcity.entity.BuilderEntity;
import net.tabor.seedcity.entity.SentinelEntity;
import net.tabor.seedcity.entity.WardenEntity;
import net.tabor.seedcity.verify.Verifier;
import net.tabor.seedcity.verify.VerifyResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Dev and admin commands:
 * <pre>
 *   /seedcity list
 *   /seedcity place &lt;cell&gt; [rotation 0-3]
 *   /seedcity verify &lt;cell&gt; [rotation 0-3] [keep]
 *   /seedcity verifyall
 *   /seedcity platform &lt;size&gt;        a stone boat under your feet, for testing
 *   /seedcity plant                     a powered Seed where you stand
 *   /seedcity city                      status of the nearest city and its builders
 *   /seedcity slots                     every slot of the nearest city
 * </pre>
 */
public final class SeedCityCommands {
	private static final DynamicCommandExceptionType UNKNOWN_CELL =
			new DynamicCommandExceptionType(id -> Component.literal("Unknown cell " + id));

	private SeedCityCommands() {
	}

	public static void init() {
		CommandRegistrationCallback.EVENT.register(SeedCityCommands::register);
	}

	private static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext ctx, Commands.CommandSelection selection) {
		dispatcher.register(Commands.literal("seedcity")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("list").executes(c -> list(c.getSource())))
				.then(Commands.literal("place")
						.then(Commands.argument("cell", IdentifierArgument.id())
								.suggests((c, b) -> SharedSuggestionProvider.suggestResource(CellLibrary.ids(), b))
								.executes(c -> place(c, Rotation.NONE))
								.then(Commands.argument("rotation", IntegerArgumentType.integer(0, 3))
										.executes(c -> place(c, rotation(c))))))
				.then(Commands.literal("verify")
						.then(Commands.argument("cell", IdentifierArgument.id())
								.suggests((c, b) -> SharedSuggestionProvider.suggestResource(CellLibrary.ids(), b))
								.executes(c -> verify(c, Rotation.NONE, false))
								.then(Commands.literal("keep").executes(c -> verify(c, Rotation.NONE, true)))
								.then(Commands.argument("rotation", IntegerArgumentType.integer(0, 3))
										.executes(c -> verify(c, rotation(c), false))
										.then(Commands.literal("keep").executes(c -> verify(c, rotation(c), true))))))
				.then(Commands.literal("verifyall").executes(c -> verifyAll(c.getSource())))
				.then(Commands.literal("platform")
						.then(Commands.argument("size", IntegerArgumentType.integer(7, 200))
								.executes(c -> platform(c.getSource(), IntegerArgumentType.getInteger(c, "size")))))
				.then(Commands.literal("plant").executes(c -> plant(c.getSource())))
				.then(Commands.literal("city").executes(c -> city(c.getSource())))
				.then(Commands.literal("slots").executes(c -> slots(c.getSource())))
				.then(Commands.literal("graph").executes(c -> graph(c.getSource())))
				.then(Commands.literal("card")
						.then(Commands.argument("name", com.mojang.brigadier.arguments.StringArgumentType.word())
								.suggests((c, b) -> SharedSuggestionProvider.suggest(net.tabor.seedcity.card.CardLibrary.names(), b))
								.executes(c -> card(c.getSource(), com.mojang.brigadier.arguments.StringArgumentType.getString(c, "name")))))
				.then(Commands.literal("insert")
						.then(Commands.argument("name", com.mojang.brigadier.arguments.StringArgumentType.word())
								.suggests((c, b) -> SharedSuggestionProvider.suggest(net.tabor.seedcity.card.CardLibrary.names(), b))
								.executes(c -> insert(c.getSource(), com.mojang.brigadier.arguments.StringArgumentType.getString(c, "name")))))
				.then(Commands.literal("eject").executes(c -> eject(c.getSource())))
				.then(Commands.literal("manual").executes(c -> manual(c.getSource())))
				.then(Commands.literal("dream").executes(c -> dream(c.getSource())))
				.then(Commands.literal("fit").executes(c -> fit(c.getSource()))
						.then(Commands.argument("cell", IdentifierArgument.id())
								.suggests((c, b) -> SharedSuggestionProvider.suggestResource(CellLibrary.ids(), b))
								.executes(c -> explain(c, Rotation.NONE))
								.then(Commands.argument("rotation", IntegerArgumentType.integer(0, 3))
										.executes(c -> explain(c, rotation(c))))))
				.then(Commands.literal("labels").executes(c -> labels(c.getSource())))
				.then(Commands.literal("reader").executes(c -> reader(c.getSource())))
				.then(Commands.literal("blueprint")
						.then(Commands.argument("cell", IdentifierArgument.id())
								.suggests((c, b) -> SharedSuggestionProvider.suggestResource(CellLibrary.ids(), b))
								.executes(c -> blueprint(c)))));
	}

	private static Rotation rotation(CommandContext<CommandSourceStack> c) {
		return Rotation.values()[IntegerArgumentType.getInteger(c, "rotation")];
	}

	private static Cell cell(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		Identifier id = IdentifierArgument.getId(c, "cell");
		return CellLibrary.get(id).orElseThrow(() -> UNKNOWN_CELL.create(id));
	}

	/** Cells go two blocks east of the caller so they never land on top of them. */
	private static BlockPos originFor(CommandSourceStack source) {
		return BlockPos.containing(source.getPosition()).offset(2, 0, 0);
	}

	private static int list(CommandSourceStack source) {
		List<Cell> cells = new ArrayList<>(CellLibrary.all());
		if (cells.isEmpty()) {
			source.sendSuccess(() -> Component.literal("No cells loaded."), false);
		}
		for (Cell cell : cells) {
			StringBuilder sb = new StringBuilder(cell.id().toString())
					.append("  ").append(cell.definition().kind().name().toLowerCase())
					.append("  ").append(cell.size().toShortString())
					.append("  truth=").append(cell.definition().truth())
					.append("  ports:");
			for (Port p : cell.definition().ports()) {
				sb.append(' ').append(p);
			}
			source.sendSuccess(() -> Component.literal(sb.toString()), false);
		}
		for (String err : CellLibrary.errors()) {
			source.sendFailure(Component.literal("rejected: " + err));
		}
		return cells.size();
	}

	private static int place(CommandContext<CommandSourceStack> c, Rotation rotation) throws CommandSyntaxException {
		Cell cell = cell(c);
		CommandSourceStack source = c.getSource();
		Placement p = new Placement(cell, originFor(source), rotation);
		if (!p.place(source.getLevel())) {
			source.sendFailure(Component.literal("Could not place " + cell.id()));
			return 0;
		}
		source.sendSuccess(() -> Component.literal("Placed " + p + "; ports: " + portSummary(p)), true);
		return 1;
	}

	private static String portSummary(Placement p) {
		StringBuilder sb = new StringBuilder();
		for (Placement.WorldPort wp : p.ports()) {
			sb.append(wp.port().name()).append('@').append(wp.pos().toShortString()).append(' ');
		}
		return sb.toString().trim();
	}

	private static int verify(CommandContext<CommandSourceStack> c, Rotation rotation, boolean keep) throws CommandSyntaxException {
		Cell cell = cell(c);
		CommandSourceStack source = c.getSource();
		ServerLevel level = source.getLevel();
		Placement p = new Placement(cell, originFor(source), rotation);
		if (!p.place(level)) {
			source.sendFailure(Component.literal("Could not place " + cell.id()));
			return 0;
		}
		source.sendSuccess(() -> Component.literal("Verifying " + p + " ..."), false);
		Verifier.verify(level, p, List.of(), result -> {
			report(source, result);
			if (!keep) {
				p.clear(level, false);
			}
		});
		return 1;
	}

	private static int verifyAll(CommandSourceStack source) {
		ServerLevel level = source.getLevel();
		List<Cell> cells = new ArrayList<>(CellLibrary.all());
		if (cells.isEmpty()) {
			source.sendFailure(Component.literal("No cells loaded."));
			return 0;
		}
		BlockPos base = originFor(source);
		List<VerifyResult> results = new ArrayList<>();
		int i = 0;
		for (Cell cell : cells) {
			Placement p = new Placement(cell, base.offset(i * 12, 0, 0), Rotation.NONE);
			i++;
			if (!p.place(level)) {
				results.add(VerifyResult.fail(cell.id(), "could not place"));
				continue;
			}
			Verifier.verify(level, p, List.of(), result -> {
				report(source, result);
				p.clear(level, false);
				results.add(result);
				if (results.size() == cells.size()) {
					long passed = results.stream().filter(VerifyResult::pass).count();
					source.sendSuccess(() -> Component.literal("verifyall: " + passed + "/" + results.size() + " passed"), true);
				}
			});
		}
		source.sendSuccess(() -> Component.literal("Verifying " + cells.size() + " cell(s) ..."), false);
		return cells.size();
	}

	private static void report(CommandSourceStack source, VerifyResult result) {
		if (result.pass()) {
			source.sendSuccess(() -> Component.literal(result.toString()), false);
		} else {
			source.sendFailure(Component.literal(result.toString()));
		}
	}

	/** Two layers of smooth stone centred under the caller: a city boat for testing. */
	private static int platform(CommandSourceStack source, int size) {
		ServerLevel level = source.getLevel();
		BlockPos feet = BlockPos.containing(source.getPosition());
		int half = size / 2;
		int placed = 0;
		for (int x = -half; x < size - half; x++) {
			for (int z = -half; z < size - half; z++) {
				for (int dy = 1; dy <= 2; dy++) {
					level.setBlock(feet.offset(x, -dy, z), Blocks.SMOOTH_STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
					placed++;
				}
			}
		}
		int n = placed;
		source.sendSuccess(() -> Component.literal("Platform " + size + "x" + size + " built (" + n + " blocks). Stand in the middle and /seedcity plant."), true);
		return 1;
	}

	/** Puts a Seed at the caller's feet with a redstone block beneath it, which roots a city. */
	private static int plant(CommandSourceStack source) {
		ServerLevel level = source.getLevel();
		BlockPos feet = BlockPos.containing(source.getPosition());
		level.setBlock(feet.below(), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(feet, SeedCityBlocks.SEED.defaultBlockState(), Block.UPDATE_ALL);
		Optional<CityState> city = CityManager.get(level).city(feet);
		if (city.isEmpty()) {
			source.sendFailure(Component.literal("Seed placed but no city rooted; is it powered?"));
			return 0;
		}
		source.sendSuccess(() -> Component.literal("Planted. " + city.get().summary()), true);
		return 1;
	}

	private static int city(CommandSourceStack source) {
		ServerLevel level = source.getLevel();
		Optional<CityState> city = CityManager.get(level).nearest(BlockPos.containing(source.getPosition()));
		if (city.isEmpty()) {
			source.sendFailure(Component.literal("No city in this dimension."));
			return 0;
		}
		CityState c = city.get();
		source.sendSuccess(() -> Component.literal(c.summary()), false);
		for (BuilderEntity b : c.builders(level)) {
			source.sendSuccess(() -> Component.literal("  builder " + b.blockPosition().toShortString() + ": " + b.status()), false);
		}
		for (WardenEntity w : c.wardens(level)) {
			source.sendSuccess(() -> Component.literal("  warden " + w.blockPosition().toShortString() + ": " + w.status()), false);
		}
		for (SentinelEntity s : c.sentinels(level)) {
			source.sendSuccess(() -> Component.literal("  " + s.status() + " at " + s.blockPosition().toShortString()), false);
		}
		for (net.tabor.seedcity.entity.CourierEntity k : c.couriers(level)) {
			source.sendSuccess(() -> Component.literal("  " + k.status() + " at " + k.blockPosition().toShortString()), false);
		}
		for (net.tabor.seedcity.entity.CollectorEntity k : c.collectors(level)) {
			source.sendSuccess(() -> Component.literal("  " + k.status() + " at " + k.blockPosition().toShortString()), false);
		}
		c.deficit().ifPresent(k -> source.sendSuccess(() -> Component.literal("  short of " + k.name + "; collectors are out for it"), false));
		if (!c.lastBlueprintResult().isEmpty()) {
			source.sendSuccess(() -> Component.literal("  last blueprint: " + c.lastBlueprintResult()), false);
		}
		source.sendSuccess(() -> Component.literal("  districts: " + c.districts() + ", mail pending " + c.pendingMail()), false);
		for (String d : c.districts()) {
			if (c.districtHasFault(d)) {
				source.sendSuccess(() -> Component.literal("  district " + d + " is DARK: open fault, no warden"), false);
			}
		}
		source.sendSuccess(() -> Component.literal("  verifier jobs active: " + Verifier.activeJobs()), false);
		return 1;
	}

	/**
	 * The city as a graph: every built cell is a node, every output-to-input mating is an edge.
	 * Edges that carry the clock are marked; outputs that dead-end are listed so wasted signal
	 * is easy to spot.
	 */
	private static int graph(CommandSourceStack source) {
		ServerLevel level = source.getLevel();
		Optional<CityState> city = CityManager.get(level).nearest(BlockPos.containing(source.getPosition()));
		if (city.isEmpty()) {
			source.sendFailure(Component.literal("No city in this dimension."));
			return 0;
		}
		CityState c = city.get();
		java.util.Set<CityState.SlotKey> clocked = c.clockedSlots(true);
		java.util.Set<CityState.SlotKey> live = c.liveSlots(true, false);
		int nodes = 0;
		int edges = 0;
		int deadEnds = 0;
		List<String> lines = new ArrayList<>();
		for (CityState.Slot s : c.slots()) {
			if (s.status != CityState.SlotStatus.BUILT && s.status != CityState.SlotStatus.FAULT) {
				continue;
			}
			Optional<Placement> p = c.placement(s);
			if (p.isEmpty()) {
				continue;
			}
			nodes++;
			for (Placement.WorldPort wp : p.get().ports()) {
				Port port = wp.port();
				if (port.dir() != net.tabor.seedcity.cell.PortDir.OUT) {
					continue;
				}
				String from = s.key + " " + s.cell.getPath() + "." + port.name();
				Optional<CityState.Meeting> m = c.meeting(wp);
				if (m.isPresent() && m.get().port().port().dir() == net.tabor.seedcity.cell.PortDir.IN) {
					edges++;
					String tag = clocked.contains(s.key) ? "  [clock]" : live.contains(s.key) ? "  [live]" : "  [dead]";
					lines.add(from + " -> " + m.get().slot().key + " " + m.get().slot().cell.getPath() + "." + m.get().port().port().name() + tag + (s.status == CityState.SlotStatus.FAULT ? " FAULT" : ""));
				} else {
					deadEnds++;
					lines.add(from + " -> (nothing)");
				}
			}
		}
		int n = nodes, e = edges, d = deadEnds;
		source.sendSuccess(() -> Component.literal("graph: " + n + " cells, " + e + " connections, " + d + " dead-end outputs, "
				+ clocked.size() + " cells on the clock, " + live.size() + " carrying any signal"), false);
		for (String line : lines) {
			source.sendSuccess(() -> Component.literal("  " + line), false);
		}
		return edges;
	}

	/** Gives the caller a written book holding a built-in card, ready for the Card Reader. */
	private static int card(CommandSourceStack source, String name) {
		Optional<String> text = net.tabor.seedcity.card.CardLibrary.get(name);
		if (text.isEmpty()) {
			source.sendFailure(Component.literal("No card named " + name + ". Cards: " + String.join(", ", net.tabor.seedcity.card.CardLibrary.names())));
			return 0;
		}
		net.minecraft.server.level.ServerPlayer player = source.getPlayer();
		if (player == null) {
			source.sendFailure(Component.literal("Only players can hold cards."));
			return 0;
		}
		net.minecraft.world.item.ItemStack book = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WRITTEN_BOOK);
		List<net.minecraft.server.network.Filterable<Component>> pages = new ArrayList<>();
		pages.add(net.minecraft.server.network.Filterable.passThrough(Component.literal(text.get())));
		book.set(net.minecraft.core.component.DataComponents.WRITTEN_BOOK_CONTENT,
				new net.minecraft.world.item.component.WrittenBookContent(net.minecraft.server.network.Filterable.passThrough(name), "Seed City", 0, pages, true));
		if (!player.getInventory().add(book)) {
			player.drop(book, false);
		}
		source.sendSuccess(() -> Component.literal("Card '" + name + "'. Right-click the Card Reader in a Core with it."), false);
		return 1;
	}

	/** The in-game reference: a written book that explains the city, the cards and the commands. */
	private static final String[] MANUAL = {
			"SEED CITY\n\nA city that is a computer. Plant a Seed; Builders raise a Core, a Clock Tower and streets of redstone around it. Every building is a verified circuit. The city runs a program you write in a book.",
			"THE CORE\n\nThe chamber around the Seed. The Card Reader is on its south wall; the Reader wall text shows the program, the registers and errors. The bus leaves through the east wall.",
			"THE CLOCK\n\nThe tall spire south of the Core. Its lamps climb with every beat. One instruction runs per beat. Break the tower and the program stops; Rectifiers rebuild what differs from the blueprint.",
			"WRITING A CARD\n\nCraft a Book and Quill (book + feather + ink sac). Write one op per line. Sign it or leave it open, then right-click the Card Reader with it. Right-click the reader with an empty hand to eject.",
			"OPS (values are 0..15)\n\nSET Rd X\nADD Rd X (max 15)\nSUB Rd X (min 0)\nAND Rd X (min)\nOR Rd X (max)\nNOT Rd (15-Rd)\nJMP label\nJZ Rd label\nWAIT n beats\nOUT port X\nIN Rd port\nNEED cell",
			"EXAMPLE\n\nloop:\nSET R0 15\nOUT drawbridge.*.in R0\nWAIT 2\nSET R0 0\nOUT drawbridge.*.in R0\nWAIT 2\nJMP loop\n\nEvery drawbridge rises and falls with the beat.",
			"PORTS\n\ndrawbridge.in is the first bridge, drawbridge.2.in the second, drawbridge.*.in all of them. OUT drives an input port, IN reads an output port. /seedcity list prints every cell and its ports.",
			"WHAT YOU CAN CONTROL\n\ndrawbridge.in and river_bridge.in lift a deck.\ngatehouse.gate drops a portcullis (any value above 0).\nvault.in opens the strongroom at exactly 15.\nshrine.in rings the bell at exactly 15.\nlamp_tower.in lights that many lamps up the mast.",
			"MORE TO CONTROL AND READ\n\ndecoder_plaza.in: its exits out1 and out8 fire at exactly 1 and 8.\nSentinels wake with a vault's value.\n\nREAD\ndaylight_plaza.out 0..15 by sun.\nfootfall_plaza.out who stands on the plates.",
			"CARDS TO COPY\n\n/seedcity card curfew: gates shut at night.\n/seedcity card alarm: a foot on the plaza seals the gates.\n/seedcity card blink, countdown, daylight, hold_bridges.",
			"REGISTERS\n\nR0 and R1 live in RAM vaults on the bus. A card that names a register the city lacks is accepted as a PLAN: the Builders grow a bus and a vault, then it goes live by itself. Watch the east gate of the Core.",
			"THE BUS\n\nThree comparator lanes: select under the pavement, data and return on top under glass. A vault answers when the select lane says its address. Streets pass the lanes on, branches tap them sideways, an end loops data back.",
			"ALU\n\nSUB, ADD, AND and NOT run through cells in the Forge district: a subtractor, a complement and a max. A card that needs them waits as a plan until the Forge has grown them.",
			"DISTRICTS\n\nThe ring around the Seed is the core. Beyond it the city is cut into sectors: Forge (dark, compute), RAM (vaults and bus), Storage (warehouses), residential and plaza. Labels over each cell show its name and district.",
			"MOBS\n\nBuilders build. Rectifiers patrol and repair. Couriers carry OUT and IN values to far districts. Collectors leave the city for wood, stone and redstone. Sentinels guard vaults, asleep at 0, hostile at 15. Rats scare creepers.",
			"DREAMS\n\nA city with an empty reader writes its own card and runs it; a bigger one follows when it is idle. Its books are in the reader. Your card always wins; ejecting it hands the city back.",
			"COMMANDS\n\n/seedcity card <name> example book\n/seedcity insert <name>\n/seedcity eject\n/seedcity reader\n/seedcity slots  /seedcity graph\n/seedcity fit <cell> [rot]\n/seedcity labels\n/seedcity dream\n/seedcity blueprint <cell>",
			"BLUEPRINTS\n\n/seedcity blueprint <cell> gives an item. Right-click a Builder with it and it builds that cell where you stand, verified like any other. Free, off the grid, yours."
	};

	private static int manual(CommandSourceStack source) {
		net.minecraft.server.level.ServerPlayer player = source.getPlayer();
		if (player == null) {
			source.sendFailure(Component.literal("Only players can hold books."));
			return 0;
		}
		net.minecraft.world.item.ItemStack book = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WRITTEN_BOOK);
		List<net.minecraft.server.network.Filterable<Component>> pages = new ArrayList<>();
		for (String page : MANUAL) {
			pages.add(net.minecraft.server.network.Filterable.passThrough(Component.literal(page)));
		}
		book.set(net.minecraft.core.component.DataComponents.WRITTEN_BOOK_CONTENT,
				new net.minecraft.world.item.component.WrittenBookContent(net.minecraft.server.network.Filterable.passThrough("Seed City Manual"), "Seed City", 0, pages, true));
		if (!player.getInventory().add(book)) {
			player.drop(book, false);
		}
		source.sendSuccess(() -> Component.literal("The Seed City manual. Open it like any book."), false);
		return 1;
	}

	/** Dev shortcut: insert a built-in card straight into the nearest city. */
	private static int insert(CommandSourceStack source, String name) {
		Optional<String> text = net.tabor.seedcity.card.CardLibrary.get(name);
		if (text.isEmpty()) {
			source.sendFailure(Component.literal("No card named " + name));
			return 0;
		}
		ServerLevel level = source.getLevel();
		Optional<CityState> city = CityManager.get(level).nearest(BlockPos.containing(source.getPosition()));
		if (city.isEmpty()) {
			source.sendFailure(Component.literal("No city in this dimension."));
			return 0;
		}
		CityState.CardResult r = city.get().insertCard(level, text.get(), net.minecraft.world.item.ItemStack.EMPTY);
		CityManager.get(level).touch();
		if (r.accepted()) {
			source.sendSuccess(() -> Component.literal(r.message()), true);
			return 1;
		}
		source.sendFailure(Component.literal(r.message()));
		return 0;
	}

	/** Dev shortcut: make the nearest city dream a card now, as if the slot had been empty for the configured time. */
	private static int dream(CommandSourceStack source) {
		ServerLevel level = source.getLevel();
		Optional<CityState> city = CityManager.get(level).nearest(BlockPos.containing(source.getPosition()));
		if (city.isEmpty()) {
			source.sendFailure(Component.literal("No city in this dimension."));
			return 0;
		}
		boolean ok = city.get().dream(level, false);
		CityManager.get(level).touch();
		if (!ok) {
			source.sendFailure(Component.literal("The city could not dream: " + (city.get().programLive() && !city.get().dreaming() ? "a player card is in the reader" : "no fragment fits, even as a plan") + ". " + city.get().programSummary()));
			return 0;
		}
		source.sendSuccess(() -> Component.literal("The city dreams. " + city.get().programSummary()), true);
		return 1;
	}

	/** How the slot under the caller sits on the land: its level, or why it cannot be built. */
	private static int fit(CommandSourceStack source) {
		ServerLevel level = source.getLevel();
		BlockPos at = BlockPos.containing(source.getPosition());
		Optional<CityState> city = CityManager.get(level).nearest(at);
		if (city.isEmpty()) {
			source.sendFailure(Component.literal("No city in this dimension."));
			return 0;
		}
		CityState c = city.get();
		BlockPos flat = c.coreOrigin();
		CityState.SlotKey k = new CityState.SlotKey(Math.floorDiv(at.getX() - flat.getX(), CityState.SLOT), Math.floorDiv(at.getZ() - flat.getZ(), CityState.SLOT));
		CityState.Fit fit = c.fitSlot(level, k);
		source.sendSuccess(() -> Component.literal("slot " + k + " {" + c.district(k) + "}: " + (fit.ok() ? "fits at y=" + fit.y() : fit.unloaded() ? "ground not loaded" : "unfit: " + fit.reason())
				+ c.slot(k).map(s -> "; " + s).orElse("")), false);
		return 1;
	}

	/** Why the planner would or would not put a cell where you stand: room, feeding lanes, legality, weight, wants. */
	private static int explain(CommandContext<CommandSourceStack> c, Rotation rotation) {
		CommandSourceStack source = c.getSource();
		ServerLevel level = source.getLevel();
		BlockPos at = BlockPos.containing(source.getPosition());
		Optional<CityState> city = CityManager.get(level).nearest(at);
		if (city.isEmpty()) {
			source.sendFailure(Component.literal("No city in this dimension."));
			return 0;
		}
		CityState cs = city.get();
		BlockPos flat = cs.coreOrigin();
		CityState.SlotKey k = new CityState.SlotKey(Math.floorDiv(at.getX() - flat.getX(), CityState.SLOT), Math.floorDiv(at.getZ() - flat.getZ(), CityState.SLOT));
		String text = cs.explain(level, k, IdentifierArgument.getId(c, "cell"), rotation);
		source.sendSuccess(() -> Component.literal(text), false);
		return 1;
	}

	/** Toggles floating labels over every built cell: name as cards see it, district, clocked or not, port values. */
	private static int labels(CommandSourceStack source) {
		ServerLevel level = source.getLevel();
		Optional<CityState> city = CityManager.get(level).nearest(BlockPos.containing(source.getPosition()));
		if (city.isEmpty()) {
			source.sendFailure(Component.literal("No city in this dimension."));
			return 0;
		}
		boolean on = !city.get().labels();
		city.get().setLabels(level, on);
		source.sendSuccess(() -> Component.literal(on ? "Cell labels on: name.ordinal, district, clocked/quiet, port values. Run again to hide." : "Cell labels off."), false);
		return 1;
	}

	private static int eject(CommandSourceStack source) {
		ServerLevel level = source.getLevel();
		Optional<CityState> city = CityManager.get(level).nearest(BlockPos.containing(source.getPosition()));
		if (city.isEmpty()) {
			source.sendFailure(Component.literal("No city in this dimension."));
			return 0;
		}
		net.minecraft.world.item.ItemStack card = city.get().ejectCard(level);
		CityManager.get(level).touch();
		net.minecraft.server.level.ServerPlayer player = source.getPlayer();
		if (player != null && !card.isEmpty() && !player.getInventory().add(card)) {
			player.drop(card, false);
		}
		source.sendSuccess(() -> Component.literal("Card ejected. " + city.get().programSummary()), true);
		return 1;
	}

	private static int reader(CommandSourceStack source) {
		ServerLevel level = source.getLevel();
		Optional<CityState> city = CityManager.get(level).nearest(BlockPos.containing(source.getPosition()));
		if (city.isEmpty()) {
			source.sendFailure(Component.literal("No city in this dimension."));
			return 0;
		}
		for (String line : city.get().readerLines()) {
			source.sendSuccess(() -> Component.literal(line), false);
		}
		return 1;
	}

	/** Gives the caller a blueprint for a cell. Hand it to a Builder. */
	private static int blueprint(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		Cell cell = cell(c);
		net.minecraft.server.level.ServerPlayer player = c.getSource().getPlayer();
		if (player == null) {
			c.getSource().sendFailure(Component.literal("Only players can hold blueprints."));
			return 0;
		}
		net.minecraft.world.item.ItemStack stack = net.tabor.seedcity.SeedCityItems.blueprint(cell.id().getPath());
		if (!player.getInventory().add(stack)) {
			player.drop(stack, false);
		}
		c.getSource().sendSuccess(() -> Component.literal("Blueprint for " + cell.id().getPath() + ". Right-click a Builder with it where you want the cell."), false);
		return 1;
	}

	private static int slots(CommandSourceStack source) {
		ServerLevel level = source.getLevel();
		Optional<CityState> city = CityManager.get(level).nearest(BlockPos.containing(source.getPosition()));
		if (city.isEmpty()) {
			source.sendFailure(Component.literal("No city in this dimension."));
			return 0;
		}
		for (String line : city.get().describeSlots()) {
			source.sendSuccess(() -> Component.literal(line), false);
		}
		return 1;
	}
}
