package net.tabor.seedcity.gametest;

import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.tabor.seedcity.SeedCity;
import net.tabor.seedcity.build.BuildTask;
import net.tabor.seedcity.cell.Cell;
import net.tabor.seedcity.cell.CellDefinition;
import net.tabor.seedcity.cell.CellKind;
import net.tabor.seedcity.cell.Placement;
import net.tabor.seedcity.core.CityState;

/** Regression coverage for the reconstructed source missing from upstream. */
public final class BuildTaskTests {
    private static final CityState.SlotKey SLOT = new CityState.SlotKey(0, 0);
    private static Cell cell(List<Cell.CellBlock> blocks) {
        return new Cell(new CellDefinition(SeedCity.id("task_fixture"), CellKind.DECOR,
                new Vec3i(3,3,3), List.of(), "none", Map.of(),
                Map.of("redstone",2,"stone",3,"wood",4), false,40,null),
                new StructureTemplate(), blocks);
    }
    private static Cell.CellBlock stone(int x, int y, int z) {
        return new Cell.CellBlock(new BlockPos(x,y,z), Blocks.STONE.defaultBlockState());
    }

    @GameTest(structure="seedcity:arena", maxTicks=20)
    public void incrementalAndIdempotent(GameTestHelper h) {
        BlockPos origin=h.absolutePos(new BlockPos(3,2,3));
        var task=new BuildTask(new Placement(cell(List.of(stone(0,0,0),stone(1,0,0))),origin,Rotation.NONE),SLOT);
        h.assertTrue(!task.step(h.getLevel()),"first step must not complete two blocks");
        h.assertTrue(h.getLevel().getBlockState(origin).is(Blocks.STONE),"first block missing");
        h.assertTrue(h.getLevel().getBlockState(origin.east()).isAir(),"placed more than one block per step");
        h.assertTrue(task.progress()==0.5,"wrong progress");
        h.assertTrue(task.step(h.getLevel()) && task.step(h.getLevel()),"completion must be idempotent");
        h.assertTrue(task.cost().equals(new BuildTask.Cost(2,3,4)),"material ledger cost mismatch");
        h.succeed();
    }

    @GameTest(structure="seedcity:arena", maxTicks=20)
    public void rotatedFaultAndRepair(GameTestHelper h) {
        BlockPos origin=h.absolutePos(new BlockPos(4,2,4));
        Cell c=cell(List.of(stone(0,0,0),stone(1,0,0)));
        for (Rotation r:Rotation.values()) {
            Placement p=new Placement(c,origin,r);
            BlockPos fault=origin.offset(new BlockPos(1,0,0).rotate(r));
            h.getLevel().setBlock(fault,Blocks.AIR.defaultBlockState(),Block.UPDATE_ALL);
            BuildTask t=new BuildTask(p,SLOT,new BlockPos(1,0,0));
            while(!t.step(h.getLevel())) {}
            h.assertTrue(h.getLevel().getBlockState(fault).isAir(),"fault was not omitted for "+r);
            t=BuildTask.repair(p,SLOT);
            while(!t.step(h.getLevel())) {}
            h.assertTrue(h.getLevel().getBlockState(fault).is(Blocks.STONE),"repair did not restore rotated fault "+r);
        }
        h.succeed();
    }

    @GameTest(structure="seedcity:arena", maxTicks=20)
    public void growthProtectsObstructionButRepairReplacesIt(GameTestHelper h) {
        BlockPos origin=h.absolutePos(new BlockPos(3,2,3));
        Placement p=new Placement(cell(List.of(stone(0,1,0))),origin,Rotation.NONE);
        h.getLevel().setBlock(origin.above(),Blocks.DIAMOND_BLOCK.defaultBlockState(),Block.UPDATE_ALL);
        boolean rejected=false;
        try { new BuildTask(p,SLOT).step(h.getLevel()); }
        catch(IllegalStateException expected) { rejected=true; }
        h.assertTrue(rejected,"growth must reject a foreign block");
        h.assertTrue(h.getLevel().getBlockState(origin.above()).is(Blocks.DIAMOND_BLOCK),"foreign block overwritten");
        h.assertTrue(BuildTask.repair(p,SLOT).step(h.getLevel()),"repair did not complete");
        h.assertTrue(h.getLevel().getBlockState(origin.above()).is(Blocks.STONE),"blueprint not restored");
        h.succeed();
    }

    @GameTest(structure="seedcity:arena", maxTicks=20)
    public void repairPreservesLiveStateAndEmptyTaskCompletes(GameTestHelper h) {
        BlockPos origin=h.absolutePos(new BlockPos(3,2,3));
        var off=Blocks.REDSTONE_LAMP.defaultBlockState();
        var on=off.setValue(BlockStateProperties.LIT,true);
        Placement p=new Placement(cell(List.of(new Cell.CellBlock(BlockPos.ZERO,off))),origin,Rotation.NONE);
        h.getLevel().setBlock(origin,on,Block.UPDATE_CLIENTS);
        h.assertTrue(BuildTask.repair(p,SLOT).step(h.getLevel()),"unchanged circuit should complete");
        h.assertTrue(h.getLevel().getBlockState(origin).getValue(BlockStateProperties.LIT),"repair reset live state");
        BuildTask empty=new BuildTask(new Placement(cell(List.of()),origin,Rotation.NONE),SLOT);
        h.assertTrue(empty.step(h.getLevel()) && empty.progress()==1.0,"empty task did not complete");
        h.succeed();
    }
}
