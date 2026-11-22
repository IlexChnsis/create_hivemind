package de.hivemind.create_hivemind.robo.fold;

import de.hivemind.create_hivemind.config.CHMServer;
import de.theidler.create_mobile_packages.robo.BeePortBlockEntityTarget;
import de.theidler.create_mobile_packages.robo.PlayerTarget;
import de.theidler.create_mobile_packages.robo.RoboTarget;
import org.jetbrains.annotations.Nullable;

/**
 * 折跃开关的类型判定。
 *
 * <p>运输蜂的目标只有两种来源（见 CMP 的 {@link RoboTarget} 实现）：</p>
 * <ul>
 *   <li>{@link PlayerTarget} —— 地址解析到玩家；</li>
 *   <li>{@link BeePortBlockEntityTarget} —— 解析到另一个 Robo Bee Port。</li>
 * </ul>
 *
 * <p>配置里对这两类分别提供 {@code enableForPlayers} / {@code enableForPorts}，
 * 因此「能不能折跃」必须结合目标类型判断。折跃执行（{@link FoldController}）与
 * ETA 预测（{@code EtaModel}）都走这里，保证两处口径一致——
 * 否则会出现「显示按会折跃算、实际不折跃」的错位。</p>
 *
 * <p><b>为什么用 instanceof 而不是 {@code asPlayer()} / {@code asBeePortBlockEntity()}：</b>
 * CMP 的 {@code BeePortBlockEntityTarget.asBeePortBlockEntity()} 在目标区块未加载时返回 {@code null}
 * （见其实现里的 {@code level.hasChunk(...)} 判断）。而远距离投递恰恰常常发生在目标区块未加载时——
 * 若用返回值判类型，港口目标会被误判成「未知类型」从而永远不折跃，正好废掉本模组的主要场景。
 * 类型判断只看对象本身，不受区块加载状态影响。</p>
 */
public final class FoldGate {

    public enum Kind {
        /** 目标是玩家。 */
        PLAYER,
        /** 目标是另一个港口。 */
        PORT,
        /** 目标为空，或者不是已知的两类（保守处理为不折跃）。 */
        UNKNOWN
    }

    public static Kind kindOf(@Nullable RoboTarget target) {
        if (target == null) {
            return Kind.UNKNOWN;
        }
        if (target instanceof PlayerTarget) {
            return Kind.PLAYER;
        }
        if (target instanceof BeePortBlockEntityTarget) {
            return Kind.PORT;
        }
        return Kind.UNKNOWN;
    }

    /**
     * 当前目标是否允许折跃。
     *
     * <p>未知目标类型一律返回 false：宁可少折跃，也不要在上游将来新增目标类型时
     * 因为默认开启而产生预料之外的行为。</p>
     */
    public static boolean allows(@Nullable RoboTarget target) {
        // 专用客户端或配置尚未加载时不能读取 config，否则 get() 会抛 IllegalStateException
        if (!CHMServer.SPEC.isLoaded()) {
            return false;
        }
        if (!CHMServer.isUsable()) {
            return false;
        }
        return switch (kindOf(target)) {
            case PLAYER -> CHMServer.ENABLE_FOR_PLAYERS.get();
            case PORT -> CHMServer.ENABLE_FOR_PORTS.get();
            case UNKNOWN -> false;
        };
    }

    private FoldGate() {
    }
}
