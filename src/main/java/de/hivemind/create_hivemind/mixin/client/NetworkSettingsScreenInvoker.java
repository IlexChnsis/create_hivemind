package de.hivemind.create_hivemind.mixin.client;

import de.theidler.create_mobile_packages.network_settings.NetworkSettingsScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.UUID;

/**
 * CMP 的网络设置界面把构造器声明成了 {@code protected}，外部无法直接 new。
 *
 * <p>这里用 {@code @Invoker("<init>")} 生成一个静态工厂，效果等同于「把构造器改成 public」
 * 但不修改 CMP 的源码。</p>
 *
 * <p>之所以非要打开这个界面：用户要求命名逻辑与「蛙港 / 运输蜂停泊港」一致，
 * 而那两个地方用的正是 {@code NetworkSettingsScreen}——复用它，一致性就是白送的。</p>
 */
@Mixin(NetworkSettingsScreen.class)
public interface NetworkSettingsScreenInvoker {

    @Invoker("<init>")
    static NetworkSettingsScreen chm$create(Screen parent, UUID networkId) {
        throw new AssertionError("mixin invoker not applied");
    }
}
