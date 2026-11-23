package de.hivemind.create_hivemind.index;

import de.hivemind.create_hivemind.CreateHiveMind;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * 本模组附加在 CMP 便携式仓库管理器上的数据组件。
 *
 * <p>不修改 CMP 自己的组件，只往同一个 ItemStack 上追加一个：已绑定的网络列表。</p>
 *
 * <p>「当前活动网络」仍然复用 CMP 自己的 {@code CMP_FREQ}，所以上游所有
 * 请求库存、下单、校验权限的逻辑一行都不用改。</p>
 *
 * <p>网络<b>名字</b>不在这里存：直接用 CMP 自己的网络名（{@code NetworkSettingsScreen} 改的那个），
 * 避免出现「终端上的别名」和「网络真名」两套说法。</p>
 */
public final class CHMDataComponents {

    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, CreateHiveMind.MOD_ID);

    /** 最大绑定数量。与下拉列表可视行数一致，超过需要滚动，故设 8。 */
    public static final int MAX_BOUND_NETWORKS = 8;

    /** 已绑定的物流网络，顺序即绑定顺序，未命名的网络按此顺序编号。 */
    public static final DataComponentType<List<UUID>> BOUND_NETWORKS = register(
            "bound_networks",
            builder -> builder
                    .persistent(UUIDUtil.STRING_CODEC.listOf())
                    // 必须 networkSynchronized：客户端要靠它渲染频道列表。
                    // 只写 persistent 的话，客户端读到的永远是空列表。
                    .networkSynchronized(
                            net.createmod.catnip.codecs.stream.CatnipStreamCodecBuilders.list(UUIDUtil.STREAM_CODEC))
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
