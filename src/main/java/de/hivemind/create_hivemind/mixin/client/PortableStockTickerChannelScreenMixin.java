package de.hivemind.create_hivemind.mixin.client;

import de.hivemind.create_hivemind.logistics.ChannelRegistry;
import de.hivemind.create_hivemind.network.SelectChannelPacket;
import de.theidler.create_mobile_packages.items.portable_stock_ticker.ClientScreenStorage;
import de.theidler.create_mobile_packages.items.portable_stock_ticker.PortableStockTicker;
import de.theidler.create_mobile_packages.items.portable_stock_ticker.PortableStockTickerScreen;
import de.theidler.create_mobile_packages.network_settings.ClientNetworkDataStorage;
import de.theidler.create_mobile_packages.network_settings.RequestPlayerNetworksPacket;
import de.theidler.create_mobile_packages.network_settings.SetNetworkNamePackage;
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
 *   <li>点<b>标题文字</b> → 就地改名（需网络 owner；与港口命名是同一个字段）</li>
 *   <li>点<b>最右侧箭头</b> → 展开/收起频道下拉</li>
 *   <li>点<b>下拉项</b> → 切换到该网络</li>
 * </ul>
 *
 * <p>改名为什么自己实现输入而不是打开 NetworkSettingsScreen：用户要求「直接点击标题可以改名」。
 * 这里不注册 EditBox（{@code addRenderableWidget} 是 protected，mixin 调不到），
 * 改为自己维护编辑缓冲，在 {@code charTyped} / {@code keyPressed} 里处理输入。</p>
 *
 * <p>权限：{@code SetNetworkNamePackage} 在服务端只接受 network owner，
 * 非 owner 的请求会被静默丢弃。所以非 owner 点击标题直接忽略，避免「以为改了其实没改」。</p>
 */
@Mixin(PortableStockTickerScreen.class)
public abstract class PortableStockTickerChannelScreenMixin {

    @Unique
    private static final String CHM_TITLE_PREFIX = "\u4fbf\u643a\u5f0f\u4ed3\u5e93\u7ba1\u7406\u5668\uff1a";

    /** 网络名最大字符数，与服务端 SetNetworkNamePackage 的 maxLength 25 保持余量。 */
    @Unique
    private static final int CHM_MAX_NAME_CHARS = 20;

    @Unique
    private static final int CHM_ROW_HEIGHT = 12;

    @Unique
    private static final int CHM_MAX_ROWS = 8;

    /** 右侧箭头占位宽度。 */
    @Unique
    private static final int CHM_ARROW_WIDTH = 14;

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

    /** 当前玩家是否是这个网络的 owner。只有 owner 能改名。 */
    @Unique
    private boolean chm$canRename(UUID network) {
        var player = Minecraft.getInstance().player;
        if (player == null || network == null) {
            return false;
        }
        var data = ClientNetworkDataStorage.getNetworkData(network);
        return data != null && data.owner != null && data.owner.equals(player.getUUID());
    }

    /** 网络显示名：优先用 CMP 的网络名，没有则按固定序号回退「物流网络N」。 */
    @Unique
    private String chm$rawName(ItemStack stack, UUID network) {
        var data = ClientNetworkDataStorage.getNetworkData(network);
        if (data != null && data.name != null && !data.name.isBlank()) {
            return data.name;
        }
        // 与服务端 ChannelRegistry.fallbackName 用同一套序号，
        // 不能用列表索引：已命名的网络也占着序号（绑定 1、2 → 命名 2 → 新增得 3）
        return ChannelRegistry.fallbackName(stack, network);
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

    /** 打开界面时拉一次网络名数据，否则标题只能显示「物流网络N」。 */
    @Inject(method = "init", at = @At("TAIL"), require = 1, remap = false)
    private void chm$requestNetworkNames(CallbackInfo ci) {
        CatnipServices.NETWORK.sendToServer(RequestPlayerNetworksPacket.INSTANCE);
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

        // 下拉箭头：贴最右侧
        if (chm$bound.size() >= 2) {
            String arrow = chm$dropdownOpen ? "\u25B2" : "\u25BC";
            int arrowX = x + windowWidth - CHM_ARROW_WIDTH + 2;
            boolean hovered = mouseX >= x + windowWidth - CHM_ARROW_WIDTH && mouseX <= x + windowWidth
                    && mouseY >= y + 2 && mouseY <= y + 14;
            if (hovered) {
                graphics.fill(x + windowWidth - CHM_ARROW_WIDTH, y + 2,
                        x + windowWidth - 1, y + 14, 0x40C8A050);
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

        // 最右侧箭头：展开/收起下拉
        if (inTitleRow && chm$bound.size() >= 2
                && mouseX >= x + windowWidth - CHM_ARROW_WIDTH && mouseX <= x + windowWidth) {
            chm$dropdownOpen = !chm$dropdownOpen;
            cir.setReturnValue(true);
            return;
        }

        // 标题文字：进入改名（仅 owner）
        if (inTitleRow && active != null
                && mouseX >= x + 4 && mouseX < x + windowWidth - CHM_ARROW_WIDTH) {
            if (chm$canRename(active)) {
                chm$dropdownOpen = false;
                chm$editing = true;
                // 初始值：已有自定义名则带出来，默认名则留空方便直接输入
                var data = ClientNetworkDataStorage.getNetworkData(active);
                chm$editBuffer = data != null && data.name != null ? data.name : "";
                cir.setReturnValue(true);
            }
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
        CatnipServices.NETWORK.sendToServer(new SetNetworkNamePackage(name, active));
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
