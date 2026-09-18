package com.KKKXia.NHToolbox.mixin;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.KKKXia.NHToolbox.inventory.SlotLockClickGuard;
import com.KKKXia.NHToolbox.inventory.SlotLockManager;

/**
 * 在 {@code Container.slotClick} 这一层拦住"往锁定栏位里放东西"的点击。
 *
 * <p>
 * 为什么需要它：GUI 层的守卫只覆盖真实鼠标事件，而 InventoryTweaks 的整理、NEI 的快速移动、
 * 其它 mod 的搬运都是直接调用 {@code windowClick -> slotClick} 批量下点击，绕开鼠标事件，
 * 于是能把物品整理进锁定栏位。{@code slotClick} 是所有点击的统一收口，挂在这里一次全覆盖；
 * 而且客户端容器与服务端容器执行的是同一份代码（单机同一个 JVM），两端结果一致，
 * 不会出现"客户端拦下、服务端照做"的错位。
 *
 * <p>
 * 命中时整次取消并返回"光标当前物品"，语义上等于这次点击什么都没发生；
 * 具体判定见 {@link SlotLockClickGuard}（只守 mode 0 的光标放置与 mode 2 的数字键交换）。
 */
@Mixin(Container.class)
public abstract class MixinContainerSlotClickLock {

    @Inject(method = "slotClick", at = @At("HEAD"), cancellable = true)
    private void nhtoolbox$guardLockedSlots(final int slotId, final int clickedButton, final int mode,
        final EntityPlayer player, final CallbackInfoReturnable<ItemStack> cir) {
        if (!SlotLockManager.getInstance()
            .hasAnyLock()) {
            return;
        }
        if (SlotLockClickGuard.shouldBlock((Container) (Object) this, slotId, clickedButton, mode, player)) {
            SlotLockClickGuard.logActivation();
            cir.setReturnValue(player.inventory.getItemStack());
        }
    }
}
