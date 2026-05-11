package com.layerfill.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/**
 * Rewrites water horizontal spreading so that a layer must be completely
 * filled before water rises to the next layer above.
 *
 * We hook FlowingFluid#tick. On every tick of a water source block:
 *   1. Downward flow is left entirely to vanilla (we return immediately).
 *   2. If the space is too large (>64 reachable cells) we return — vanilla.
 *   3. Otherwise we fill one open horizontal neighbour on the same Y and
 *      reschedule, suppressing vanilla's own spread for this tick.
 */
@Mixin(value = FlowingFluid.class, priority = 1100)
public abstract class FlowingFluidMixin {

    private static final int MAX_SCAN = 64;

    @Inject(
        method = "tick",
        at = @At("HEAD"),
        cancellable = true
    )
    private void layerFill$tick(Level level, BlockPos pos, FluidState state, CallbackInfo ci) {
        // Only intercept water source blocks.
        if (!state.getType().isSame(Fluids.WATER)) return;
        if (!state.isSource()) return;

        // If there's air/replaceable space below, let vanilla handle downward flow.
        BlockPos below = pos.below();
        if (canReceiveWater(level, below)) return;

        // Scan the current Y layer for open (unfilled) cells.
        LayerResult result = scanLayer(level, pos);

        // Too large — open world, let vanilla run.
        if (result.hitCap) return;

        // No open cells — layer is full, let vanilla run (it will flow up).
        if (result.openCells.isEmpty()) return;

        // Fill the first open cell and reschedule both blocks.
        BlockPos target = result.openCells.get(0);
        level.setBlock(target, Blocks.WATER.defaultBlockState(), 3);
        level.scheduleTick(target, Fluids.WATER, 5);
        level.scheduleTick(pos,    Fluids.WATER, 5);

        // Cancel vanilla spread for this tick so we don't get double-fill.
        ci.cancel();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * BFS across the same Y plane. Returns all reachable open (air/replaceable)
     * cells, and whether the scan hit the cap.
     */
    private LayerResult scanLayer(Level level, BlockPos start) {
        List<BlockPos>  open    = new ArrayList<>();
        Set<BlockPos>   visited = new HashSet<>();
        Queue<BlockPos> queue   = new LinkedList<>();

        visited.add(start);
        queue.add(start);

        while (!queue.isEmpty()) {
            if (visited.size() > MAX_SCAN) return new LayerResult(open, true);
            BlockPos cur = queue.poll();

            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos nb = cur.relative(dir);
                if (visited.contains(nb)) continue;
                visited.add(nb);

                if (canReceiveWater(level, nb)) {
                    open.add(nb);
                    queue.add(nb); // keep scanning through open space
                } else {
                    FluidState fs = level.getFluidState(nb);
                    if (fs.getType().isSame(Fluids.WATER) && fs.isSource()) {
                        queue.add(nb); // already water — scan through it
                    }
                    // solid wall — stop branch
                }
            }
        }

        return new LayerResult(open, false);
    }

    /** True if water can move into this position. */
    private boolean canReceiveWater(Level level, BlockPos pos) {
        BlockState bs = level.getBlockState(pos);
        if (bs.isAir()) return true;
        FluidState fs = level.getFluidState(pos);
        if (fs.getType().isSame(Fluids.WATER) && !fs.isSource()) return true;
        return bs.canBeReplaced() && !bs.isSolid();
    }

    // ── Result container ─────────────────────────────────────────────────────

    private static final class LayerResult {
        final List<BlockPos> openCells;
        final boolean hitCap;
        LayerResult(List<BlockPos> openCells, boolean hitCap) {
            this.openCells = openCells;
            this.hitCap    = hitCap;
        }
    }
}
