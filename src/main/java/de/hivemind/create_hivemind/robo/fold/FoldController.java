package de.hivemind.create_hivemind.robo.fold;

import de.hivemind.create_hivemind.config.CHMServer;
import de.theidler.create_mobile_packages.robo.VirtualRobo;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * 折跃判定与执行。
 *
 * <p>只在 {@code RoboBeeBehaviorController#handleNavigateToTarget} 的 TAIL 调用：
 * 此时运输蜂已经朝目标推进了一 tick，位置已是本 tick 的结果，
 * 折跃后下一 tick 状态机自然继续跑 {@code NAVIGATE_TO_TARGET}，无需改状态机。</p>
 *
 * <p>判定条件（两者同时成立才折跃）：</p>
 * <ol>
 *   <li>当前位置 → 出发点 的距离 &gt; {@code minFoldDistance}</li>
 *   <li>当前位置 → 目标点 的距离 &gt; {@code maxArrivalDistance}</li>
 * </ol>
 *
 * <p>落点：连线（当前位置 → 目标点）上距目标正好 {@code maxArrivalDistance} 的点。
 * 折跃后落点成为新的出发点，因此下一轮检测的基准随之前移，
 * 一次航程可以连续折跃多次，直到离目标足够近为止。</p>
 */
public final class FoldController {

    /**
     * @return 本 tick 是否发生了折跃
     */
    public static boolean tryFold(VirtualRobo robo) {
        if (robo == null) {
            return false;
        }
        if (!CHMServer.ENABLE.get() || !CHMServer.isUsable()) {
            return false;
        }

        ServerLevel level = robo.getServerLevel();
        if (level == null) {
            return false;
        }

        Vec3 targetPos = robo.getTargetPosition();
        if (targetPos == null) {
            return false;
        }

        FoldState state = FoldStateStore.get(robo);
        long now = level.getGameTime();
        Vec3 here = robo.getCurrentPos();

        // 新的一段巡航：把当前位置记为出发点
        if (!state.hasAnchor() || state.isStale(now) || state.targetChanged(targetPos)) {
            state.beginTrip(now, here, targetPos);
            return false;
        }
        state.observe(now, targetPos);

        // 探测间隔：没到下一次检查时间就直接返回
        int interval = CHMServer.CHECK_INTERVAL_TICKS.get();
        if (now - state.lastCheckTick() < interval) {
            return false;
        }
        state.markChecked(now);

        Vec3 anchor = state.anchor();
        if (anchor == null) {
            return false;
        }

        double fromStart = here.distanceTo(anchor);
        if (fromStart <= CHMServer.MIN_FOLD_DISTANCE.get()) {
            return false;
        }

        double toTarget = here.distanceTo(targetPos);
        double arrival = CHMServer.MAX_ARRIVAL_DISTANCE.get();
        if (toTarget <= arrival) {
            return false;
        }

        Vec3 landing = FoldLanding.pointTowardTarget(here, targetPos, arrival);
        apply(level, robo, here, landing);
        state.markFold(now, landing, targetPos);
        return true;
    }

    private static void apply(ServerLevel level, VirtualRobo robo, Vec3 from, Vec3 landing) {
        robo.setPos(landing);
        // 清掉残余速度：否则折跃后第一 tick 会因为旧 targetVelocity 再挪一小段，观感抖动
        robo.setTargetVelocity(Vec3.ZERO);

        if (CHMServer.PARTICLES.get()) {
            level.sendParticles(ParticleTypes.PORTAL,
                    from.x, from.y, from.z,
                    24, 0.4, 0.4, 0.4, 0.6);
            level.sendParticles(ParticleTypes.PORTAL,
                    landing.x, landing.y, landing.z,
                    40, 0.6, 0.6, 0.6, 0.8);
        }

        if (CHMServer.SOUND.get()) {
            level.playSound(null, landing.x, landing.y, landing.z,
                    SoundEvents.ENDERMAN_TELEPORT, SoundSource.NEUTRAL, 0.7f, 1.2f);
        }
    }

    private FoldController() {
    }
}
