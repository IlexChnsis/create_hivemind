package de.hivemind.create_hivemind.index;

import de.hivemind.create_hivemind.config.CHMServer;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;

/**
 * 配置文件注册入口。SERVER 类型 → <code>config/create_hivemind-server.toml</code>。
 */
public final class CHMConfigs {

    public static void register(ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.SERVER, CHMServer.SPEC);
    }

    private CHMConfigs() {
    }
}
