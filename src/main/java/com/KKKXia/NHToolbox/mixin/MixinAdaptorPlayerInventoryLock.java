package com.KKKXia.NHToolbox.mixin;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.KKKXia.NHToolbox.inventory.SlotLockManager;

import appeng.util.inv.AdaptorPlayerInventory;

/**
 * 兜底：AE2 的玩家背包适配器在"这个槽位能不能放这个物品"上遵守栏位锁定。
 *
 * <p>
 * {@code AdaptorPlayerInventory.isItemValidForSlot} 直接委托给
 * {@code InventoryPlayer.isItemValidForSlot}（恒为 true），所以 AE2 的插入循环
 * （{@code AdaptorIInventory:217/241}）会毫不犹豫地使用 EMPTY 锁定格。
 * 主路径已由 {@code MixinAdaptorIInventoryLock} 接管，这里再压一道：
 * 返回 false 让任何走这个适配器的 AE2 代码都跳过拒绝该物品的锁定栏位。
 */
@Mixin(AdaptorPlayerInventory.class)
public abstract class MixinAdaptorPlayerInventoryLock {

    @Inject(method = "isItemValidForSlot", at = @At("HEAD"), cancellable = true)
    private void nhtoolbox$respectLocks(final int slot, final ItemStack stack,
        final CallbackInfoReturnable<Boolean> cir) {
        if (!SlotLockManager.getInstance()
            .canAccept(slot, stack)) {
            cir.setReturnValue(false);
        }
    }
}
