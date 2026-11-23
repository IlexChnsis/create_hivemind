package de.hivemind.create_hivemind.mixin;

import de.hivemind.create_hivemind.logistics.ChannelRegistry;
import de.theidler.create_mobile_packages.items.portable_stock_ticker.PortableStockTicker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 潜行右键空气 / 任意空处时，在已绑定的频道之间循环切换。
 *
 * <p>CMP 原版在潜行使用时不产生任何副作用（只是读了几个字段就返回），
 * 这块交互是空白的，用来做「快速切频道」不会抢占任何现有功能。</p>
 *
 * <p>只有绑定数量 ≥ 2 时才生效，否则与上游完全一致。</p>
 */
@Mixin(targets = "de.theidler.create_mobile_packages.items.portable_stock_ticker.PortableStockTicker")
public abstract class PortableStockTickerCycleMixin {

    @Inject(method = "use", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private void hivemind$cycleChannel(Level level, Player player,
                                       net.minecraft.world.InteractionHand hand,
                                       CallbackInfoReturnable<net.minecraft.world.InteractionResultHolder<ItemStack>> cir) {
        if (player == null || !player.isShiftKeyDown()) {
            return;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof PortableStockTicker)) {
            return;
        }
        if (ChannelRegistry.bound(stack).size() < 2) {
            return;
        }
        if (level.isClientSide) {
            // 客户端直接返回成功即可，服务端会回传新状态
            return;
        }
        var next = ChannelRegistry.cycle(stack, true);
        if (next != null) {
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable(
                            "create_hivemind.portable_stock_ticker.channel_switched",
                            ChannelRegistry.displayName(stack, next))
                            .withStyle(net.minecraft.ChatFormatting.GOLD), true);
        }
        cir.setReturnValue(net.minecraft.world.InteractionResultHolder.success(stack));
    }
}
