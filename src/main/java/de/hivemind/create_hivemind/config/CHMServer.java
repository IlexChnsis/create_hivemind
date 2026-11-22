package de.hivemind.create_hivemind.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 服务端配置：<code>config/create_hivemind-server.toml</code>。
 *
 * <p>所有字段在运行时通过 {@code .get()} 读取，不缓存，保证改配置后重启即生效。</p>
 *
 * <p>折跃语义（0.0.3 起）：起飞时把当前位置记为「出发点」，之后按 {@code checkIntervalTicks}
 * 的间隔探测；当「离出发点超过 {@code minFoldDistance}」且「离目标超过 {@code maxArrivalDistance}」
 * 同时成立时，沿「当前位置 → 目标」连线跳到距目标 {@code maxArrivalDistance} 的点，
 * 并把该落点记为新的出发点。</p>
 */
public final class CHMServer {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue ENABLE_FOR_PLAYERS;
    public static final ModConfigSpec.BooleanValue ENABLE_FOR_PORTS;
    public static final ModConfigSpec.IntValue MIN_FOLD_DISTANCE;
    public static final ModConfigSpec.IntValue MAX_ARRIVAL_DISTANCE;
    public static final ModConfigSpec.IntValue CHECK_INTERVAL_TICKS;
    public static final ModConfigSpec.BooleanValue PARTICLES;
    public static final ModConfigSpec.BooleanValue SOUND;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment(
                "同维度远距离折跃：运输蜂从出发点飞出一定距离后，沿目标方向跳到目标附近，再飞完最后一段并降落。",
                "折跃开关按目标类型分开控制：发往玩家的航班与发往另一个港口的航班可以分别启用或禁用。",
                "Cross-dimension delivery is NOT part of this milestone (M2).")
                .push("fold");

        ENABLE_FOR_PLAYERS = b
                .comment(
                        "对「玩家目标」启用折跃。即运输蜂飞向玩家（地址以 @ 开头解析到的玩家）时是否允许折跃。",
                        "两个开关都为 false 时，运输蜂行为与未安装本模组完全一致。")
                .define("enableForPlayers", true);

        ENABLE_FOR_PORTS = b
                .comment(
                        "对「港口目标」启用折跃。即运输蜂飞向另一个 Robo Bee Port 时是否允许折跃。",
                        "两个开关都为 false 时，运输蜂行为与未安装本模组完全一致。")
                .define("enableForPorts", true);

        MIN_FOLD_DISTANCE = b
                .comment(
                        "最小跃迁距离：当前位置与「出发点」的直线距离超过该值才允许折跃（格）。",
                        "出发点在起飞时记录，之后每次折跃会把落点写成新的出发点。")
                .defineInRange("minFoldDistance", 100, 16, 30_000_000);

        MAX_ARRIVAL_DISTANCE = b
                .comment(
                        "最大到达距离：当前位置距离「目标点」超过该值才允许折跃；",
                        "折跃落点取在「当前位置 → 目标」连线上、距目标正好这么多格的位置。",
                        "该值必须小于 minFoldDistance，否则折跃会在原地反复触发，本模组会自动禁用折跃。")
                .defineInRange("maxArrivalDistance", 64, 4, 30_000_000);

        CHECK_INTERVAL_TICKS = b
                .comment(
                        "探测间隔（tick）。每多少 tick 检查一次折跃条件，20 tick = 1 秒。",
                        "间隔越小折跃点越精确，开销也越高；默认 5 tick（0.25 秒）。")
                .defineInRange("checkIntervalTicks", 5, 1, 200);

        PARTICLES = b
                .comment("折跃时在起点与落点播放传送门粒子。")
                .define("particles", true);

        SOUND = b
                .comment("折跃时在落点播放末影人传送音效。")
                .define("sound", true);

        b.pop();

        SPEC = b.build();
    }

    /**
     * 配置是否自洽。{@code maxArrivalDistance >= minFoldDistance} 时折跃会原地自激，必须禁用。
     */
    public static boolean isUsable() {
        return MAX_ARRIVAL_DISTANCE.get() < MIN_FOLD_DISTANCE.get();
    }

    private CHMServer() {
    }
}
