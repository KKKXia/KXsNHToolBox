package com.KKKXia.NHToolbox.handler;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraftforge.client.event.GuiOpenEvent;

import com.KKKXia.NHToolbox.NHToolbox;
import com.KKKXia.NHToolbox.config.ModConfig;
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
 * mouseClicked / mouseClickMove / mouseMovedOrUp / keyTyped / drawGuiContainerForegroundLayer，
 * 由原版直接调用、必然送达。
 */
public class SlotLockHandler {

    private boolean worldLoaded = false;

    public SlotLockHandler() {
        if (ModConfig.isSlotLockEnabled()) {
            NHToolbox.LOG.info("[SlotLock] GUI 替换安装器已注册（开关：开，锁键键码 {}）", KeyBindings.getLockSlotKeyCode());
        } else {
            // 关闭时必须什么都不做：连 KeyBinding 都不创建，原版中键（选取方块）保持原状
            NHToolbox.LOG.info("[SlotLock] 功能关闭：不替换界面、不注册锁定按键，原版中键保持原状");
        }
    }

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        if (!ModConfig.isSlotLockEnabled()) {
            return; // 配置开关关闭（默认）：不改写任何原版界面
        }
        // 只用精确类型判断：instanceof 会连第三方 mod 继承 GuiInventory 的界面一起替换掉，
        // 那些子类自己的按钮与状态会被静默丢弃
        GuiScreen gui = event.gui;
        if (gui == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (gui.getClass() == GuiInventory.class) {
            if (minecraft.thePlayer == null) {
                return;
            }
            event.gui = new SlotLockGuiInventory(minecraft.thePlayer);
            NHToolbox.LOG.debug("[SlotLock] 背包界面已替换为锁定增强版（生存）");
        } else if (gui.getClass() == GuiContainerCreative.class) {
            if (minecraft.thePlayer == null) {
                return;
            }
            event.gui = new SlotLockGuiCreative(minecraft.thePlayer);
            NHToolbox.LOG.debug("[SlotLock] 背包界面已替换为锁定增强版（创造）");
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!ModConfig.isSlotLockEnabled()) {
            return; // 关闭时不读取锁定数据（不触碰 slotLocks.dat）
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.theWorld != null && minecraft.thePlayer != null) {
            if (!worldLoaded) {
                worldLoaded = true;
                SlotLockManager.getInstance()
                    .load();
                NHToolbox.LOG.debug("[SlotLock] 已加载背包栏位锁定状态");
            }
        } else {
            worldLoaded = false;
        }
    }
}
