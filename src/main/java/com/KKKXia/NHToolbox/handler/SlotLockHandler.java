package com.KKKXia.NHToolbox.handler;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraftforge.client.event.GuiOpenEvent;

import com.KKKXia.NHToolbox.NHToolbox;
import com.KKKXia.NHToolbox.inventory.SlotLockGuiCreative;
import com.KKKXia.NHToolbox.inventory.SlotLockGuiInventory;
import com.KKKXia.NHToolbox.inventory.SlotLockManager;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * 背包栏位锁定：把原版背包界面替换为锁定增强版。
 *
 * <p>
 * 为什么不使用 FML 的 KeyInputEvent/MouseInputEvent：本环境实测这些事件
 * 在原版 GUI 打开时不会到达处理器（而 TickEvent 正常），无法用于界面输入；
 * 1.7.10 的 GuiScreenEvent 只有 Init/Draw/Action，没有输入事件。
 * 可靠做法：用 {@link GuiOpenEvent}（displayGuiScreen 中触发，已从运行时字节码
 * 核实）把 GuiInventory / GuiContainerCreative 替换成覆写版，直接覆写
 * mouseClicked / mouseClickMove / mouseMovedOrUp / keyTyped / drawScreen，
 * 由原版直接调用、必然送达。
 */
public class SlotLockHandler {

    private static final Minecraft MC = Minecraft.getMinecraft();

    private boolean worldLoaded = false;

    public SlotLockHandler() {
        NHToolbox.LOG.info("[SlotLock] GUI 替换安装器已注册（锁键键码 {}）", KeyBindings.LOCK_SLOT.getKeyCode());
    }

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        if (event.gui instanceof SlotLockGuiInventory || event.gui instanceof SlotLockGuiCreative) {
            return; // 避免重复替换
        }
        if (event.gui instanceof GuiInventory) {
            event.gui = new SlotLockGuiInventory(MC.thePlayer);
            NHToolbox.LOG.info("[SlotLock] 背包界面已替换为锁定增强版（生存）");
        } else if (event.gui instanceof GuiContainerCreative) {
            event.gui = new SlotLockGuiCreative(MC.thePlayer);
            NHToolbox.LOG.info("[SlotLock] 背包界面已替换为锁定增强版（创造）");
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (MC.theWorld != null && MC.thePlayer != null) {
            if (!worldLoaded) {
                worldLoaded = true;
                SlotLockManager.getInstance()
                    .load();
                NHToolbox.LOG.info("[SlotLock] 已加载背包栏位锁定状态");
            }
        } else {
            worldLoaded = false;
        }
    }
}
