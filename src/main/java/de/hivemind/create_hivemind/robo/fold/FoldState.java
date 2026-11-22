package de.hivemind.create_hivemind.robo.fold;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 单只逻辑蜂的折跃状态。
 *
 * <p>核心是 <b>anchor（出发点）</b>：起飞时记为当前位置，之后每次折跃成功就把落点写成新的 anchor。
 * 折跃判定里的「最小跃迁距离」始终是相对于这个 anchor 量的，所以连续折跃时基准会一步步前移。</p>
 *
 * <p>状态不落盘：运输蜂被移除后条目由 {@link FoldStateStore} 的弱引用自动回收。
 * 重进存档后 anchor 会重新落在当前位置，相当于把这一段重新算作起点，不影响正确性。</p>
 */
public final class FoldState {

    /** 两次 {@code handleNavigateToTarget} 之间超过这么久，视为新的一段巡航。 */
    private static final long STALE_TICKS = 40L;

    /** 目标点位移超过这个距离，视为换了目的地。 */
    private static final double TARGET_CHANGE_DISTANCE_SQR = 16.0 * 16.0;

    /** 保证首次检查立即执行：now - 这个值 必然大于任何探测间隔。 */
    private static final long NEVER_CHECKED = Long.MIN_VALUE / 4;

    private long lastSeenTick;

    private long lastCheckTick = NEVER_CHECKED;

    private int folds;

    @Nullable
    private Vec3 anchor;

    @Nullable
    private Vec3 lastTarget;

    public boolean hasAnchor() {
        return anchor != null;
    }

    public @Nullable Vec3 anchor() {
        return anchor;
    }

    public int folds() {
        return folds;
    }

    public long lastCheckTick() {
        return lastCheckTick;
    }

    /**
     * @return 上一 tick 之后停了一段时间（例如降落、交付、被别的状态接管）
     */
    boolean isStale(long now) {
        return lastTarget == null || now - lastSeenTick > STALE_TICKS;
    }

    boolean targetChanged(Vec3 target) {
        return lastTarget == null || lastTarget.distanceToSqr(target) > TARGET_CHANGE_DISTANCE_SQR;
    }

    /** 开始新的一段巡航：anchor 落在当前位置。 */
    void beginTrip(long now, Vec3 from, Vec3 target) {
        anchor = from;
        folds = 0;
        lastCheckTick = NEVER_CHECKED;
        lastSeenTick = now;
        lastTarget = target;
    }

    /** 每 tick 都要调，只更新「还活着」的标记，不参与探测间隔。 */
    void observe(long now, Vec3 target) {
        lastSeenTick = now;
        lastTarget = target;
    }

    void markChecked(long now) {
        lastCheckTick = now;
    }

    void markFold(long now, Vec3 landing, Vec3 target) {
        anchor = landing;
        folds++;
        lastCheckTick = now;
        lastSeenTick = now;
        lastTarget = target;
    }
}
