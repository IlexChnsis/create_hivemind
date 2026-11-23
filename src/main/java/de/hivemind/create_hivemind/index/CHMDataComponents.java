package de.hivemind.create_hivemind.index;

import com.mojang.serialization.Codec;
import de.hivemind.create_hivemind.CreateHiveMind;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * 本模组附加在 CMP 便携式仓库管理器上的数据组件。
 *
 * <p>不修改 CMP 自己的组件，只往同一个 ItemStack 上追加两个：</p>
 * <ul>
 *   <li>{@link #BOUND_NETWORKS} —— 已绑定的物流网络列表，<b>顺序即玩家绑定顺序</b>，
 *       未命名的网络按这个顺序生成「物流网络N」；</li>
 *   <li>{@link #NETWORK_NAMES} —— 网络 UUID → 自定义名。没有条目即视为未命名。</li>
 * </ul>
 *
 * <p>「当前活动网络」仍然复用 CMP 自己的 {@code CMP_FREQ}，所以上游所有
 * 请求库存、下单、校验权限的逻辑一行都不用改。</p>
 */
public final class CHMDataComponents {

    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, CreateHiveMind.MOD_ID);

    /** 最大绑定数量。与下拉列表可视行数一致，超过需要滚动，故设 8。 */
    public static final int MAX_BOUND_NETWORKS = 8;

    /** 网络 UUID → 自定义名称。玩家改名后写入，清空即删除条目回到默认名。 */
    public static final DataComponentType<Map<UUID, String>> NETWORK_NAMES = register(
            "network_names",
            builder -> builder.persistent(Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.STRING))
    );

    public static final DataComponentType<List<UUID>> BOUND_NETWORKS = register(
            "bound_networks",
            builder -> builder
                    .persistent(UUIDUtil.STRING_CODEC.listOf())
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
