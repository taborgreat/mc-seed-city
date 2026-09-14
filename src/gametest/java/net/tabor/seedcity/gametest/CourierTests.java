package net.tabor.seedcity.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.tabor.seedcity.entity.SeedCityEntities;

public final class CourierTests {
    private void tunnel(GameTestHelper h, boolean blocked) {
        for(int x=1;x<=9;x++) for(int z=1;z<=3;z++) {
            h.setBlock(new BlockPos(x,1,z),Blocks.STONE);
            h.setBlock(new BlockPos(x,2,z),z==2?Blocks.AIR:Blocks.STONE);
            h.setBlock(new BlockPos(x,3,z),Blocks.STONE);
        }
        for(int x=3;x<=7;x++) h.setBlock(new BlockPos(x,2,2),
                Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE,SlabType.TOP));
        h.setBlock(new BlockPos(1,2,2),Blocks.STONE);
        h.setBlock(new BlockPos(9,2,2),Blocks.STONE);
        if(blocked) h.setBlock(new BlockPos(5,2,2),Blocks.STONE);
        var courier=h.spawn(SeedCityEntities.COURIER,new BlockPos(2,2,2));
        var target=h.absolutePos(new BlockPos(8,2,2));
        h.runAtTickTime(5,()->{
            var path=courier.getNavigation().createPath(target,0);
            if(blocked) {
                h.assertTrue(path==null || !path.canReach(),"Solid wall must prevent a complete route");
                h.succeed();
            } else {
                h.assertTrue(path!=null && path.canReach(),"Courier must plan a route below upper slabs");
                courier.getNavigation().moveTo(path,1);
            }
        });
        if(!blocked) h.onEachTick(()->{
            if(courier.distanceToSqr(target.getX()+.5,target.getY(),target.getZ()+.5)<.3) {
                h.assertTrue(courier.getHealth()==courier.getMaxHealth(),"Tunnel must not suffocate Courier");
                h.succeed();
            }
        });
    }
    @GameTest(structure="seedcity:boat",maxTicks=160)
    public void traversesHalfBlockTunnel(GameTestHelper h) { tunnel(h,false); }
    @GameTest(structure="seedcity:boat",maxTicks=30)
    public void solidWallBlocksRoute(GameTestHelper h) { tunnel(h,true); }
}
