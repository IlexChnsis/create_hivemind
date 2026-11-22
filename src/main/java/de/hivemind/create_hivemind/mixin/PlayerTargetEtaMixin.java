package de.hivemind.create_hivemind.mixin;

import de.hivemind.create_hivemind.robo.eta.EtaModel;
import de.theidler.create_mobile_packages.robo.PlayerTarget;
import de.theidler.create_mobile_packages.robo.VirtualRobo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 玩家侧 Toast 的 ETA 修正。
 *
 * <p>{@code handleNavigateToTarget} 的顺序是：先 {@code setETA(calcETA(...))}，
 * 紧接着 {@code updateEtaToast(robo)} 读取 {@code getETA()} 拼出提示文本。
 * 也就是说走 {@code VirtualRobo.updateEta} 那条注入时，Toast 早就把旧值读走了。
 *
 * <p>因此在 Toast 方法开头把 target 的 ETA 换成预测值，保证玩家看到的是
 * 「实际还要飞多久」，而不是「剩余直线距离 / 速度」。</p>
 */
@Mixin(PlayerTarget.class)
public abstract class PlayerTargetEtaMixin {

    @Inject(method = "updateEtaToast", at = @At("HEAD"), require = 1, remap = false)
    private void hivemind$applyFoldAwareEtaBeforeToast(VirtualRobo robo, CallbackInfo ci) {
        EtaModel.applyToTarget(robo);
    }
}
