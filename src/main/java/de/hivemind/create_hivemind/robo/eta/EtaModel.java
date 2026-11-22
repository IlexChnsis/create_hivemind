package de.hivemind.create_hivemind.robo.eta;

import de.hivemind.create_hivemind.config.CHMServer;
import de.hivemind.create_hivemind.robo.fold.FoldGate;
import de.hivemind.create_hivemind.robo.fold.FoldState;
import de.hivemind.create_hivemind.robo.fold.FoldStateStore;
import de.theidler.create_mobile_packages.blocks.bee_port.RoboRequest;
import de.theidler.create_mobile_packages.index.config.CMPConfigs;
import de.theidler.create_mobile_packages.robo.RoboTarget;
import de.theidler.create_mobile_packages.robo.VirtualRobo;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * ETA 修正：让玩家看到的剩余时间是「实际预期飞行时间」，而不是「剩余直线距离 / 速度」。
 *
 * <p>上游 {@code CMPHelper.calcETA} 只做 {@code 距离 / beeSpeed}。折跃会让运输蜂瞬间走完一大段路，
 * 所以折跃发生之前，那个数字会严重高估（5000 格默认配置下接近 14 分钟）；折跃发生后又会突然归零。</p>
 *
 * <p>0.0.3 的锚点式折跃让这件事变得很干净。设：</p>
 * <ul>
 *   <li>{@code dT} = 当前位置到目标的距离</li>
 *   <li>{@code dA} = 当前位置到出发点（anchor）的距离</li>
 * </ul>
 *
 * <p>折跃在 {@code dA > minFoldDistance} 且 {@code dT > maxArrivalDistance} 时触发，
 * 落点距目标正好 {@code maxArrivalDistance}。所以：</p>
 *
 * <pre>
 *   还要先飞  = max(0, minFoldDistance - dA)
 *   折跃点离目标 = dT - 还要先飞
 *   若 折跃点离目标 &gt; maxArrivalDistance → 会折跃 → 剩余 = 还要先飞 + maxArrivalDistance
 *   否则                              → 不折跃 → 剩余 = dT
 * </pre>
 *
 * <p>这个式子有个好处：折跃前后数值是连续的。折跃瞬间 {@code dA} 归零、{@code dT} 变成
 * {@code maxArrivalDistance}，{@code 还要先飞} 变成 {@code minFoldDistance}，
 * 而 {@code dT - 还要先飞} 变成负数 → 走「不折跃」分支 → 剩余仍是 {@code dT}。
 * 因此玩家看到的倒计时平滑下降，不会跳变。</p>
 */
public final class EtaModel {

    /** 与上游一致：无法计算时返回 -1。 */
    private static int baseline(double distance, int speed) {
        if (distance <= 0.0) {
            return 1;
        }
        return (int) (distance / speed) + 1;
    }

    /**
     * @return 预测的剩余秒数；目标未知时返回 -1
     */
    public static int predictedSeconds(@Nullable VirtualRobo robo) {
        if (robo == null) {
            return -1;
        }
        Vec3 target = robo.getTargetPosition();
        if (target == null) {
            return -1;
        }

        Vec3 here = robo.getCurrentPos();
        int speed = Math.max(1, CMPConfigs.server().beeSpeed.get());
        double toTarget = here.distanceTo(target);

        // 目标类型对应的开关关闭 / 配置未加载 / 数值不自洽 → 显示值完全等于上游公式。
        // 与 FoldController 走同一个 FoldGate，避免「按会折跃计算但实际不折跃」。
        if (!FoldGate.allows(robo.getTarget())) {
            return baseline(toTarget, speed);
        }

        FoldState state = FoldStateStore.peek(robo);
        // 本 tick 还没走过折跃判定（例如 Toast 在 handleNavigateToTarget 开头被调用）时，
        // 把当前位置当成出发点，得到的正是「这一段会怎么飞」的预期。
        Vec3 anchor = state != null && state.anchor() != null ? state.anchor() : here;

        double minFoldDistance = CHMServer.MIN_FOLD_DISTANCE.get();
        double arrival = CHMServer.MAX_ARRIVAL_DISTANCE.get();

        double flyBeforeFold = Math.max(0.0, minFoldDistance - here.distanceTo(anchor));
        double distanceAtFold = toTarget - flyBeforeFold;

        double remaining = toTarget;
        if (distanceAtFold > arrival) {
            remaining = flyBeforeFold + arrival;
        }

        return baseline(remaining, speed);
    }

    /**
     * 写入 target 的 ETA。{@code PlayerTarget.updateEtaToast} 读的是 target 上的值，
     * 所以显示路径必须走这个入口。
     */
    public static void applyToTarget(@Nullable VirtualRobo robo) {
        if (robo == null) {
            return;
        }
        RoboTarget target = robo.getTarget();
        if (target == null) {
            return;
        }
        int eta = predictedSeconds(robo);
        if (eta >= 0) {
            target.setETA(eta);
        }
    }

    /**
     * 按上游 {@code updateEta()} 的选择顺序写入：有 request 写 request，否则写 target。
     */
    public static void apply(@Nullable VirtualRobo robo) {
        if (robo == null) {
            return;
        }
        int eta = predictedSeconds(robo);
        if (eta < 0) {
            return;
        }
        RoboRequest request = robo.getRequest();
        if (request != null) {
            request.setEta(eta);
            return;
        }
        RoboTarget target = robo.getTarget();
        if (target != null) {
            target.setETA(eta);
        }
    }

    private EtaModel() {
    }
}
