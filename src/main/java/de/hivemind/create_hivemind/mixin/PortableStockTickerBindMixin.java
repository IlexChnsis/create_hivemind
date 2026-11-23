package de.hivemind.create_hivemind.mixin;

import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;
import de.hivemind.create_hivemind.logistics.ChannelRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 把便携式仓库管理器的「对着物流连接点右键」从<b>覆盖绑定</b>改成<b>按网络开关</b>。
 *
 * <p>CMP 原版行为：潜行右键连接点 → 把物品的 {@code CMP_FREQ} 覆盖成该网络。
 * 结果是一个终端永远只能挂一个网络，无法积累。</p>
 *
 * <p>本 mixin 改为：</p>
 * <ul>
 *   <li>该网络<b>不在</b>名单 → 加入名单并设为当前（提示「已绑定」）</li>
 *   <li>该网络<b>已在</b>名单 → 移出名单（提示「已解绑」）</li>
 * </ul>
 *
 * <p>仅在潜行时接管，非潜行仍然是打开界面（上游 {@code use()} 的逻辑），
 * 因此不会和「右键打开 GUI」冲突。</p>
 */
@Mixin(targets = "de.theidler.create_mobile_packages.items.portable_stock_ticker.PortableStockTicker")
public abstract class PortableStockTickerBindMixin {

    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private void hivemind$toggleNetworkBinding(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        Player player = context.getPlayer();
        if (player == null) {
            return;
        }
        // 只在潜行时接管，非潜行交还给上游
        if (!player.isShiftKeyDown()) {
            return;
        }

        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        LogisticallyLinkedBehaviour link = BlockEntityBehaviour.get(level, pos, LogisticallyLinkedBehaviour.TYPE);
        if (link == null) {
            return;
        }

        ItemStack stack = context.getItemInHand();
        if (!(stack.getItem() instanceof de.theidler.create_mobile_packages.items.portable_stock_ticker.PortableStockTicker)) {
            return;
        }

        // 客户端只负责播放动的反馈，真正写入在服务端
        if (level.isClientSide) {
            cir.setReturnValue(InteractionResult.SUCCESS);
            return;
        }

        if (!link.mayInteractMessage(player)) {
            cir.setReturnValue(InteractionResult.SUCCESS);
            return;
        }

        ChannelRegistry.BindResult result =
                ChannelRegistry.isBound(stack, link.freqId)
                        ? ChannelRegistry.unbind(stack, link.freqId)
                        : ChannelRegistry.bind(stack, link.freqId);

        switch (result) {
            case BOUND, ALREADY_BOUND -> {
                // 复用 Create 自己的绑定提示，与原版 stock_link 完全一致
                player.displayClientMessage(
                        CreateLang.translateDirect("logistically_linked.tuned"), true);
            }
            case UNBOUND -> player.displayClientMessage(
                    Component.translatable("create_hivemind.logistically_linked.untuned")
                            .withStyle(ChatFormatting.GRAY), true);
            case FULL -> player.displayClientMessage(
                    Component.translatable("create_hivemind.portable_stock_ticker.channels_full")
                            .withStyle(ChatFormatting.RED), true);
            case NOT_BOUND -> {
                // 理论上不会走到这里（分支已判定），保险起见不反馈
            }
        }

        cir.setReturnValue(InteractionResult.SUCCESS);
    }
}
