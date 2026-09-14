package net.tabor.seedcity.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.tabor.seedcity.SeedCityBlocks;
import net.tabor.seedcity.entity.SeedCityEntities;

public final class CollectorTests {
    @GameTest(structure="seedcity:boat",maxTicks=100)
    public void walksAtPlayerPace(GameTestHelper h) {
        for(int x=1;x<41;x++) for(int z=1;z<4;z++) {
            h.setBlock(new BlockPos(x,1,z),Blocks.STONE);
            for(int y=2;y<5;y++) h.setBlock(new BlockPos(x,y,z),Blocks.AIR);
        }
        var mob=h.spawn(SeedCityEntities.COLLECTOR,new BlockPos(2,2,2));
        var target=h.absolutePos(new BlockPos(39,2,2));
        final double[] start=new double[2];
        h.runAtTickTime(5,()->mob.getNavigation().moveTo(target.getX()+.5,target.getY(),target.getZ()+.5,1));
        h.runAtTickTime(30,()->{start[0]=mob.getX();start[1]=mob.getZ();});
        h.runAtTickTime(70,()->{
            double speed=Math.hypot(mob.getX()-start[0],mob.getZ()-start[1])/2;
            h.assertTrue(speed>4.15&&speed<4.5,"Normal player walking pace should be about 4.317 blocks/s; got "+speed);
            h.succeed();
        });
    }
    @GameTest(structure="seedcity:boat",maxTicks=160)
    public void walksTwoBlockTunnelWithRealTools(GameTestHelper h) {
        for(int x=1;x<=9;x++) for(int z=1;z<=3;z++) for(int y=1;y<=4;y++)
            h.setBlock(new BlockPos(x,y,z),(y==1||y==4||z!=2)?Blocks.STONE:Blocks.AIR);
        var mob=h.spawn(SeedCityEntities.COLLECTOR,new BlockPos(2,2,2));
        h.assertTrue(mob.getMainHandItem().is(Items.IRON_PICKAXE),"Must equip an actual iron pickaxe");
        mob.selectToolFor(Blocks.OAK_LOG.defaultBlockState());
        h.assertTrue(mob.getMainHandItem().is(Items.IRON_AXE),"Logs select an actual iron axe");
        mob.selectToolFor(Blocks.STONE.defaultBlockState());
        var target=h.absolutePos(new BlockPos(8,2,2));
        h.runAtTickTime(5,()->{
            var path=mob.getNavigation().createPath(target,0);
            h.assertTrue(path!=null&&path.canReach(),"Must fit a one-wide, two-high tunnel");
            mob.getNavigation().moveTo(path,1);
        });
        h.onEachTick(()->{if(mob.distanceToSqr(target.getX()+.5,target.getY(),target.getZ()+.5)<.3) h.succeed();});
    }
    @GameTest(structure="seedcity:boat",maxTicks=60)
    public void lampLightsAndCleansUp(GameTestHelper h) {
        h.setBlock(new BlockPos(3,1,3),Blocks.STONE);
        h.setBlock(new BlockPos(3,2,3),Blocks.AIR);
        h.setBlock(new BlockPos(3,3,3),Blocks.AIR);
        var mob=h.spawn(SeedCityEntities.COLLECTOR,new BlockPos(3,2,3));
        mob.setNoAi(true);
        var light=h.absolutePos(new BlockPos(3,3,3));
        h.runAtTickTime(8,()->{
            var state=h.getLevel().getBlockState(light);
            h.assertTrue(state.is(SeedCityBlocks.COLLECTOR_LIGHT)&&state.getLightEmission()==12,"Lamp must emit real block light");
            h.assertTrue(net.tabor.seedcity.verify.Integrity.equivalent(Blocks.AIR.defaultBlockState(),state),"Lamp must not create an air-cell repair fault");
            h.assertTrue(!net.tabor.seedcity.verify.Integrity.equivalent(Blocks.STONE.defaultBlockState(),state),"Lamp must not hide a missing solid block");
            mob.discard();
        });
        h.runAtTickTime(25,()->{
            h.assertTrue(h.getLevel().getBlockState(light).isAir(),"Light must expire after removal");h.succeed();
        });
    }
    @GameTest(structure="seedcity:boat",maxTicks=35)
    public void lampNeverOverwritesBlocks(GameTestHelper h) {
        h.setBlock(new BlockPos(3,1,3),Blocks.STONE);
        var mob=h.spawn(SeedCityEntities.COLLECTOR,new BlockPos(3,2,3));mob.setNoAi(true);mob.setNoGravity(true);
        var pos=mob.lampPosition();h.getLevel().setBlock(pos,Blocks.STONE.defaultBlockState(),3);
        h.runAtTickTime(15,()->{
            h.assertTrue(h.getLevel().getBlockState(pos).is(Blocks.STONE),"Lamp must never overwrite stone");mob.discard();h.succeed();
        });
    }
}
