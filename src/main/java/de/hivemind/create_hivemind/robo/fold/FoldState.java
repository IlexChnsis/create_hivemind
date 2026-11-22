package de.hivemind.create_hivemind.robo.fold;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 单只逻辑蜂的折跃状态。
 *
 * <p>状态不落盘：运输蜂被移除后条目由 {@link FoldStateStore} 的弱引用自动回收。
 * 重进存档后运输蜂会重新计数，仅在极端情况下多给一次折跃机会，不影响正确性。</p>
 */
public final class FoldState {

    /** 两次 {@code handleNavigateToTarget} 之间超过这么久，视为新的一段巡航。 */
    private static final long STALE_TICKS = 40L;

    /** 目标点位移超过这个距离，视为换了目的地。 */
    private static final double TARGET_CHANGE_DISTANCE_SQR = 16.0 * 16.0;

    long lastSeenTick;

    long lastFoldTick = Long.MIN_VALUE / 4;

    int folds;

    int navigationTicks;

    @Nullable
    Vec3 lastTarget;

    /**
     * @return 上一 tick 之后停了一段时间（例如降落、交付、被别的状态接管）
     */
    boolean isStale(long now) {
        return lastTarget == null || now - lastSeenTick > STALE_TICKS;
    }

    boolean targetChanged(Vec3 target) {
        return lastTarget == null || lastTarget.distanceToSqr(target) > TARGET_CHANGE_DISTANCE_SQR;
    }

    void reset(long now, @Nullable Vec3 target) {
        folds = 0;
        navigationTicks = 0;
        lastFoldTick = Long.MIN_VALUE / 4;
        lastSeenTick = now;
        lastTarget = target;
    }

    void markFold(long now, Vec3 target) {
        folds++;
        lastFoldTick = now;
        lastSeenTick = now;
        lastTarget = target;
    }

    public int folds() {
        return folds;
    }

    public int navigationTicks() {
        return navigationTicks;
    }
}
