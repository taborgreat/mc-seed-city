package net.tabor.seedcity.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.AABB;

/** City-local cat-style population cap; never loads chunks or changes blocks. */
public final class RedstoneRatSpawning {
    private RedstoneRatSpawning(){}
    public static boolean trySpawn(ServerLevel level,BlockPos seed,int cap) {
        cap=Math.clamp(cap,0,8);
        if(cap==0)return false;
        for(int x=(seed.getX()-24)>>4;x<=(seed.getX()+24)>>4;x++)
            for(int z=(seed.getZ()-24)>>4;z<=(seed.getZ()+24)>>4;z++)
                if(!level.hasChunk(x,z))return false;
        var area=new AABB(seed).inflate(24);
        if(level.getEntities(SeedCityEntities.REDSTONE_RAT,area,e->e.isAlive()).size()>=cap)return false;
        // Fixed search order keeps city placement deterministic.
        for(int radius:new int[]{6,10,14})for(int[] offset:new int[][]{{radius,0},{0,radius},{-radius,0},{0,-radius}})
            for(int dy=6;dy>=-6;dy--) {
                var pos=seed.offset(offset[0],dy,offset[1]);
                if(!level.getBlockState(pos.below()).isFaceSturdy(level,pos.below(),Direction.UP)
                        ||!level.getFluidState(pos).isEmpty())continue;
                var bounds=new AABB(pos.getX()+.225,pos.getY(),pos.getZ()+.225,pos.getX()+.775,pos.getY()+.3,pos.getZ()+.775);
                if(!level.noCollision(bounds))continue;
                var rat=SeedCityEntities.REDSTONE_RAT.spawn(level,pos,EntitySpawnReason.EVENT);
                if(rat!=null){rat.setHomeTo(seed,24);return true;}
            }
        return false;
    }
}
