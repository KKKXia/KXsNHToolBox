package com.KKKXia.NHToolbox.mixin;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.KKKXia.NHToolbox.inventory.SlotLockInventoryRules;
import com.KKKXia.NHToolbox.inventory.SlotLockManager;

/**
 * 让"物品进入玩家背包"的路径遵守栏位锁定（主要是<b>从世界里捡起物品</b>）。
 *
 * <p>
 * 为什么必须动这里：拾取走的是 {@code EntityItem.onCollideWithPlayer} ->
 * {@code InventoryPlayer.addItemStackToInventory} -> {@code storePartialItemStack}
 * （先补同类堆叠、再找第一个空格，快捷栏优先），全程不经过容器，也不调用
 * {@code Container.mergeItemStack}，所以 {@link MixinContainerMergeLock} 完全看不到它——
 * 症状就是"捡起来的物品不会进锁定格"。
 * 背包模组的自动拾取（{@code BackpackUtil.pickupItem}）只负责塞进穿戴中的背包，
 * 剩下的部分仍然由这条原版链路处理，因此这里补上正好接住。
 *
 * <p>
 * 注入点在 HEAD：先让 {@link SlotLockInventoryRules} 把物品放进匹配的类型锁定格，
 * 整叠放完就直接返回 true（等价于原版的"全部放入"，物品实体才会被移除），
 * 放不完的则原样走原版逻辑继续找地方。
 *
 * <p>
 * 本 mixin 登记在通用列表：该方法客户端/服务端都会调用，SP 下两端一致；
 * MP 且服务端没装本模组时，这条规则与服务端行为无关（锁定功能整体是客户端侧的）。
 */
@Mixin(InventoryPlayer.class)
public abstract class MixinInventoryPlayerLock {

    @Inject(method = "addItemStackToInventory", at = @At("HEAD"), cancellable = true)
    private void nhtoolbox$preferLockedSlots(final ItemStack stack, final CallbackInfoReturnable<Boolean> cir) {
        if (!SlotLockManager.getInstance()
            .hasAnyLock()) {
            return; // 没有任何锁定：原版逻辑原样执行
        }
        if (SlotLockInventoryRules.insertIntoLockedSlots((InventoryPlayer) (Object) this, stack)) {
            cir.setReturnValue(Boolean.TRUE);
        }
    }
}
