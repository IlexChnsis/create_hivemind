package de.hivemind.create_hivemind;

import com.mojang.logging.LogUtils;
import de.hivemind.create_hivemind.index.CHMConfigs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Create: Hive Mind —— Create: Mobile Packages 的附属模组。
 *
 * <p>当前里程碑 M1：同维度远距离折跃。
 * 只注册服务端配置，玩法实现全部通过 {@code RoboBeeBehaviorMixin} 注入，
 * 不新增方块、物品或实体，因此不会与上游 CMP 的注册表冲突。</p>
 */
@Mod(CreateHiveMind.MOD_ID)
public final class CreateHiveMind {

    public static final String MOD_ID = "create_hivemind";

    public static final Logger LOGGER = LogUtils.getLogger();

    public CreateHiveMind(IEventBus modEventBus, ModContainer modContainer) {
        CHMConfigs.register(modContainer);
        LOGGER.info("[{}] loaded: same-dimension long-range fold is wired", MOD_ID);
    }
}
