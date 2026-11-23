package de.hivemind.create_hivemind.index;

import de.hivemind.create_hivemind.CreateHiveMind;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
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
 * <p>不修改 CMP 自己的组件，只往同一个 ItemStack 上追加两个：</p>
 * <ul>
 *   <li>{@link #BOUND_NETWORKS} —— 已绑定的物流网络，顺序即绑定顺序；</li>
 *   <li>{@link #NETWORK_SERIALS} —— 网络 UUID → <b>固定的显示序号</b>。
 *       序号在绑定时分配，之后永不改变；解绑才释放。默认名「物流网络N」就是它。</li>
 * </ul>
 *
 * <p>「当前活动网络」仍然复用 CMP 自己的 {@code CMP_FREQ}，所以上游所有
 * 请求库存、下单、校验权限的逻辑一行都不用改。</p>
 *
 * <p>网络<b>名字</b>不在这里存：直接用 CMP 自己的网络名（{@code NetworkSettingsScreen} 改的那个）。
 * 这里只存「没有名字时该显示几号」。</p>
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
                    .networkSynchronized(
                            net.createmod.catnip.codecs.stream.CatnipStreamCodecBuilders.list(UUIDUtil.STREAM_CODEC))
    );

    /**
     * 网络 UUID → 固定显示序号。同样需要同步到客户端，因为标题和下拉列表在客户端渲染。
     */
    public static final DataComponentType<Map<UUID, Integer>> NETWORK_SERIALS = register(
            "network_serials",
            builder -> builder
                    .persistent(com.mojang.serialization.Codec.unboundedMap(UUIDUtil.STRING_CODEC,
                            com.mojang.serialization.Codec.INT))
                    .networkSynchronized(
                            ByteBufCodecs.map(HashMap::new, UUIDUtil.STREAM_CODEC, ByteBufCodecs.INT))
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
