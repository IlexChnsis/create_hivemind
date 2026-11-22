package de.hivemind.create_hivemind;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Create: Hive Mind —— Create: Mobile Packages 的附属模组。
 *
 * <p>M0 阶段只保留一个可加载的空壳：不注册任何内容，不注入任何 mixin。
 * 玩法实现见 docs/ROADMAP.md 的 M1 / M2。</p>
 */
@Mod(CreateHiveMind.MOD_ID)
public final class CreateHiveMind {

    public static final String MOD_ID = "create_hivemind";

    public static final Logger LOGGER = LogUtils.getLogger();

    public CreateHiveMind(IEventBus modEventBus, ModContainer modContainer) {
        // M0：仅确认模组被加载。M1 起在此注册配置与 mixin 相关初始化。
        LOGGER.info("[{}] loaded (skeleton, no gameplay yet)", MOD_ID);
    }
}
