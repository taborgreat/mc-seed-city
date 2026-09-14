package net.tabor.seedcity.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.AABB;

/** Admit collision-free spaces beneath slabs/trapdoors that vanilla marks as blocked cells. */
public final class CourierNavigation extends GroundPathNavigation {
    public CourierNavigation(Mob mob, Level level) { super(mob, level); }

    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes) {
        nodeEvaluator = new WalkNodeEvaluator() {
            @Override
            public PathType getPathTypeOfMob(PathfindingContext context, int x, int y, int z, Mob mob) {
                PathType original=super.getPathTypeOfMob(context,x,y,z,mob);
                var pos=new BlockPos(x,y,z);
                var block=context.getBlockState(pos).getBlock();
                if(original!=PathType.BLOCKED || !(block instanceof SlabBlock || block instanceof TrapDoorBlock))
                    return original;
                // Keep vanilla handling of hazards, fluids and unsupported drops.
                if(!context.getBlockState(pos).getFluidState().isEmpty()) return original;
                var below=context.getBlockState(pos.below());
                if(context.getPathTypeFromState(x,y-1,z)!=PathType.BLOCKED) return original;
                if(!below.isFaceSturdy(context.level(),pos.below(),net.minecraft.core.Direction.UP)) return original;
                double half=mob.getBbWidth()/2.0;
                AABB box=new AABB(x+.5-half,y+.001,z+.5-half,x+.5+half,y+mob.getBbHeight(),z+.5+half);
                return context.level().noCollision(mob,box) ? PathType.WALKABLE : original;
            }
        };
        nodeEvaluator.setCanPassDoors(true);
        return new PathFinder(nodeEvaluator,maxVisitedNodes);
    }
}
