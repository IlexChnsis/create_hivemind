package de.hivemind.create_hivemind.robo.fold;

import de.hivemind.create_hivemind.config.CHMServer;
import de.theidler.create_mobile_packages.robo.VirtualRobo;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 折跃判定与执行。
 *
 * <p>只在 {@code RoboBeeBehaviorController#handleNavigateToTarget} 的 TAIL 调用：
 * 此时运输蜂已经朝目标推进了一 tick，位置、速度都已是本 tick 的结果，
 * 折跃后下一 tick 状态机自然继续跑 {@code NAVIGATE_TO_TARGET}，无需改状态机。</p>
 */
public final class FoldController {

    private static final double MIN_ARRIVAL_GAP = 1.5;

    /**
     * @return 本 tick 是否发生了折跃
     */
    public static boolean tryFold(VirtualRobo robo) {
        if (robo == null) {
            return false;
        }
        if (!CHMServer.ENABLE.get()) {
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

        // 新的一段巡航：重置计数
        if (state.isStale(now) || state.targetChanged(targetPos)) {
            state.reset(now, targetPos);
        }
        state.navigationTicks++;
        state.lastSeenTick = now;
        state.lastTarget = targetPos;

        // 先在未加高度偏移的真实目标点上算距离，语义与配置说明一致
        double distance = robo.getCurrentPos().distanceTo(targetPos);
        if (distance < CHMServer.MIN_DISTANCE.get()) {
            return false;
        }
        if (state.navigationTicks < CHMServer.MIN_TRAVEL_TICKS.get()) {
            return false;
        }
        if (state.folds >= CHMServer.MAX_FOLDS_PER_TRIP.get()) {
            return false;
        }
        if (now - state.lastFoldTick < CHMServer.FOLD_COOLDOWN_TICKS.get()) {
            return false;
        }

        int height = CHMServer.ARRIVAL_HEIGHT.get();
        int spread = CHMServer.ARRIVAL_SPREAD.get();

        Vec3 from = robo.getCurrentPos();
        Vec3 landing = FoldLanding.pick(level, targetPos, height, spread);

        // 落点离目标太近会让 isAtTarget(dist < speed) 立刻成立，跳过对齐动画
        if (landing.distanceTo(targetPos.add(0, 2, 0)) < MIN_ARRIVAL_GAP) {
            landing = landing.add(0, height, 0);
        }

        apply(level, robo, from, landing);

        state.markFold(now, targetPos);
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

    /**
     * 供调试使用：折跃一次并返回落点，不受各项阈值限制（仅受总开关约束）。
     */
    @Nullable
    public static Vec3 forceFold(VirtualRobo robo, int height, int spread) {
        if (robo == null || !CHMServer.ENABLE.get()) {
            return null;
        }
        ServerLevel level = robo.getServerLevel();
        Vec3 targetPos = robo.getTargetPosition();
        if (level == null || targetPos == null) {
            return null;
        }
        Vec3 from = robo.getCurrentPos();
        Vec3 landing = FoldLanding.pick(level, targetPos, height, spread);
        apply(level, robo, from, landing);
        FoldState state = FoldStateStore.get(robo);
        state.markFold(level.getGameTime(), targetPos);
        return landing;
    }

    private FoldController() {
    }
}
