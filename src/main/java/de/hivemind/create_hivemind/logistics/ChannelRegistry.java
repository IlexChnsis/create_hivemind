package de.hivemind.create_hivemind.logistics;

import de.hivemind.create_hivemind.index.CHMDataComponents;
import de.theidler.create_mobile_packages.index.CMPDataComponents;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 便携式仓库管理器的「频道名单」读写。
 *
 * <p>上游只认 {@code CMP_FREQ}（当前活动网络），这里额外维护两件事：</p>
 * <ol>
 *   <li>有哪些网络可选（{@code BOUND_NETWORKS}）</li>
 *   <li>每个网络的<b>固定显示序号</b>（{@code NETWORK_SERIALS}）</li>
 * </ol>
 *
 * <p>序号规则（用户指定）：绑定时取「当前名单里所有序号的最大值 + 1」，从 1 开始。
 * 序号<b>分配后永不改变</b>，即使该网络后来被命名、即使中间有网络被解绑——
 * 解绑才会释放它的序号。</p>
 *
 * <p>举例：绑定 A(1)、B(2) → 把 B 命名为「仓库」→ 再绑定 C，C 得到 3
 * （因为 B 的序号 2 仍占着，即使它现在叫「仓库」而不是「物流网络2」）。</p>
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

    /** 读取序号表。返回可变副本，调用方可安全修改后写回。 */
    public static Map<UUID, Integer> serials(ItemStack stack) {
        return new HashMap<>(stack.getOrDefault(CHMDataComponents.NETWORK_SERIALS, Map.of()));
    }

    /**
     * 该网络的显示序号；没有记录时返回 0（调用方应回退成「物流网络」不带数字）。
     */
    public static int serialOf(ItemStack stack, UUID network) {
        Integer serial = serials(stack).get(network);
        return serial == null ? 0 : serial;
    }

    /** 下一个可用序号 = 当前所有序号的最大值 + 1，从 1 开始。 */
    public static int nextSerial(ItemStack stack) {
        return nextSerial(serials(stack));
    }

    /** 同上，但针对已经取出的序号表，避免重复读组件。 */
    private static int nextSerial(Map<UUID, Integer> serials) {
        int max = 0;
        for (int serial : serials.values()) {
            if (serial > max) {
                max = serial;
            }
        }
        return max + 1;
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

        // 先按本地副本算出序号，再一次性写入，保证「按新增顺序编号且不回退」
        Map<UUID, Integer> serials = serials(stack);
        serials.put(network, nextSerial(serials));

        current.add(network);
        stack.set(CHMDataComponents.BOUND_NETWORKS, List.copyOf(current));
        stack.set(CHMDataComponents.NETWORK_SERIALS, Map.copyOf(serials));
        setActive(stack, network);
        return BindResult.BOUND;
    }

    /**
     * 解绑一个网络。若解绑的是当前网络，自动切到名单里剩下的第一个；名单空了就清掉 {@code CMP_FREQ}。
     *
     * <p>序号随之释放，下一个新绑定的网络可能复用这个号。</p>
     */
    public static BindResult unbind(ItemStack stack, UUID network) {
        List<UUID> current = bound(stack);
        if (!current.contains(network)) {
            return BindResult.NOT_BOUND;
        }
        current.remove(network);
        stack.set(CHMDataComponents.BOUND_NETWORKS, List.copyOf(current));

        Map<UUID, Integer> serials = serials(stack);
        if (serials.remove(network) != null) {
            if (serials.isEmpty()) {
                stack.remove(CHMDataComponents.NETWORK_SERIALS);
            } else {
                stack.set(CHMDataComponents.NETWORK_SERIALS, Map.copyOf(serials));
            }
        }

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
     * 服务端安全的名字回退：只按固定序号生成「物流网络N」。
     *
     * <p>真正的网络名由客户端从 CMP 的 {@code ClientNetworkDataStorage} 读取
     * （见 {@code PortableStockTickerChannelScreenMixin#chm$rawName}），
     * 因为那个类带 {@code @OnlyIn(Dist.CLIENT)}，服务端不能碰。</p>
     */
    public static String fallbackName(ItemStack stack, UUID network) {
        int serial = serialOf(stack, network);
        return serial <= 0 ? "\u7269\u6d41\u7f51\u7edc" : "\u7269\u6d41\u7f51\u7edc" + serial;
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
