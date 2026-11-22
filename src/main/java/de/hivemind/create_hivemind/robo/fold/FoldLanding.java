package de.hivemind.create_hivemind.robo.fold;

import net.minecraft.world.phys.Vec3;

/**
 * 折跃落点：纯几何，不做地形修正。
 *
 * <p>规则来自需求原文——沿着「当前位置 → 目标点」这条连线，落在连线上距目标
 * {@code distanceFromTarget} 的那个点上。因此落点永远在当前位置与目标之间，
 * 不会往回跳。</p>
 *
 * <p>注意：这里不检查落点是否卡在方块里。逻辑蜂本身没有碰撞（{@code noPhysics}），
 * 表现实体每 tick 由 {@code syncFromVirtual} 强行同步位置，所以卡在山体里只会有一瞬间的视觉瑕疵，
 * 之后它继续朝目标飞、会自己出来。若以后要做「安全落点」，应该在这里加，而不是改调用方。</p>
 */
public final class FoldLanding {

    private static final double MIN_LINE_LENGTH = 1.0E-6;

    /**
     * @param from               当前位置（必须在目标之外，且离目标比 {@code distanceFromTarget} 远）
     * @param target             目标点
     * @param distanceFromTarget 落点与目标之间的距离
     * @return 连线上的落点
     */
    public static Vec3 pointTowardTarget(Vec3 from, Vec3 target, double distanceFromTarget) {
        Vec3 awayFromTarget = from.subtract(target);
        double length = awayFromTarget.length();
        if (length < MIN_LINE_LENGTH) {
            // 已经和目标重合，没什么可折的
            return target;
        }
        return target.add(awayFromTarget.scale(distanceFromTarget / length));
    }

    private FoldLanding() {
    }
}
