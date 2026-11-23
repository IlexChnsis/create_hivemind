package de.hivemind.create_hivemind.network;

import de.hivemind.create_hivemind.CreateHiveMind;
import net.createmod.catnip.net.base.BasePacketPayload;
import net.createmod.catnip.net.base.CatnipPacketRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.Locale;

/**
 * 本模组自己的网络包。
 *
 * <p>不复用 CMP 的 {@code CMPPackets} 枚举（enum 无法扩展），
 * 而是用同一套 Catnip 注册器另开一个通道。</p>
 */
public enum CHMPackets implements BasePacketPayload.PacketTypeProvider {

    // Client -> Server
    SELECT_CHANNEL(SelectChannelPacket.class, SelectChannelPacket.STREAM_CODEC),
    RENAME_CHANNEL(RenameChannelPacket.class, RenameChannelPacket.STREAM_CODEC);

    private final CatnipPacketRegistry.PacketType<?> type;

    <T extends BasePacketPayload> CHMPackets(Class<T> clazz,
                                             StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        String name = this.name().toLowerCase(Locale.ROOT);
        this.type = new CatnipPacketRegistry.PacketType<>(
                new CustomPacketPayload.Type<>(CreateHiveMind.asResource(name)),
                clazz, codec
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends CustomPacketPayload> CustomPacketPayload.Type<T> getType() {
        return (CustomPacketPayload.Type<T>) this.type.type();
    }

    public static void register() {
        CatnipPacketRegistry registry = new CatnipPacketRegistry(CreateHiveMind.MOD_ID, 1);
        for (CHMPackets packet : CHMPackets.values()) {
            registry.registerPacket(packet.type);
        }
        registry.registerAllPackets();
    }
}
