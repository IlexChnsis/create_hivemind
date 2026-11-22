package de.hivemind.create_hivemind.robo.fold;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * 折跃落点选择。
 *
 * <p>规则：目标点正上方 {@code height} 格，加一圈随机水平偏移（{@code spread}），
 * 然后从该位置向下找第一个「本格与上方一格都是空气」的位置，返回该位置上方 2 格的中心。
 * 找不到就退回高空落点——高空落点永远合法，运输蜂会自己往下飞。</p>
 *
 * <p>安全约束：任何读方块的操作前都先检查区块是否已加载。
 * 逻辑蜂允许在未加载区块里存在，但 {@link ServerLevel#getBlockState} 会同步加载区块，
 * 在主线程里可能触发长时间卡顿。</p>
 */
public final class FoldLanding {

    private static final int MAX_SCAN_DOWN = 64;

    public static Vec3 pick(ServerLevel level, Vec3 target, int height, int spread) {
        double angle = level.random.nextDouble() * Math.PI * 2.0;
        double dx = spread <= 0 ? 0.0 : Math.cos(angle) * spread;
        double dz = spread <= 0 ? 0.0 : Math.sin(angle) * spread;

        Vec3 high = target.add(dx, height, dz);
        BlockPos base = BlockPos.containing(high);

        if (!isChunkLoaded(level, base)) {
            return high;
        }

        for (int dy = 0; dy < MAX_SCAN_DOWN; dy++) {
            BlockPos candidate = base.below(dy);
            if (!isChunkLoaded(level, candidate)) {
                return high;
            }
            BlockState here = level.getBlockState(candidate);
            BlockState above = level.getBlockState(candidate.above());
            if (here.isAir() && above.isAir()) {
                return Vec3.atBottomCenterOf(candidate.above(2));
            }
        }

        return high;
    }

    private static boolean isChunkLoaded(ServerLevel level, BlockPos pos) {
        return level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4);
    }

    private FoldLanding() {
    }
}
