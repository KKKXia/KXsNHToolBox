package com.KKKXia.NHToolbox.inventory;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import org.lwjgl.opengl.GL11;

import com.KKKXia.NHToolbox.NHToolbox;
import com.KKKXia.NHToolbox.handler.KeyBindings;

/**
 * 背包栏位锁定的界面层支持工具。
 *
 * <p>
 * 本类只提供纯静态判定/渲染逻辑；实际的事件覆写位于
 * {@link SlotLockGuiInventory} / {@link SlotLockGuiCreative}。
 * 所有方法都接收调用方（子类）解析好的鼠标坐标与 guiLeft/guiTop，
 * 以便子类直接访问 GuiContainer 的 protected 字段（无需反射）。
 */
public final class SlotLockGuiSupport {

    private static final Minecraft MC = Minecraft.getMinecraft();
    private static final RenderItem RENDER_ITEM = new RenderItem();

    /** 边框颜色：空锁定 = 蓝色，类型锁定 = 绿色。 */
    private static final int COLOR_EMPTY_LOCK = 0xFF40B0FF;
    private static final int COLOR_TYPE_LOCK = 0xFF40FF80;
    /** 取空后残留图标的透明度（越小越淡；0.4 与真实物品区分明显，可按口味调整）。 */
    private static final float GHOST_ALPHA = 0.4F;

    private SlotLockGuiSupport() {}

    // =====================================================================
    // 键位判定（沿用"直接比对键码"策略，不依赖 KeyBinding 内部哈希表）
    // =====================================================================
    public static boolean isLockMouseButton(int button) {
        int keyCode = KeyBindings.LOCK_SLOT.getKeyCode();
        return keyCode < 0 && button == keyCode + 100;
    }

    public static boolean isLockKeyCode(int keyCode) {
        int lockKey = KeyBindings.LOCK_SLOT.getKeyCode();
        return lockKey >= 0 && keyCode == lockKey;
    }

    // =====================================================================
    // 鼠标按下判定（子类 mouseClicked 在调用 super 之前使用）
    // =====================================================================
    public static boolean handleMouseClick(GuiContainer gui, int mouseX, int mouseY, int mouseButton, int guiLeft,
        int guiTop) {
        if (isLockMouseButton(mouseButton)) {
            Slot slot = slotAt(gui, mouseX, mouseY, guiLeft, guiTop);
            if (slot != null) {
                toggleAt(slot);
                return true; // 吞掉锁定键，阻止原版的中键行为（clickType 3 / 拖拽）
            }
            return false;
        }
        // 放置类：手中有物品且目标锁定栏位不兼容时吞掉（阻止直接放入/开始拖拽）
        if (mouseButton == 0 || mouseButton == 1) {
            ItemStack held = MC.thePlayer == null ? null : MC.thePlayer.inventory.getItemStack();
            if (held != null) {
                Slot slot = slotAt(gui, mouseX, mouseY, guiLeft, guiTop);
                if (slot != null && !SlotLockManager.getInstance()
                    .canAccept(slot.getSlotIndex(), held)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 拖拽移动中（子类 mouseClickMove）：不兼容的锁定槽不应加入原版拖拽目标集合。 */
    public static boolean shouldBlockDragMove(GuiContainer gui, int mouseX, int mouseY, int guiLeft, int guiTop) {
        ItemStack held = MC.thePlayer == null ? null : MC.thePlayer.inventory.getItemStack();
        if (held == null) {
            return false;
        }
        Slot slot = slotAt(gui, mouseX, mouseY, guiLeft, guiTop);
        return slot != null && !SlotLockManager.getInstance()
            .canAccept(slot.getSlotIndex(), held);
    }

    /** 松键（子类 mouseMovedOrUp）：松开在锁定槽上且不兼容时吞掉（held 留在手上）。 */
    public static boolean shouldBlockRelease(GuiContainer gui, int mouseX, int mouseY, int guiLeft, int guiTop) {
        ItemStack held = MC.thePlayer == null ? null : MC.thePlayer.inventory.getItemStack();
        if (held == null) {
            return false;
        }
        Slot slot = slotAt(gui, mouseX, mouseY, guiLeft, guiTop);
        return slot != null && !SlotLockManager.getInstance()
            .canAccept(slot.getSlotIndex(), held);
    }

    // =====================================================================
    // 命中判定：GUI 缩放坐标（与原版 mouseClicked 同一坐标系）
    // =====================================================================
    @SuppressWarnings("unchecked")
    public static Slot slotAt(GuiContainer gui, int mouseX, int mouseY, int guiLeft, int guiTop) {
        for (Slot slot : (List<Slot>) gui.inventorySlots.inventorySlots) {
            if (slot.inventory != MC.thePlayer.inventory) {
                continue;
            }
            if (slot.getSlotIndex() < 0 || slot.getSlotIndex() >= SlotLockManager.SLOT_COUNT) {
                continue; // 排除护甲(36-39)与其他索引
            }
            int x = guiLeft + slot.xDisplayPosition;
            int y = guiTop + slot.yDisplayPosition;
            if (mouseX >= x - 1 && mouseX < x + 17 && mouseY >= y - 1 && mouseY < y + 17) {
                return slot;
            }
        }
        return null;
    }

    // =====================================================================
    // 切换 + 渲染
    // =====================================================================
    /** 键盘键路径（子类 keyTyped）：悬浮在玩家槽上时切换锁定。 */
    public static void handleKeyboardToggle(GuiContainer gui, int mouseX, int mouseY, int guiLeft, int guiTop) {
        Slot slot = slotAt(gui, mouseX, mouseY, guiLeft, guiTop);
        if (slot != null) {
            toggleAt(slot);
        }
    }

    public static void toggleAt(Slot slot) {
        SlotLockManager.getInstance()
            .toggle(slot.getSlotIndex(), slot.getStack());
        NHToolbox.LOG.info(
            "[SlotLock] 切换栏位槽 {} -> 锁定状态 {}",
            slot.getSlotIndex(),
            SlotLockManager.getInstance()
                .getState(slot.getSlotIndex())
                .getType());
    }

    /**
     * 绘制锁定槽边框与淡化图标。
     *
     * <p>
     * 必须在 {@code drawGuiContainerForegroundLayer} 内调用：原版绘制顺序为
     * 槽位物品(GuiContainer.drawScreen:114) -> 前景层(134) -> 手持/拖拽物品 -> tooltip(186)，
     * 画在前景层才能既盖住物品又不遮挡 tooltip。
     *
     * <p>
     * 该层的坐标系已由 GuiContainer.drawScreen 平移过 (guiLeft, guiTop)，
     * 因此这里直接使用 slot.xDisplayPosition / yDisplayPosition；
     * 且该层内光照与深度测试均为禁用状态，绘制时不要改变这两个状态。
     */
    public static void renderLocks(GuiContainer gui) {
        SlotLockManager manager = SlotLockManager.getInstance();
        boolean anyLocked = false;
        for (Slot slot : (List<Slot>) gui.inventorySlots.inventorySlots) {
            if (slot.inventory != MC.thePlayer.inventory) {
                continue;
            }
            int index = slot.getSlotIndex();
            if (index < 0 || index >= SlotLockManager.SLOT_COUNT) {
                continue;
            }
            if (manager.getState(index)
                .isLocked()) {
                anyLocked = true;
                break;
            }
        }
        if (!anyLocked) {
            return;
        }

        GL11.glDisable(GL11.GL_LIGHTING);
        for (Slot slot : (List<Slot>) gui.inventorySlots.inventorySlots) {
            if (slot.inventory != MC.thePlayer.inventory) {
                continue;
            }
            int index = slot.getSlotIndex();
            if (index < 0 || index >= SlotLockManager.SLOT_COUNT) {
                continue;
            }
            SlotLockState state = manager.getState(index);
            if (!state.isLocked()) {
                continue;
            }
            int x = slot.xDisplayPosition;
            int y = slot.yDisplayPosition;
            int color = state.getType() == SlotLockState.LockType.EMPTY ? COLOR_EMPTY_LOCK : COLOR_TYPE_LOCK;

            // 彩色细边框：1px 宽的四条边（避免遮挡相邻栏位与物品边缘）
            Gui.drawRect(x - 1, y - 1, x + 17, y, color);
            Gui.drawRect(x - 1, y + 16, x + 17, y + 17, color);
            Gui.drawRect(x - 1, y - 1, x, y + 17, color);
            Gui.drawRect(x + 16, y - 1, x + 17, y + 17, color);

            // 类型锁定且槽位为空：半透明渲染模板图标（"取空后的残影"）
            if (state.getType() == SlotLockState.LockType.TYPE && slot.getStack() == null) {
                ItemStack template = state.getTemplate();
                if (template != null) {
                    GL11.glEnable(GL11.GL_BLEND);
                    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                    // 物品绘制会开启 alpha test，若阈值高于残影 alpha 会整片被丢弃；
                    // 显式降到原版默认阈值 0.1，保证 0.4 的残影一定可见
                    GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);
                    GL11.glColor4f(1.0F, 1.0F, 1.0F, GHOST_ALPHA);
                    RenderHelper.enableGUIStandardItemLighting();
                    // RenderItem 在 renderWithColor=true 时会强制 glColor4f(r, g, b, 1.0F)，
                    // 把上面设置的透明度覆盖掉（残影会与真实物品毫无区别）。
                    // 关闭它，让我们的 alpha 生效——这正是"淡化残影"的关键。
                    boolean prevRenderWithColor = RENDER_ITEM.renderWithColor;
                    RENDER_ITEM.renderWithColor = false;
                    RENDER_ITEM.renderItemAndEffectIntoGUI(MC.fontRenderer, MC.getTextureManager(), template, x, y);
                    RENDER_ITEM.renderWithColor = prevRenderWithColor;
                    RenderHelper.disableStandardItemLighting();
                    GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
                    GL11.glDisable(GL11.GL_BLEND);
                }
            }
        }
        // 恢复到本层入口状态（前景层光照为禁用；深度测试保持调用方原状，不可擅自开启）
        GL11.glDisable(GL11.GL_LIGHTING);
    }
}
