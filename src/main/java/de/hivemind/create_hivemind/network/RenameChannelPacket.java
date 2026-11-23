package de.hivemind.create_hivemind.network;

import de.hivemind.create_hivemind.logistics.ChannelRegistry;
import de.theidler.create_mobile_packages.items.portable_stock_ticker.PortableStockTicker;
import net.createmod.catnip.net.base.BasePacketPayload;
import net.createmod.catnip.net.base.ServerboundPacketPayload;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * 给某个频道设置<b>本地别名</b>。
 *
 * <p>与 CMP 的 {@code SetNetworkNamePackage} 的区别：那个改的是
 * {@code LogisticsNetwork.name}（全局、只有 owner 能改、所有人可见）；
 * 这个改的是管理器物品上的 {@code NETWORK_NAMES} 组件，只对这个管理器生效。</p>
 *
 * <p>因此<b>不需要 owner 权限</b>——每个玩家都可以给自己的管理器起别名。</p>
 *
 * <p>空字符串表示清除别名，回到序号默认名。</p>
 */
public class RenameChannelPacket implements ServerboundPacketPayload {

    /** 与服务端校验一致的长度上限。 */
    public static final int MAX_NAME_LENGTH = 20;

    public static final StreamCodec<RegistryFriendlyByteBuf, RenameChannelPacket> STREAM_CODEC =
            StreamCodec.composite(
                    UUIDUtil.STREAM_CODEC, packet -> packet.network,
                    ByteBufCodecs.STRING_UTF8, packet -> packet.name,
                    RenameChannelPacket::new
            );

    private final UUID network;
    private final String name;

    public RenameChannelPacket(UUID network, String name) {
        this.network = network;
        this.name = name == null ? "" : name;
    }

    @Override
    public void handle(ServerPlayer player) {
        if (player == null || player.isSpectator()) {
            return;
        }
        ItemStack stack = PortableStockTicker.find(player.getInventory());
        if (stack == null || !(stack.getItem() instanceof PortableStockTicker)) {
            return;
        }
        // 只能给已绑定的网络起别名
        if (!ChannelRegistry.isBound(stack, network)) {
            return;
        }
        String trimmed = name.trim();
        if (trimmed.length() > MAX_NAME_LENGTH) {
            trimmed = trimmed.substring(0, MAX_NAME_LENGTH);
        }
        ChannelRegistry.setAlias(stack, network, trimmed);
    }

    @Override
    public BasePacketPayload.PacketTypeProvider getTypeProvider() {
        return CHMPackets.RENAME_CHANNEL;
    }
}
