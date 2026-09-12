package com.KKKXia.NHToolbox.inventory;

import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.entity.player.EntityPlayer;

/**
 * 背包界面（创造）的锁定增强版：与 {@link SlotLockGuiInventory} 相同的覆写逻辑。
 */
public class SlotLockGuiCreative extends GuiContainerCreative {

    public SlotLockGuiCreative(EntityPlayer player) {
        super(player);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (SlotLockGuiSupport.handleMouseClick(this, mouseX, mouseY, mouseButton, this.guiLeft, this.guiTop)) {
            return; // 已切换锁定或已吞掉不兼容操作
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (SlotLockGuiSupport.shouldBlockDragMove(this, mouseX, mouseY, this.guiLeft, this.guiTop)) {
            return; // 不把锁定槽加入拖拽目标集合
        }
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int state) {
        if (SlotLockGuiSupport.shouldBlockRelease(this, mouseX, mouseY, this.guiLeft, this.guiTop)) {
            this.field_147007_t = false;
            this.field_147008_s.clear();
            return; // 吞掉松开，held 留在手上，并清理拖拽残留状态
        }
        super.mouseMovedOrUp(mouseX, mouseY, state);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (SlotLockGuiSupport.isLockKeyCode(keyCode)) {
            int mouseX = org.lwjgl.input.Mouse.getX() * this.width / this.mc.displayWidth;
            int mouseY = this.height - org.lwjgl.input.Mouse.getY() * this.height / this.mc.displayHeight - 1;
            SlotLockGuiSupport.handleKeyboardToggle(this, mouseX, mouseY, this.guiLeft, this.guiTop);
            return; // 匹配到锁定键时吞掉，避免进入搜索框/原版 keyTyped
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        super.drawGuiContainerForegroundLayer(mouseX, mouseY);
        // 前景层位于"槽位物品(之后) 与 tooltip(之前)"之间，在此绘制边框
        // 既盖住槽位物品，又不会遮挡 tooltip（原版 tooltip 在本层之后绘制）
        SlotLockGuiSupport.renderLocks(this);
    }
}
