package de.hivemind.create_hivemind.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 服务端配置：<code>config/create_hivemind-server.toml</code>。
 *
 * <p>所有字段在运行时通过 {@code .get()} 读取，不缓存，保证改配置后重启即生效。</p>
 */
public final class CHMServer {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue ENABLE;
    public static final ModConfigSpec.IntValue MIN_DISTANCE;
    public static final ModConfigSpec.IntValue MIN_TRAVEL_TICKS;
    public static final ModConfigSpec.IntValue ARRIVAL_HEIGHT;
    public static final ModConfigSpec.IntValue ARRIVAL_SPREAD;
    public static final ModConfigSpec.IntValue MAX_FOLDS_PER_TRIP;
    public static final ModConfigSpec.IntValue FOLD_COOLDOWN_TICKS;
    public static final ModConfigSpec.BooleanValue PARTICLES;
    public static final ModConfigSpec.BooleanValue SOUND;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment(
                "同维度远距离折跃：运输蜂先正常飞一段，剩余距离仍然很远时直接跳到目标附近，再飞完最后一段并降落。",
                "Cross-dimension delivery is NOT part of this milestone (M2).")
                .push("fold");

        ENABLE = b
                .comment("总开关。false 时运输蜂行为与未安装本模组完全一致。")
                .define("enable", true);

        MIN_DISTANCE = b
                .comment("剩余直线距离超过该值才考虑折跃（格）。")
                .defineInRange("minDistance", 512, 0, 30_000_000);

        MIN_TRAVEL_TICKS = b
                .comment("起飞进入巡航后，至少先飞这么多 tick 才允许折跃。20 tick = 1 秒。")
                .defineInRange("minTravelTicks", 60, 0, 72_000);

        ARRIVAL_HEIGHT = b
                .comment("落点相对目标点的高度（格）。越高越安全，但要多飞一会儿。")
                .defineInRange("arrivalHeight", 32, 4, 256);

        ARRIVAL_SPREAD = b
                .comment("落点水平随机偏移半径（格）。为 0 时正好在目标正上方，容易立刻判定到达。")
                .defineInRange("arrivalSpread", 12, 0, 256);

        MAX_FOLDS_PER_TRIP = b
                .comment("单次巡航内最多折跃几次，防止配置过激导致来回瞬移。")
                .defineInRange("maxFoldsPerTrip", 2, 1, 32);

        FOLD_COOLDOWN_TICKS = b
                .comment("两次折跃之间的最小间隔（tick）。")
                .defineInRange("foldCooldownTicks", 40, 0, 72_000);

        PARTICLES = b
                .comment("折跃时在起点与落点播放传送门粒子。")
                .define("particles", true);

        SOUND = b
                .comment("折跃时在落点播放末影人传送音效。")
                .define("sound", true);

        b.pop();

        SPEC = b.build();
    }

    private CHMServer() {
    }
}
