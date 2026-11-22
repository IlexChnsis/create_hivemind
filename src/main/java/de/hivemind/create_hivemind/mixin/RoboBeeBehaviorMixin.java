package de.hivemind.create_hivemind.mixin;

import de.hivemind.create_hivemind.robo.fold.FoldController;
import de.theidler.create_mobile_packages.entities.robo_entity.RoboBeeBehaviorController;
import de.theidler.create_mobile_packages.robo.VirtualRobo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在「巡航」这一步结束时尝试折跃。
 *
 * <p>选这个注入点而不是 {@code VirtualRobo#tick}：
 * <ul>
 *   <li>能确认运输蜂此刻确实处于 {@code NAVIGATE_TO_TARGET} 状态，而不是起飞/降落/交付；</li>
 *   <li>方法签名稳定（{@code private void handleNavigateToTarget(VirtualRobo)}），
 *       不像 {@code tick} 那样可能被上游重构；</li>
 *   <li>TAIL 时本 tick 的位移已经写入 {@code currentPos}，折跃落点不会被再次推进。</li>
 * </ul>
 *
 * <p>注意：{@code handleNavigateToTarget} 在目标为空时会提前 return，
 * 那种情况下 TAIL 不触发，但也正因没有目标而不该折跃，语义一致。</p>
 */
@Mixin(RoboBeeBehaviorController.class)
public abstract class RoboBeeBehaviorMixin {

    // remap = false：NeoForge 运行时使用 Mojang 官方名（mojmap），
    // Create: Mobile Packages 也按官方名发布，因此这里不需要 refmap 重映射。
    @Inject(
            method = "handleNavigateToTarget(Lde/theidler/create_mobile_packages/robo/VirtualRobo;)V",
            at = @At("TAIL"),
            require = 1,
            remap = false
    )
    private void hivemind$tryFoldAfterNavigation(VirtualRobo robo, CallbackInfo ci) {
        FoldController.tryFold(robo);
    }
}
