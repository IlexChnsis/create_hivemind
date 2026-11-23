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
 * 客户端请求给某个频道改名。空字符串表示恢复默认名。
 */
public class RenameChannelPacket implements ServerboundPacketPayload {

    public static final int MAX_NAME_LENGTH = 32;

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
        if (!ChannelRegistry.isBound(stack, network)) {
            return;
        }
        String trimmed = name.trim();
        if (trimmed.length() > MAX_NAME_LENGTH) {
            trimmed = trimmed.substring(0, MAX_NAME_LENGTH);
        }
        ChannelRegistry.setName(stack, network, trimmed);
    }

    @Override
    public BasePacketPayload.PacketTypeProvider getTypeProvider() {
        return CHMPackets.RENAME_CHANNEL;
    }
}
