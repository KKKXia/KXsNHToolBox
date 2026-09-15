package com.KKKXia.NHToolbox.mixin;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.KKKXia.NHToolbox.inventory.SlotLockAE2Rules;
import com.KKKXia.NHToolbox.inventory.SlotLockManager;

import appeng.api.config.InsertionMode;
import appeng.util.inv.AdaptorIInventory;
import appeng.util.inv.AdaptorPlayerInventory;

/**
 * 让 AE2 的库存适配器把物品放进玩家背包时遵守栏位锁定。
 *
 * <p>
 * ME 终端 shift+左键取出网络物品的链路是
 * {@code ContainerMEMonitorable.doAction(SHIFT_CLICK):669} ->
 * {@code InventoryAdaptor.addItems()}（先用 {@code simulateAdd} 算容量、再从网络抽取、
 * 最后真插），全程不碰容器，所以 {@code MixinContainerMergeLock} 与
 * {@code MixinAEBaseContainerLock} 都拦不到——症状正是"取出的物品被塞进 EMPTY 锁定格"。
 *
 * <p>
 * 这里注入 {@code AdaptorIInventory} 的私有 {@code addItems(ItemStack, boolean, InsertionMode)}
 * （第 199 行，公开的 {@code addItems}/{@code simulateAdd} 全部转发到它），只在目标是玩家背包
 * 适配器时接管，改为调用 {@link SlotLockAE2Rules#addItems}：跳过拒绝该物品的锁定格、并优先
 * 使用匹配的类型锁定格。模拟与真插走同一套代码，因此不会出现"抽出来放不下"的损失。
 *
 * <p>
 * {@code remap = false}：AE2 不是 Minecraft 类，方法名没有混淆映射。
 */
@Mixin(AdaptorIInventory.class)
public abstract class MixinAdaptorIInventoryLock {

    /** AE2 自己的私有字段（没有混淆映射，因此 remap = false）。 */
    @Shadow(remap = false)
    @Final
    private IInventory i;

    @Inject(
        method = "addItems(Lnet/minecraft/item/ItemStack;ZLappeng/api/config/InsertionMode;)Lnet/minecraft/item/ItemStack;",
        at = @At("HEAD"),
        cancellable = true,
        remap = false)
    private void nhtoolbox$addItemsRespectingLocks(final ItemStack itemsToAdd, final boolean modulate,
        final InsertionMode insertionMode, final CallbackInfoReturnable<ItemStack> cir) {
        if (!(this.i instanceof AdaptorPlayerInventory) || !SlotLockManager.getInstance()
            .hasAnyLock()) {
            return; // 非玩家背包（箱子、ME 驱动器等）或没有任何锁定：完全走 AE2 原实现
        }
        cir.setReturnValue(SlotLockAE2Rules.addItems(this.i, itemsToAdd, modulate, insertionMode));
    }
}
