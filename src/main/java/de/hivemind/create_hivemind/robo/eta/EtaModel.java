package de.hivemind.create_hivemind.robo.eta;

import de.hivemind.create_hivemind.config.CHMServer;
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
 * 所以折跃发生之前，那个数字会严重高估（5000 格默认配置下接近 14 分钟）；折跃发生之后又突然归零。
 * 用户要的口径是：</p>
 *
 * <pre>剩余时间 = 总路程时间 − 折跃路段按正常速度折算的时间</pre>
 *
 * <p>等价于「这段路实际要飞多久」，也就是把即将发生的折跃提前计入。</p>
 *
 * <p>预测规则与 {@code FoldController} 的判定保持一致：</p>
 * <ol>
 *   <li>距离不够 / 总开关关闭 / 次数用尽 → 退回上游公式，不做任何修饰；</li>
 *   <li>还需要补足 {@code minTravelTicks} 时，先把这段时间折算成飞行距离；</li>
 *   <li>补足之后如果剩余距离仍然 ≥ {@code minDistance}，那么下一 tick 必定折跃，
 *       折跃后只剩「落点 → 目标上方 2 格」这段；</li>
 *   <li>补足之后距离已经不够了，则不会折跃，按普通公式算。</li>
 * </ol>
 *
 * <p>落点距离用 {@code sqrt(spread² + (height-2)²)} 估算，即折跃后还要飞的那一段。
 * 与真实落点的差异来自随机水平偏移的方向和地形高度，属于可接受的估算误差；
 * 折跃真正发生后本类自动退回第 1 条，数值不会跳变。</p>
 */
public final class EtaModel {

    private static final double TICKS_PER_SECOND = 20.0;

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
        double distance = robo.getCurrentPos().distanceTo(target);
        int speed = Math.max(1, CMPConfigs.server().beeSpeed.get());

        // 专用客户端或配置尚未加载时不要访问 config，否则 get() 会抛 IllegalStateException
        if (!CHMServer.SPEC.isLoaded()) {
            return baseline(distance, speed);
        }

        if (!CHMServer.ENABLE.get() || distance < CHMServer.MIN_DISTANCE.get()) {
            return baseline(distance, speed);
        }

        FoldState state = FoldStateStore.peek(robo);
        int foldsDone = state == null ? 0 : state.folds();
        if (foldsDone >= CHMServer.MAX_FOLDS_PER_TRIP.get()) {
            return baseline(distance, speed);
        }

        int navigationTicks = state == null ? 0 : state.navigationTicks();
        int waitTicks = Math.max(0, CHMServer.MIN_TRAVEL_TICKS.get() - navigationTicks);
        double waitDistance = waitTicks / TICKS_PER_SECOND * speed;

        // 补足最小飞行时间后已经离目标足够近 → 不会折跃
        if (distance - waitDistance < CHMServer.MIN_DISTANCE.get()) {
            return baseline(distance, speed);
        }

        // 会折跃：先飞 waitDistance，再飞折跃落点到目标的那一小段
        return baseline(waitDistance + arrivalDistance(), speed);
    }

    /** 折跃落点 到「目标上方 2 格」的直线距离。 */
    private static double arrivalDistance() {
        double spread = CHMServer.ARRIVAL_SPREAD.get();
        double height = Math.max(0, CHMServer.ARRIVAL_HEIGHT.get() - 2);
        return Math.sqrt(spread * spread + height * height);
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
