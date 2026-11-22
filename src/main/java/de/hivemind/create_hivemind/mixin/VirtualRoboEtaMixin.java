package de.hivemind.create_hivemind.mixin;

import de.hivemind.create_hivemind.robo.eta.EtaModel;
import de.theidler.create_mobile_packages.robo.VirtualRobo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 把 {@code VirtualRobo.updateEta()} 算出来的 ETA 换成折跃感知的预测值。
 *
 * <p>{@code updateEta()} 在 {@code tick()} 的最后一步执行，所以这里覆盖的是一 tick 的最终值，
 * 对任何之后读取 request / target ETA 的地方（例如旅行显示源）都生效。</p>
 *
 * <p>注意：玩家 Toast 不在这个注入点后面取值——它在 {@code handleNavigateToTarget} 里
 * 先 setETA 再立刻读。那条路径由 {@link PlayerTargetEtaMixin} 处理。</p>
 */
@Mixin(VirtualRobo.class)
public abstract class VirtualRoboEtaMixin {

    @Inject(method = "updateEta()V", at = @At("TAIL"), require = 1, remap = false)
    private void hivemind$applyFoldAwareEta(CallbackInfo ci) {
        EtaModel.apply((VirtualRobo) (Object) this);
    }
}
