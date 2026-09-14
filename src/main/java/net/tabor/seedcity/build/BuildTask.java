package net.tabor.seedcity.build;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.tabor.seedcity.SeedCityBlocks;
import net.tabor.seedcity.cell.Cell;
import net.tabor.seedcity.cell.Placement;
import net.tabor.seedcity.core.CityState;
import net.tabor.seedcity.verify.Integrity;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** One ordered, incremental blueprint task, shared by construction and repair.
 * Reconstructed from the published callers/spec: the original source was absent
 * from Git because the unanchored build/ ignore rule also hid this Java package.
 * CityState owns materials, scheduling and verification; this class only places.
 */
public final class BuildTask {
    public record Cost(int redstone, int stone, int wood) {}

    private final Placement placement;
    private final CityState.SlotKey slot;
    private final List<Cell.CellBlock> blocks;
    private final Set<BlockPos> dynamic;
    private final boolean repair;
    private int cursor;

    public BuildTask(Placement placement, CityState.SlotKey slot) {
        this(placement, slot, null);
    }

    /** omit is unrotated cell-local, matching the sidecar's fault coordinate. */
    public BuildTask(Placement placement, CityState.SlotKey slot, BlockPos omit) {
        this(placement, slot, omit, false);
    }

    private BuildTask(Placement placement, CityState.SlotKey slot, BlockPos omit, boolean repair) {
        this.placement = placement;
        this.slot = slot;
        this.repair = repair;
        BlockPos rotatedOmit = omit == null ? null : omit.rotate(placement.rotation());
        blocks = placement.cell().blocks(placement.rotation()).stream()
                .filter(b -> !b.pos().equals(rotatedOmit))
                .filter(b -> !b.state().isAir() && !b.state().is(Blocks.STRUCTURE_VOID))
                .toList();
        dynamic = repair ? Integrity.dynamicPositions(placement.cell()).stream()
                .map(p -> p.rotate(placement.rotation())).collect(Collectors.toUnmodifiableSet()) : Set.of();
    }

    public static BuildTask repair(Placement placement, CityState.SlotKey slot) {
        return new BuildTask(placement, slot, null, true);
    }

    public Placement placement() { return placement; }
    public CityState.SlotKey slot() { return slot; }
    public Cost cost() {
        var cost = placement.cell().definition().cost();
        return new Cost(cost.getOrDefault("redstone", 0), cost.getOrDefault("stone", 0), cost.getOrDefault("wood", 0));
    }
    public double progress() { return blocks.isEmpty() ? 1.0 : (double) cursor / blocks.size(); }
    public BlockPos nextPos() {
        return cursor < blocks.size() ? placement.origin().offset(blocks.get(cursor).pos()) : placement.origin();
    }

    /** Skip correct blocks without resetting live circuits; write at most one changed block.
     * Returns true on completion, including empty blueprints and repeated completion calls.
     * A newly obstructed growth slot fails safely and is re-queued by BuilderEntity.
     */
    public boolean step(ServerLevel level) {
        while (cursor < blocks.size()) {
            Cell.CellBlock b = blocks.get(cursor);
            BlockPos pos = placement.origin().offset(b.pos());
            BlockState actual = level.getBlockState(pos);
            if (Integrity.equivalent(b.state(), actual)
                    || repair && (dynamic.contains(b.pos()) || actual.is(SeedCityBlocks.PROBE)
                    || actual.is(Blocks.PISTON_HEAD) || actual.is(Blocks.MOVING_PISTON))) {
                cursor++;
                continue;
            }
            if (!repair && b.pos().getY() != 0 && !actual.isAir()
                    && !actual.is(net.tabor.seedcity.SeedCityBlocks.COLLECTOR_LIGHT)) {
                throw new IllegalStateException("Construction obstructed at " + pos.toShortString());
            }
            if (!level.setBlock(pos, b.state(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS)) {
                throw new IllegalStateException("Cannot place blueprint block at " + pos.toShortString());
            }
            var sound = b.state().getSoundType();
            level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS,
                    (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
            cursor++;
            return cursor == blocks.size();
        }
        return true;
    }
}
