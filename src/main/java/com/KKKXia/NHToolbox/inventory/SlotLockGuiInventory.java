package com.KKKXia.NHToolbox.inventory;

import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.entity.player.EntityPlayer;

/**
 * 背包界面（生存）的锁定增强版：直接覆写原版输入/绘制方法，
 * 完全不依赖任何事件总线（1.7.10 的 FML 键盘/鼠标游戏事件在本环境不可靠）。
 */
public class SlotLockGuiInventory extends GuiInventory {

    public SlotLockGuiInventory(EntityPlayer player) {
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
        if (SlotLockGuiSupport.isHeldItemBlockedAt(this, mouseX, mouseY, this.guiLeft, this.guiTop)) {
            return; // 不把锁定槽加入原版拖拽目标集合
        }
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int state) {
        if (SlotLockGuiSupport.isHeldItemBlockedAt(this, mouseX, mouseY, this.guiLeft, this.guiTop)) {
            this.field_147007_t = false;
            this.field_147008_s.clear();
            return; // 吞掉松开，held 留在手上，并清理拖拽残留状态
        }
        super.mouseMovedOrUp(mouseX, mouseY, state);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        int mouseX = SlotLockGuiSupport.mouseX(this);
        int mouseY = SlotLockGuiSupport.mouseY(this);
        if (SlotLockGuiSupport.isLockKeyCode(keyCode)) {
            // 只有真的切换了锁定才吞掉按键，否则会把其它键位一起吃掉
            if (SlotLockGuiSupport.handleKeyboardToggle(this, mouseX, mouseY, this.guiLeft, this.guiTop)) {
                return;
            }
        } else if (SlotLockGuiSupport.isHotbarSwapBlocked(this, keyCode, mouseX, mouseY, this.guiLeft, this.guiTop)) {
            return; // 数字键交换会把快捷栏物品放进锁定栏位
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
