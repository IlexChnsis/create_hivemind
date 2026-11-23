package de.hivemind.create_hivemind.mixin.client;

import de.hivemind.create_hivemind.logistics.ChannelRegistry;
import de.hivemind.create_hivemind.network.RenameChannelPacket;
import de.hivemind.create_hivemind.network.SelectChannelPacket;
import de.theidler.create_mobile_packages.items.portable_stock_ticker.ClientScreenStorage;
import de.theidler.create_mobile_packages.items.portable_stock_ticker.PortableStockTicker;
import de.theidler.create_mobile_packages.items.portable_stock_ticker.PortableStockTickerScreen;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.UUID;

/**
 * 便携式仓库管理器的频道选择区，位置在顶部标题行。
 *
 * <p>标题格式：便携式仓库管理器：网络名。原版那行标题会被
 * {@link #chm$hideVanillaTitle} 拦截掉，否则会与新的重叠。</p>
 *
 * <p>交互分工：</p>
 * <ul>
 *   <li>点<b>标题文字</b> → 就地给当前频道起别名（预填当前显示名）</li>
 *   <li>点<b>右侧箭头</b> → 展开/收起频道下拉</li>
 *   <li>点<b>下拉项</b> → 切换到该网络</li>
 * </ul>
 *
 * <p>改名为什么自己实现输入而不是打开 NetworkSettingsScreen：用户要求「直接点击标题可以改名」。
 * 这里不注册 EditBox（{@code addRenderableWidget} 是 protected，mixin 调不到），
 * 改为自己维护编辑缓冲，在 {@code charTyped} / {@code keyPressed} 里处理输入。</p>
 *
 * <p><b>别名是管理器 specific 的</b>：写在物品的 {@code NETWORK_NAMES} 组件上，
 * 不是 CMP 的 {@code LogisticsNetwork.name}（那个是全局名，只有 owner 能改）。
 * 因此这里无需任何权限校验，每个玩家都能给同一个网络起自己的叫法。</p>
 */
@Mixin(PortableStockTickerScreen.class)
public abstract class PortableStockTickerChannelScreenMixin {

    @Unique
    private static final String CHM_TITLE_PREFIX = "\u4fbf\u643a\u5f0f\u4ed3\u5e93\u7ba1\u7406\u5668\uff1a";

    /** 别名最大字符数，与 RenameChannelPacket.MAX_NAME_LENGTH 一致。 */
    @Unique
    private static final int CHM_MAX_NAME_CHARS = RenameChannelPacket.MAX_NAME_LENGTH;

    @Unique
    private static final int CHM_ROW_HEIGHT = 12;

    @Unique
    private static final int CHM_MAX_ROWS = 8;

    /** 右侧箭头占位宽度。 */
    @Unique
    private static final int CHM_ARROW_WIDTH = 14;

    /**
     * 右侧区域的右边界：内缩 4px。
     *
     * <p>{@code getXSize()} 是逻辑宽度 226，而背景纹理实际约 220 宽，
     * 贴着逻辑右边界绘制会探出纹理 1–2px。这里统一内缩，绘制与命中判定共用同一计算。</p>
     */
    @Unique
    private int chm$arrowRight(int guiLeft, int windowWidth) {
        return guiLeft + windowWidth - 4;
    }

    @Unique
    private boolean chm$dropdownOpen;

    @Unique
    private boolean chm$editing;

    @Unique
    private String chm$editBuffer = "";

    @Unique
    private List<UUID> chm$bound = List.of();

    @Unique
    private PortableStockTickerScreen chm$self() {
        return (PortableStockTickerScreen) (Object) this;
    }

    @Unique
    private ItemStack chm$stack() {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return ItemStack.EMPTY;
        }
        ItemStack found = PortableStockTicker.find(player.getInventory());
        return found == null ? ItemStack.EMPTY : found;
    }

    /**
     * 网络显示名：别名优先，其次序号默认名「物流网络N」。
     *
     * <p>别名存在<b>物品</b>上（{@code NETWORK_NAMES}），因此每个管理器可以有不同的叫法。
     * 这与 CMP 的 {@code LogisticsNetwork.name}（全局、owner 才能改）是两件事。</p>
     */
    @Unique
    private String chm$rawName(ItemStack stack, UUID network) {
        return ChannelRegistry.displayName(stack, network);
    }

    @Unique
    private String chm$name(ItemStack stack, UUID network) {
        String raw = chm$rawName(stack, network);
        return raw.length() > CHM_MAX_NAME_CHARS ? raw.substring(0, CHM_MAX_NAME_CHARS) : raw;
    }

    /** 按像素宽度截断，超长补省略号。 */
    @Unique
    private String chm$truncate(String text, int maxWidth) {
        var font = Minecraft.getInstance().font;
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int limit = Math.max(0, maxWidth - font.width(ellipsis));
        StringBuilder sb = new StringBuilder();
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int w = font.width(String.valueOf(c));
            if (width + w > limit) {
                break;
            }
            sb.append(c);
            width += w;
        }
        return sb + ellipsis;
    }

    /**
     * 干掉原版标题，避免与新标题重叠。
     *
     * <p>原版在 renderBg 里是 {@code Component.translatable("...screen_title")}，<b>单个 String 参数</b>，
     * 所以字节码里调的是 {@code translatable(String)} 这个重载，
     * 而不是 {@code translatable(String, Object[])}。target 必须写成前者，否则匹配不到。</p>
     */
    @Redirect(
            method = "renderBg",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/network/chat/Component;translatable(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;"),
            require = 1,
            remap = false
    )
    private MutableComponent chm$hideVanillaTitle(String key) {
        return Component.empty();
    }

    @Inject(method = "renderBg", at = @At("TAIL"), require = 1, remap = false)
    private void chm$renderChannelBar(GuiGraphics graphics, float partialTick, int mouseX, int mouseY,
                                      CallbackInfo ci) {
        PortableStockTickerScreen self = chm$self();
        ItemStack stack = chm$stack();
        if (stack.isEmpty()) {
            return;
        }
        chm$bound = ChannelRegistry.bound(stack);
        UUID active = ChannelRegistry.active(stack);
        var font = Minecraft.getInstance().font;

        int x = self.getGuiLeft();
        int y = self.getGuiTop();
        int windowWidth = self.getXSize();

        // 标题可用宽度：整行减去右侧箭头占位
        int maxTitleWidth = windowWidth - CHM_ARROW_WIDTH - 6;
        String label = active == null ? "" : chm$name(stack, active);

        if (chm$editing) {
            // 编辑态：画缓冲内容 + 光标
            String shown = chm$truncate(chm$editBuffer, maxTitleWidth);
            int textX = x + windowWidth / 2 - font.width(shown) / 2;
            graphics.fill(x + windowWidth / 2 - maxTitleWidth / 2 - 2, y + 2,
                    x + windowWidth / 2 + maxTitleWidth / 2 + 2, y + 14, 0x30C8A050);
            graphics.drawString(font, shown, textX, y + 4, 0x2F6B2F, false);
            int caretX = textX + font.width(shown);
            graphics.fill(caretX, y + 3, caretX + 1, y + 13, 0xFF2F6B2F);
        } else {
            String full = label.isEmpty()
                    ? CHM_TITLE_PREFIX.substring(0, CHM_TITLE_PREFIX.length() - 1)
                    : CHM_TITLE_PREFIX + label;
            String shown = chm$truncate(full, maxTitleWidth);
            graphics.drawString(font, shown,
                    x + windowWidth / 2 - font.width(shown) / 2, y + 4, 0x714A40, false);
        }

        // 下拉箭头：贴右侧，但留余量
        // getXSize() 返回逻辑宽度 226，背景纹理实际只有约 220 宽，
        // 贴着 226 画会探出纹理边缘 1–2px，所以右边界内缩。
        if (chm$bound.size() >= 2) {
            String arrow = chm$dropdownOpen ? "\u25B2" : "\u25BC";
            int arrowRight = chm$arrowRight(x, windowWidth);
            int arrowLeft = arrowRight - CHM_ARROW_WIDTH;
            int arrowX = arrowLeft + 3;
            boolean hovered = mouseX >= arrowLeft && mouseX <= arrowRight
                    && mouseY >= y + 2 && mouseY <= y + 14;
            if (hovered) {
                graphics.fill(arrowLeft, y + 2, arrowRight, y + 14, 0x40C8A050);
            }
            graphics.drawString(font, arrow, arrowX, y + 5, 0x8C5D4B, false);
        }

        if (!chm$dropdownOpen || chm$bound.isEmpty()) {
            return;
        }

        // 下拉列表浮在库存区之上
        int listTop = y + 15;
        int listWidth = 118;
        int listLeft = x + windowWidth / 2 - listWidth / 2;
        int rows = Math.min(chm$bound.size(), CHM_MAX_ROWS);
        int listHeight = rows * CHM_ROW_HEIGHT + 2;

        graphics.fill(listLeft - 1, listTop - 1, listLeft + listWidth + 1, listTop + listHeight + 1, 0xFF140A0A);
        graphics.fill(listLeft, listTop, listLeft + listWidth, listTop + listHeight, 0xFFF8F8EC);

        for (int i = 0; i < rows; i++) {
            UUID network = chm$bound.get(i);
            int rowY = listTop + 1 + i * CHM_ROW_HEIGHT;
            boolean hovered = mouseX >= listLeft && mouseX < listLeft + listWidth
                    && mouseY >= rowY && mouseY < rowY + CHM_ROW_HEIGHT;
            if (hovered) {
                graphics.fill(listLeft, rowY, listLeft + listWidth, rowY + CHM_ROW_HEIGHT, 0x60C8A050);
            }
            boolean isActive = network.equals(active);
            graphics.drawString(font, chm$name(stack, network), listLeft + 4, rowY + 2,
                    isActive ? 0x2F6B2F : 0x4A2D31, false);
            if (isActive) {
                graphics.drawString(font, "\u2714", listLeft + listWidth - 11, rowY + 2, 0x2F6B2F, false);
            }
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private void chm$onMouseClicked(double mouseX, double mouseY, int button,
                                    CallbackInfoReturnable<Boolean> cir) {
        if (button != 0) {
            return;
        }
        PortableStockTickerScreen self = chm$self();
        ItemStack stack = chm$stack();
        if (stack.isEmpty()) {
            return;
        }
        chm$bound = ChannelRegistry.bound(stack);
        UUID active = ChannelRegistry.active(stack);

        int x = self.getGuiLeft();
        int y = self.getGuiTop();
        int windowWidth = self.getXSize();

        boolean inTitleRow = mouseY >= y + 2 && mouseY <= y + 14;

        // 编辑态：点别处即提交
        if (chm$editing) {
            if (!inTitleRow) {
                chm$commitRename(stack, active);
            }
            cir.setReturnValue(true);
            return;
        }

        // 右侧箭头：展开/收起下拉（与绘制共用 chm$arrowRight，保证两者位置一致）
        int arrowRight = chm$arrowRight(x, windowWidth);
        int arrowLeft = arrowRight - CHM_ARROW_WIDTH;
        if (inTitleRow && chm$bound.size() >= 2
                && mouseX >= arrowLeft && mouseX <= arrowRight) {
            chm$dropdownOpen = !chm$dropdownOpen;
            cir.setReturnValue(true);
            return;
        }

        // 标题文字：进入改名（别名是本地数据，任何玩家都能改自己的）
        if (inTitleRow && active != null
                && mouseX >= x + 4 && mouseX < arrowLeft) {
            chm$dropdownOpen = false;
            chm$editing = true;
            // 编辑框预填「当前看到的那个名字」：有别名就是别名，否则是序号默认名。
            // 用户要求重命名时显示原本的名字。
            chm$editBuffer = chm$rawName(stack, active);
            cir.setReturnValue(true);
            return;
        }

        if (!chm$dropdownOpen) {
            return;
        }

        int listTop = y + 15;
        int listWidth = 118;
        int listLeft = x + windowWidth / 2 - listWidth / 2;
        int rows = Math.min(chm$bound.size(), CHM_MAX_ROWS);

        for (int i = 0; i < rows; i++) {
            int rowY = listTop + 1 + i * CHM_ROW_HEIGHT;
            if (mouseX >= listLeft && mouseX < listLeft + listWidth
                    && mouseY >= rowY && mouseY < rowY + CHM_ROW_HEIGHT) {
                UUID target = chm$bound.get(i);
                if (!target.equals(active)) {
                    CatnipServices.NETWORK.sendToServer(new SelectChannelPacket(target));
                    ClientScreenStorage.stacks.clear();
                    ClientScreenStorage.manualUpdate();
                }
                chm$dropdownOpen = false;
                cir.setReturnValue(true);
                return;
            }
        }

        chm$dropdownOpen = false;
        cir.setReturnValue(true);
    }

    @Unique
    private void chm$commitRename(ItemStack stack, UUID active) {
        chm$editing = false;
        if (active == null) {
            return;
        }
        String name = chm$editBuffer.trim();
        // 别名只写本管理器物品，不走 CMP 的全局网络改名（那个需要 owner 权限）
        CatnipServices.NETWORK.sendToServer(
                new de.hivemind.create_hivemind.network.RenameChannelPacket(active, name));
        chm$editBuffer = "";
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private void chm$onCharTyped(char codePoint, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (!chm$editing) {
            return;
        }
        if (codePoint >= 32 && codePoint != 127 && chm$editBuffer.length() < CHM_MAX_NAME_CHARS) {
            chm$editBuffer += codePoint;
        }
        cir.setReturnValue(true);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private void chm$onKeyPressed(int keyCode, int scanCode, int modifiers,
                                  CallbackInfoReturnable<Boolean> cir) {
        if (!chm$editing) {
            return;
        }
        ItemStack stack = chm$stack();
        UUID active = ChannelRegistry.active(stack);

        switch (keyCode) {
            case GLFW.GLFW_KEY_ESCAPE -> {
                chm$editing = false;
                chm$editBuffer = "";
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> chm$commitRename(stack, active);
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (!chm$editBuffer.isEmpty()) {
                    chm$editBuffer = chm$editBuffer.substring(0, chm$editBuffer.length() - 1);
                }
            }
            default -> {
                // 编辑期间吞掉其它按键，避免同时触发搜索框
            }
        }
        cir.setReturnValue(true);
    }

    @Inject(method = "removed", at = @At("HEAD"), require = 1, remap = false)
    private void chm$onRemoved(CallbackInfo ci) {
        chm$dropdownOpen = false;
        chm$editing = false;
        chm$editBuffer = "";
    }
}
