package de.hivemind.create_hivemind;

import com.mojang.logging.LogUtils;
import de.hivemind.create_hivemind.index.CHMConfigs;
import de.hivemind.create_hivemind.index.CHMDataComponents;
import de.hivemind.create_hivemind.network.CHMPackets;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Create: Hive Mind —— Create: Mobile Packages 的附属模组。
 *
 * <p>里程碑：</p>
 * <ul>
 *   <li>M1 同维度远距离折跃（0.0.1–0.0.4）</li>
 *   <li>M2 便携式仓库管理器多频道（0.0.5）</li>
 * </ul>
 */
@Mod(CreateHiveMind.MOD_ID)
public final class CreateHiveMind {

    public static final String MOD_ID = "create_hivemind";

    public static final Logger LOGGER = LogUtils.getLogger();

    public CreateHiveMind(IEventBus modEventBus, ModContainer modContainer) {
        CHMConfigs.register(modContainer);
        CHMDataComponents.register(modEventBus);
        CHMPackets.register();
        LOGGER.info("[{}] loaded: fold + multi-channel", MOD_ID);
    }

    public static ResourceLocation asResource(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
