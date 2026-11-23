package de.hivemind.create_hivemind.index;

import de.hivemind.create_hivemind.CreateHiveMind;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.createmod.catnip.codecs.stream.CatnipStreamCodecBuilders;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * 本模组附加在 CMP 便携式仓库管理器上的数据组件。
 *
 * <p>三个组件，全部挂在<b>物品</b>上，因此是「管理器 specific」的：</p>
 * <ul>
 *   <li>{@link #BOUND_NETWORKS} —— 已绑定的物流网络，顺序即绑定顺序；</li>
 *   <li>{@link #NETWORK_SERIALS} —— 网络 UUID → <b>固定显示序号</b>。
 *       绑定时分配，之后永不改变；解绑才释放。默认名「物流网络N」就是它；</li>
 *   <li>{@link #NETWORK_NAMES} —— 网络 UUID → <b>本地别名</b>。玩家自己起的名字，
 *       只对这一个管理器生效。</li>
 * </ul>
 *
 * <p><b>关于别名的语义（重要）：</b>这不是 CMP 的 {@code LogisticsNetwork.name}。
 * 那个是网络的全局名，写在网络数据上、全服共享、且只有 owner 能改。
 * 这里的别名随物品走，任何玩家拿到这个管理器都看到同一组别名，
 * 但不同管理器之间互不影响——这正是「每个玩家用自己的别名」的实现方式。</p>
 *
 * <p>别名会覆盖显示，但<b>不影响</b>物流行为：寻址、下单、权限校验全部仍按网络 UUID 走。</p>
 *
 * <p>「当前活动网络」仍然复用 CMP 自己的 {@code CMP_FREQ}，所以上游所有
 * 请求库存、下单、校验权限的逻辑一行都不用改。</p>
 */
public final class CHMDataComponents {

    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, CreateHiveMind.MOD_ID);

    /** 最大绑定数量。与下拉列表可视行数一致，超过需要滚动，故设 8。 */
    public static final int MAX_BOUND_NETWORKS = 8;

    public static final DataComponentType<List<UUID>> BOUND_NETWORKS = register(
            "bound_networks",
            builder -> builder
                    .persistent(UUIDUtil.STRING_CODEC.listOf())
                    // 必须 networkSynchronized：客户端要靠它渲染频道列表
                    .networkSynchronized(CatnipStreamCodecBuilders.list(UUIDUtil.STREAM_CODEC))
    );

    /** 网络 UUID → 固定显示序号。同样需要同步，因为标题与下拉在客户端渲染。 */
    public static final DataComponentType<Map<UUID, Integer>> NETWORK_SERIALS = register(
            "network_serials",
            builder -> builder
                    .persistent(com.mojang.serialization.Codec.unboundedMap(UUIDUtil.STRING_CODEC,
                            com.mojang.serialization.Codec.INT))
                    .networkSynchronized(
                            ByteBufCodecs.map(HashMap::new, UUIDUtil.STREAM_CODEC, ByteBufCodecs.INT))
    );

    /** 网络 UUID → 本地别名。没有条目表示使用序号默认名。 */
    public static final DataComponentType<Map<UUID, String>> NETWORK_NAMES = register(
            "network_names",
            builder -> builder
                    .persistent(com.mojang.serialization.Codec.unboundedMap(UUIDUtil.STRING_CODEC,
                            com.mojang.serialization.Codec.STRING))
                    .networkSynchronized(
                            ByteBufCodecs.map(HashMap::new, UUIDUtil.STREAM_CODEC, ByteBufCodecs.STRING_UTF8))
    );

    private static <T> DataComponentType<T> register(String name,
                                                     UnaryOperator<DataComponentType.Builder<T>> builder) {
        DataComponentType<T> type = builder.apply(DataComponentType.builder()).build();
        DATA_COMPONENTS.register(name, () -> type);
        return type;
    }

    public static void register(IEventBus modEventBus) {
        DATA_COMPONENTS.register(modEventBus);
    }

    private CHMDataComponents() {
    }
}
