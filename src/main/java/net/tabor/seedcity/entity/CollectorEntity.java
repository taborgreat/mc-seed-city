package net.tabor.seedcity.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.tabor.seedcity.SeedCityBlocks;

/** Equipped ground worker. City-owned gather orders are a separate integration. */
public final class CollectorEntity extends PathfinderMob {
    public CollectorEntity(EntityType<? extends CollectorEntity> type, Level level) {
        super(type,level);
        setPersistenceRequired();
        setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_PICKAXE));
        setDropChance(EquipmentSlot.MAINHAND,0);
    }
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH,30)
                .add(Attributes.MOVEMENT_SPEED,.1).add(Attributes.FOLLOW_RANGE,24);
    }
    @Override public void setSpeed(float speed) {
        super.setSpeed(speed);
        // Players use full forward input; Mob.setSpeed otherwise scales it a second time.
        setZza(speed>0?1:0);
    }
    @Override protected void registerGoals() {
        goalSelector.addGoal(0,new FloatGoal(this));
        goalSelector.addGoal(6,new LookAtPlayerGoal(this,Player.class,6));
        goalSelector.addGoal(7,new RandomLookAroundGoal(this));
    }
    /** Called by a gather task before working its authorized target. */
    public void selectToolFor(BlockState target) {
        var item=target.is(BlockTags.LOGS)?Items.IRON_AXE:Items.IRON_PICKAXE;
        if(!getMainHandItem().is(item)) setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(item));
    }
    public BlockPos lampPosition() { return BlockPos.containing(getX(),getY()+1.35,getZ()); }
    @Override public void tick() {
        super.tick();
        if(level() instanceof ServerLevel server && isAlive()) {
            if(!getMainHandItem().is(Items.IRON_PICKAXE) && !getMainHandItem().is(Items.IRON_AXE))
                setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_PICKAXE));
            if(tickCount%5==0) {
                var pos=lampPosition(); var state=server.getBlockState(pos);
                if(state.isAir()) {
                    // Update clients/light without notifying redstone neighbors or shapes.
                    server.setBlock(pos,SeedCityBlocks.COLLECTOR_LIGHT.defaultBlockState(),18);
                    server.scheduleTick(pos,SeedCityBlocks.COLLECTOR_LIGHT,10);
                } else if(state.is(SeedCityBlocks.COLLECTOR_LIGHT)) {
                    server.scheduleTick(pos,SeedCityBlocks.COLLECTOR_LIGHT,10);
                }
            }
        }
    }
}
