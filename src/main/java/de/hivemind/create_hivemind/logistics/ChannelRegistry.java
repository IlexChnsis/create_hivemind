package de.hivemind.create_hivemind.logistics;

import de.hivemind.create_hivemind.index.CHMDataComponents;
import de.theidler.create_mobile_packages.index.CMPDataComponents;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 便携式仓库管理器的「频道名单」读写。
 *
 * <p>这是多频道功能唯一的真相来源。上游只认 {@code CMP_FREQ}（当前活动网络），
 * 这里额外维护「有哪些网络可选」和「每个网络叫什么」。</p>
 *
 * <p>所有方法直接操作 {@link ItemStack} 的数据组件，不缓存任何东西，
 * 与 CMP 自己的读写方式一致，不会出现两份状态。</p>
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
     * 绑定一个网络。已在名单里则改为切到它，让玩家右键重复执行时有可见反馈。
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

        Map<UUID, String> names = new LinkedHashMap<>(names(stack));
        if (names.remove(network) != null) {
            stack.set(CHMDataComponents.NETWORK_NAMES, Map.copyOf(names));
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

    public static Map<UUID, String> names(ItemStack stack) {
        return new LinkedHashMap<>(stack.getOrDefault(CHMDataComponents.NETWORK_NAMES, Map.of()));
    }

    /**
     * 显示名：有自定义名就用，否则按「在名单中的位置」生成「物流网络N」。
     *
     * <p>N 取索引 + 1，所以删掉中间的网络后后面的编号会前移。这是有意为之：
     * 没命名过的网络玩家不会在意外观编号，命名过的则完全不受影响。</p>
     */
    public static String displayName(ItemStack stack, UUID network) {
        String custom = names(stack).get(network);
        if (custom != null && !custom.isBlank()) {
            return custom;
        }
        int index = bound(stack).indexOf(network);
        return index < 0 ? "?" : "物流网络" + (index + 1);
    }

    public static void setName(ItemStack stack, UUID network, @Nullable String name) {
        Map<UUID, String> current = names(stack);
        if (name == null || name.isBlank()) {
            current.remove(network);
        } else {
            current.put(network, name.trim());
        }
        if (current.isEmpty()) {
            stack.remove(CHMDataComponents.NETWORK_NAMES);
        } else {
            stack.set(CHMDataComponents.NETWORK_NAMES, Map.copyOf(current));
        }
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
