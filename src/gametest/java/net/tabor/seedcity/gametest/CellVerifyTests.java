package net.tabor.seedcity.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.tabor.seedcity.SeedCity;
import net.tabor.seedcity.cell.Cell;
import net.tabor.seedcity.cell.CellLibrary;
import net.tabor.seedcity.cell.Placement;
import net.tabor.seedcity.verify.Verifier;
import net.tabor.seedcity.verify.VerifyResult;

import java.util.List;
import java.util.Optional;

/**
 * Phase 0 acceptance (design doc 25): every shipped cell places and passes its truth model in a
 * fresh world. One test per cell so the report names the culprit.
 */
public final class CellVerifyTests {
	private static final String ARENA = "seedcity:arena";
	private static final String BOAT = "seedcity:boat";
	private static final int BUDGET = 1600;
	/** Leaves a ring of air around a 7x7 cell inside the 12x12 arena for probes. */
	private static final BlockPos CELL_ORIGIN = new BlockPos(2, 1, 2);

	@GameTest(structure = ARENA, maxTicks = BUDGET)
	public void busSegment(GameTestHelper helper) {
		verifyCell(helper, "bus_segment", Rotation.NONE);
	}

	@GameTest(structure = ARENA, maxTicks = BUDGET)
	public void busSegmentRotated(GameTestHelper helper) {
		verifyCell(helper, "bus_segment", Rotation.CLOCKWISE_90);
	}

	@GameTest(structure = ARENA, maxTicks = BUDGET)
	public void busStreet(GameTestHelper helper) {
		verifyCell(helper, "bus_street", Rotation.NONE);
	}

	@GameTest(structure = ARENA, maxTicks = BUDGET)
	public void busStreetRotated(GameTestHelper helper) {
		verifyCell(helper, "bus_street", Rotation.COUNTERCLOCKWISE_90);
	}

	@GameTest(structure = ARENA, maxTicks = BUDGET)
	public void busEnd(GameTestHelper helper) {
		verifyCell(helper, "bus_end", Rotation.NONE);
	}

	@GameTest(structure = ARENA, maxTicks = BUDGET)
	public void busBranch(GameTestHelper helper) {
		verifyCell(helper, "bus_branch", Rotation.NONE);
	}

	@GameTest(structure = BOAT, maxTicks = 2400)
	public void ramVault1(GameTestHelper helper) {
		verifyCellAt(helper, "ram_vault_1", Rotation.NONE, new BlockPos(4, 1, 4));
	}

	@GameTest(structure = BOAT, maxTicks = 2400)
	public void ramVault2(GameTestHelper helper) {
		verifyCellAt(helper, "ram_vault_2", Rotation.NONE, new BlockPos(4, 1, 4));
	}

	@GameTest(structure = BOAT, maxTicks = 2400)
	public void ramVault1Rotated(GameTestHelper helper) {
		verifyCellAt(helper, "ram_vault_1", Rotation.COUNTERCLOCKWISE_90, new BlockPos(4, 1, 4));
	}

	@GameTest(structure = ARENA, maxTicks = BUDGET)
	public void gardenLane(GameTestHelper helper) {
		verifyCell(helper, "garden_lane", Rotation.CLOCKWISE_90);
	}

	@GameTest(structure = ARENA, maxTicks = BUDGET)
	public void gatehouse(GameTestHelper helper) {
		verifyCell(helper, "gatehouse", Rotation.NONE);
	}

	@GameTest(structure = ARENA, maxTicks = BUDGET)
	public void footfallPlaza(GameTestHelper helper) {
		verifyCell(helper, "footfall_plaza", Rotation.NONE);
	}

	@GameTest(structure = ARENA, maxTicks = BUDGET)
	public void inverter(GameTestHelper helper) {
		verifyCell(helper, "inverter", Rotation.NONE);
	}

	@GameTest(structure = ARENA, maxTicks = BUDGET)
	public void registerBlock(GameTestHelper helper) {
		verifyCell(helper, "register_block", Rotation.NONE);
	}

	@GameTest(structure = ARENA, maxTicks = BUDGET)
	public void clockTower(GameTestHelper helper) {
		verifyCell(helper, "clock_tower", Rotation.NONE);
	}

	@GameTest(structure = ARENA, maxTicks = BUDGET)
	public void drawbridge(GameTestHelper helper) {
		verifyCell(helper, "drawbridge", Rotation.NONE);
	}

	@GameTest(structure = ARENA, maxTicks = BUDGET)
	public void storageCell(GameTestHelper helper) {
		verifyCell(helper, "storage_cell", Rotation.NONE);
	}

	@GameTest(structure = ARENA, maxTicks = BUDGET)
	public void core(GameTestHelper helper) {
		verifyCell(helper, "core", Rotation.NONE);
	}

	@GameTest(structure = ARENA, maxTicks = BUDGET)
	public void decorPlaza(GameTestHelper helper) {
		verifyCell(helper, "decor_plaza", Rotation.NONE);
	}

	@GameTest(structure = ARENA, maxTicks = BUDGET, skyAccess = true)
	public void daylightPlaza(GameTestHelper helper) {
		verifyCell(helper, "daylight_plaza", Rotation.NONE);
	}

	@GameTest(structure = ARENA, maxTicks = BUDGET)
	public void wireSegment(GameTestHelper helper) {
		verifyCell(helper, "wire_segment", Rotation.COUNTERCLOCKWISE_90);
	}

	@GameTest(structure = ARENA, maxTicks = BUDGET)
	public void junction(GameTestHelper helper) {
		verifyCell(helper, "junction", Rotation.CLOCKWISE_180);
	}

	@GameTest(structure = ARENA, maxTicks = BUDGET)
	public void aluSub(GameTestHelper helper) {
		verifyCell(helper, "alu_sub", Rotation.NONE);
	}

	@GameTest(structure = ARENA, maxTicks = BUDGET)
	public void aluNot(GameTestHelper helper) {
		verifyCell(helper, "alu_not", Rotation.CLOCKWISE_90);
	}

	@GameTest(structure = ARENA, maxTicks = BUDGET)
	public void aluOr(GameTestHelper helper) {
		verifyCell(helper, "alu_or", Rotation.NONE);
	}

	@GameTest(structure = ARENA, maxTicks = BUDGET)
	public void vault(GameTestHelper helper) {
		verifyCell(helper, "vault", Rotation.NONE);
	}

	@GameTest(structure = ARENA, maxTicks = 40)
	public void libraryLoadsAllCells(GameTestHelper helper) {
		helper.assertTrue(CellLibrary.all().size() >= 15, "expected at least fifteen cells loaded, got " + CellLibrary.all().size());
		helper.assertTrue(CellLibrary.errors().isEmpty(), "library rejected: " + CellLibrary.errors());
		helper.succeed();
	}

	/** Design doc 25, phase 0: a deliberately broken cell fails and the failure names a port. */
	@GameTest(structure = ARENA, maxTicks = BUDGET)
	public void corruptedCellFailsNamingPort(GameTestHelper helper) {
		Identifier id = SeedCity.id("bus_segment");
		Cell cell = CellLibrary.get(id).orElse(null);
		if (cell == null) {
			helper.fail("cell not loaded: " + id);
			return;
		}
		Placement placement = new Placement(cell, helper.absolutePos(CELL_ORIGIN), Rotation.NONE);
		placement.place(helper.getLevel());
		// knock the middle comparator out of the bus
		helper.setBlock(CELL_ORIGIN.offset(3, 1, 3), Blocks.AIR);
		VerifyResult[] result = new VerifyResult[1];
		Verifier.verify(helper.getLevel(), placement, List.of(), r -> result[0] = r);
		helper.onEachTick(() -> {
			if (result[0] == null) {
				return;
			}
			if (result[0].pass()) {
				helper.fail("broken bus verified as passing");
			} else if (!result[0].message().contains("port out")) {
				helper.fail("failure did not name the port: " + result[0]);
			} else {
				helper.succeed();
			}
		});
	}

	private static void verifyCell(GameTestHelper helper, String name, Rotation rotation) {
		BlockPos origin = switch (rotation) {
			case NONE -> CELL_ORIGIN;
			case CLOCKWISE_90 -> CELL_ORIGIN.offset(6, 0, 0);
			case CLOCKWISE_180 -> CELL_ORIGIN.offset(6, 0, 6);
			case COUNTERCLOCKWISE_90 -> CELL_ORIGIN.offset(0, 0, 6);
		};
		verifyCellAt(helper, name, rotation, origin);
	}

	/** Places a cell with its rotation origin at {@code origin} (already shifted for the rotation) and verifies it. */
	private static void verifyCellAt(GameTestHelper helper, String name, Rotation rotation, BlockPos origin) {
		Identifier id = SeedCity.id(name);
		Optional<Cell> cell = CellLibrary.get(id);
		if (cell.isEmpty()) {
			helper.fail("cell not loaded: " + id + " (library errors: " + CellLibrary.errors() + ")");
			return;
		}
		Optional<Cell> c = cell;
		// a vault is 14 deep: rotated counterclockwise it swings west, so shift by its depth instead of 6
		BlockPos shifted = origin;
		if (rotation == Rotation.COUNTERCLOCKWISE_90 && c.get().size().getZ() != 7) {
			shifted = origin.offset(0, 0, c.get().size().getX() - 1 - 6);
		}
		Placement placement = new Placement(cell.get(), helper.absolutePos(shifted), rotation);
		if (!placement.place(helper.getLevel())) {
			helper.fail("could not place " + placement);
			return;
		}
		VerifyResult[] result = new VerifyResult[1];
		Verifier.verify(helper.getLevel(), placement, List.of(), r -> result[0] = r);
		helper.onEachTick(() -> {
			if (result[0] == null) {
				return;
			}
			if (result[0].pass()) {
				helper.succeed();
			} else {
				helper.fail(result[0].toString());
			}
		});
	}
}
