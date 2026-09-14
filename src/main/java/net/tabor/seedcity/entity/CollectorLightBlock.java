package net.tabor.seedcity.entity;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Temporary lamp light; scheduled ticks survive saves and expire abandoned lights. */
public final class CollectorLightBlock extends LightBlock {
    public static final MapCodec<LightBlock> CODEC=simpleCodec(CollectorLightBlock::new);
    public CollectorLightBlock(Properties properties) { super(properties); }
    @Override public MapCodec<LightBlock> codec() { return CODEC; }
    @Override protected void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random) {
        boolean occupied=!level.getEntitiesOfClass(CollectorEntity.class,new AABB(pos).inflate(2),
                mob->mob.isAlive() && mob.lampPosition().equals(pos)).isEmpty();
        if(occupied) level.scheduleTick(pos,this,10);
        else level.setBlock(pos,state.getValue(WATERLOGGED)?Blocks.WATER.defaultBlockState():Blocks.AIR.defaultBlockState(),18);
    }
}
