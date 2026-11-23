package de.hivemind.create_hivemind.mixin.client;

import de.hivemind.create_hivemind.logistics.ChannelRegistry;
import de.hivemind.create_hivemind.network.SelectChannelPacket;
import de.theidler.create_mobile_packages.items.portable_stock_ticker.ClientScreenStorage;
import de.theidler.create_mobile_packages.items.portable_stock_ticker.PortableStockTicker;
import de.theidler.create_mobile_packages.items.portable_stock_ticker.PortableStockTickerMenu;
import de.theidler.create_mobile_packages.items.portable_stock_ticker.PortableStockTickerScreen;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.UUID;

/**
 * 给便携式仓库管理器加「频道选择区」，位置在顶部标题行。
 *
 * <p>标题保持原样并追加网络名：便携式仓库管理器：网络名。</p>
 *
 * <p>为什么用覆盖式而不是插入新行：windowHeight 由下部纹理高度动态计算，
 * 插入一行会让 itemsY、orderY、滚动条全部偏移。这里改为在标题行叠加，
 * 下拉列表浮在库存区之上，不动任何既有坐标。</p>
 *
 * <p>命名入口不注册 Widget（addRenderableWidget 在 Screen 里是 protected），
 * 改为自绘铅笔标记 + 点击时打开命名界面，与 CMP 港口那个按钮打开的是同一个界面。</p>
 */
@Mixin(PortableStockTickerScreen.class)
public abstract class PortableStockTickerChannelScreenMixin {

    @Unique
    private static final String CHM_TITLE_PREFIX = "\u4fbf\u643a\u5f0f\u4ed3\u5e93\u7ba1\u7406\u5668\uff1a";

    /**
     * 网络名最大显示字符数（全角）。
     *
     * <p>测算：窗口宽 226，标题居中，右侧留 20px 给命名标记，可用约 198px。
     * 前缀 9 个全角字符乘 9px 等于 81px，剩余 117px 除以 9px 约 13 字，
     * 给省略号留位置后取 12。</p>
     */
    @Unique
    private static final int CHM_MAX_NAME_CHARS = 12;

    @Unique
    private static final int CHM_ROW_HEIGHT = 12;

    @Unique
    private static final int CHM_MAX_ROWS = 8;

    @Unique
    private boolean chm$dropdownOpen;

    @Unique
    private List<UUID> chm$bound = List.of();

    /** menu 在 AbstractContainerScreen 里是 protected，只能通过 Shadow 读取。 */
    @Shadow
    @Final
    protected PortableStockTickerMenu menu;

    @Unique
    private PortableStockTickerScreen chm$self() {
        return (PortableStockTickerScreen) (Object) this;
    }

    @Unique
    private ItemStack chm$stack() {
        if (this.menu == null || this.menu.playerInventory == null) {
            return ItemStack.EMPTY;
        }
        ItemStack found = PortableStockTicker.find(this.menu.playerInventory);
        return found == null ? ItemStack.EMPTY : found;
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

    @Unique
    private String chm$name(ItemStack stack, UUID network) {
        String raw = ChannelRegistry.displayName(stack, network);
        return raw.length() > CHM_MAX_NAME_CHARS ? raw.substring(0, CHM_MAX_NAME_CHARS) : raw;
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

        String label = active == null ? "" : chm$name(stack, active);
        String full = label.isEmpty()
                ? CHM_TITLE_PREFIX.substring(0, CHM_TITLE_PREFIX.length() - 1)
                : CHM_TITLE_PREFIX + label;
        int maxTitleWidth = windowWidth - 8 - 20;
        String shown = chm$truncate(full, maxTitleWidth);
        graphics.drawString(font, shown,
                x + windowWidth / 2 - font.width(shown) / 2, y + 4, 0x714A40, false);

        // 可切换时给个下拉箭头提示
        if (chm$bound.size() >= 2) {
            String arrow = chm$dropdownOpen ? "\u25B2" : "\u25BC";
            int arrowX = x + windowWidth / 2 + font.width(shown) / 2 + 3;
            if (font.width(shown) + 12 < maxTitleWidth) {
                graphics.drawString(font, arrow, arrowX, y + 5, 0x8C5D4B, false);
            }
        }

        // 命名入口：右上角自绘铅笔标记
        if (active != null) {
            int penX = x + windowWidth - 16;
            int penY = y + 3;
            boolean hovered = mouseX >= penX - 2 && mouseX <= penX + 10
                    && mouseY >= penY - 1 && mouseY <= penY + 11;
            if (hovered) {
                graphics.fill(penX - 2, penY - 1, penX + 10, penY + 11, 0x40C8A050);
            }
            graphics.drawString(font, "\u270E", penX, penY, 0x8C5D4B, false);
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

        // 命名标记
        if (active != null) {
            int penX = x + windowWidth - 16;
            int penY = y + 3;
            if (mouseX >= penX - 2 && mouseX <= penX + 10 && mouseY >= penY - 1 && mouseY <= penY + 11) {
                var mc = Minecraft.getInstance();
                mc.setScreen(NetworkSettingsScreenInvoker.chm$create(mc.screen, active));
                cir.setReturnValue(true);
                return;
            }
        }

        // 标题行：展开或收起
        if (mouseY >= y + 2 && mouseY <= y + 14 && mouseX >= x + 4 && mouseX <= x + windowWidth - 20) {
            if (chm$bound.size() >= 2) {
                chm$dropdownOpen = !chm$dropdownOpen;
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
                    // 切换后立即重取库存：stacks 是静态全局的，
                    // 不清就会先闪一下旧网络的物品（最多 100 tick）
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

    /** 关闭界面时收起下拉。 */
    @Inject(method = "removed", at = @At("HEAD"), require = 1, remap = false)
    private void chm$onRemoved(CallbackInfo ci) {
        chm$dropdownOpen = false;
    }
}
