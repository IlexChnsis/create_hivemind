package de.hivemind.create_hivemind.logistics;

import de.hivemind.create_hivemind.index.CHMDataComponents;
import de.theidler.create_mobile_packages.index.CMPDataComponents;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 便携式仓库管理器的「频道名单」读写。
 *
 * <p>上游只认 {@code CMP_FREQ}（当前活动网络），这里额外维护「有哪些网络可选」。
 * 所有方法直接操作 {@link ItemStack} 的数据组件，不缓存，与 CMP 自己的读写方式一致。</p>
 *
 * <p>本类<b>不</b>处理网络显示名：名字统一用 CMP 的网络名，只在客户端界面里读取。</p>
 */
public final class ChannelRegistry {

    /** CMP 存放当前活动网络的数据组件。直接复用，保持单一真相。 */
    private static final DataComponentType<CustomData> CMP_FREQ = CMPDataComponents.CMP_FREQ;

    public static List<UUID> bound(ItemStack stack) {
        List<UUID> raw = stack.getOrDefault(CHMDataComponents.BOUND_NETWORKS, List.of());
        List<UUID> out = new ArrayList<>(raw.size());
        for (UUID id : raw) {
            if (id != null && !out.contains(id)) {
                out.add(id);
            }
        }
        return out;
    }

    public static boolean isBound(ItemStack stack, @Nullable UUID network) {
        return network != null && bound(stack).contains(network);
    }

    /** 当前活动网络：仍然读 CMP 自己的 {@code CMP_FREQ}。 */
    public static @Nullable UUID active(ItemStack stack) {
        CustomData data = stack.get(CMP_FREQ);
        if (data == null) {
            return null;
        }
        CompoundTag tag = data.copyTag();
        return tag.hasUUID("Freq") ? tag.getUUID("Freq") : null;
    }

    /**
     * 绑定一个网络。已在名单里则改为切到它，让玩家重复右键时有可见反馈。
     */
    public static BindResult bind(ItemStack stack, UUID network) {
        List<UUID> current = bound(stack);
        if (current.contains(network)) {
            setActive(stack, network);
            return BindResult.ALREADY_BOUND;
        }
        if (current.size() >= CHMDataComponents.MAX_BOUND_NETWORKS) {
            return BindResult.FULL;
        }
        current.add(network);
        stack.set(CHMDataComponents.BOUND_NETWORKS, List.copyOf(current));
        setActive(stack, network);
        return BindResult.BOUND;
    }

    /**
     * 解绑一个网络。若解绑的是当前网络，自动切到名单里剩下的第一个；名单空了就清掉 {@code CMP_FREQ}。
     */
    public static BindResult unbind(ItemStack stack, UUID network) {
        List<UUID> current = bound(stack);
        if (!current.contains(network)) {
            return BindResult.NOT_BOUND;
        }
        current.remove(network);
        stack.set(CHMDataComponents.BOUND_NETWORKS, List.copyOf(current));

        if (network.equals(active(stack))) {
            if (current.isEmpty()) {
                stack.remove(CMP_FREQ);
            } else {
                setActive(stack, current.get(0));
            }
        }
        return BindResult.UNBOUND;
    }

    /** 直接改写 CMP 的 {@code CMP_FREQ}。权限校验由调用方负责。 */
    public static void setActive(ItemStack stack, @Nullable UUID network) {
        if (network == null) {
            stack.remove(CMP_FREQ);
            return;
        }
        CustomData existing = stack.get(CMP_FREQ);
        CompoundTag tag = existing == null ? new CompoundTag() : existing.copyTag();
        tag.putUUID("Freq", network);
        stack.set(CMP_FREQ, CustomData.of(tag));
    }

    /** 按名单顺序切到下一个 / 上一个。名单不足两个时返回 null。 */
    public static @Nullable UUID cycle(ItemStack stack, boolean forward) {
        List<UUID> current = bound(stack);
        if (current.size() < 2) {
            return null;
        }
        UUID active = active(stack);
        int index = active == null ? -1 : current.indexOf(active);
        int next = index < 0
                ? 0
                : Math.floorMod(index + (forward ? 1 : -1), current.size());
        UUID target = current.get(next);
        setActive(stack, target);
        return target;
    }

    /**
     * 服务端安全的名字回退：只按名单顺序生成「物流网络N」，不依赖任何客户端数据。
     *
     * <p>真正的网络名由客户端从 CMP 的 {@code ClientNetworkDataStorage} 读取
     * （见 {@code PortableStockTickerChannelScreenMixin#chm$name}），
     * 因为那个类带 {@code @OnlyIn(Dist.CLIENT)}，服务端不能碰。</p>
     */
    public static String fallbackName(ItemStack stack, UUID network) {
        int index = bound(stack).indexOf(network);
        return index < 0 ? "\u7269\u6d41\u7f51\u7edc" : "\u7269\u6d41\u7f51\u7edc" + (index + 1);
    }

    public enum BindResult {
        /** 新加入名单 */
        BOUND,
        /** 已在名单，改为切到它 */
        ALREADY_BOUND,
        /** 名单已满 */
        FULL,
        /** 成功移出名单 */
        UNBOUND,
        /** 本来就不在名单里 */
        NOT_BOUND
    }

    private ChannelRegistry() {
    }
}
