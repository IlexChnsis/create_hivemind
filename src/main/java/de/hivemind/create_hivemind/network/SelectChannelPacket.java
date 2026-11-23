package de.hivemind.create_hivemind.network;

import de.hivemind.create_hivemind.logistics.ChannelRegistry;
import de.hivemind.create_hivemind.network.CHMPackets;
import de.theidler.create_mobile_packages.items.portable_stock_ticker.PortableStockTicker;
import net.createmod.catnip.net.base.BasePacketPayload;
import net.createmod.catnip.net.base.ServerboundPacketPayload;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * 客户端请求切换当前活动网络。
 *
 * <p>必须走服务端：{@code CMP_FREQ} 是物品数据组件，客户端直接改不会同步回去，
 * 而且服务端在处理库存请求时是从<i>服务端那份</i>物品栈上读网络的。</p>
 */
public class SelectChannelPacket implements ServerboundPacketPayload {

    public static final StreamCodec<RegistryFriendlyByteBuf, SelectChannelPacket> STREAM_CODEC =
            StreamCodec.composite(
                    UUIDUtil.STREAM_CODEC, packet -> packet.network,
                    SelectChannelPacket::new
            );

    private final UUID network;

    public SelectChannelPacket(UUID network) {
        this.network = network;
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
        if (!ChannelRegistry.isBound(stack, network)) {
            return;
        }
        ChannelRegistry.setActive(stack, network);
    }

    @Override
    public BasePacketPayload.PacketTypeProvider getTypeProvider() {
        return CHMPackets.SELECT_CHANNEL;
    }
}
