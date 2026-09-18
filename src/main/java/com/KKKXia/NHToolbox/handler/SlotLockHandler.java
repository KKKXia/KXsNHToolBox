package com.KKKXia.NHToolbox.handler;

import net.minecraft.client.Minecraft;

import com.KKKXia.NHToolbox.NHToolbox;
import com.KKKXia.NHToolbox.config.ModConfig;
import com.KKKXia.NHToolbox.inventory.SlotLockManager;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * 背包栏位锁定的客户端生命周期：进入世界后加载锁定数据。
 *
 * <p>
 * 界面相关的部分全部走 mixin，不再需要任何"替换原版界面"的机制：
 * <ul>
 * <li>{@code MixinGuiContainerLockOverlay}：所有容器界面绘制锁定边框/残影</li>
 * <li>{@code MixinGuiContainerLockInput}：所有容器界面的鼠标/键盘拦截</li>
 * <li>{@code MixinContainerMergeLock}：快捷移动（shift+左键）遵守锁定</li>
 * </ul>
 * 因此也不需要再用 {@code GuiOpenEvent} 改写界面——功能关闭时锁定数据不加载，
 * 各 mixin 的判定全部落到"无锁定"分支，等价于完全不介入。
 */
public class SlotLockHandler {

    private boolean worldLoaded = false;

    public SlotLockHandler() {
        if (ModConfig.isSlotLockEnabled()) {
            NHToolbox.LOG.info("[SlotLock] 功能已启用（锁键键码 {}），所有容器界面均生效", KeyBindings.getLockSlotKeyCode());
        } else {
            // 关闭时必须什么都不做：连 KeyBinding 都不创建，原版中键（选取方块）保持原状
            NHToolbox.LOG.info("[SlotLock] 功能关闭：不注册锁定按键、不读取锁定数据，原版行为不变");
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
