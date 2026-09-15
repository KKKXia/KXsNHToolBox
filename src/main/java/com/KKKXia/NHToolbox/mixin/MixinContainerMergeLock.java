package com.KKKXia.NHToolbox.mixin;

import java.util.List;

import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.KKKXia.NHToolbox.inventory.SlotLockMergeRules;

/**
 * 让快捷移动（shift+左键）遵守背包栏位锁定。
 *
 * <p>
 * 为什么必须动这里：shift+左键走的是 {@code Container.slotClick} 的 mode=1 ->
 * {@code transferStackInSlot} -> {@code Container.mergeItemStack}，目标栏位由原版按
 * "先补同类堆叠、再找第一个空格"决定，和 GUI 层的鼠标拦截完全无关，所以会出现
 * "锁定的空格仍然被快捷移动塞进物品"。{@code mergeItemStack} 是所有容器共用的收口，
 * 在这里拦一次即可同时覆盖背包、箱子、工作台等界面对玩家背包栏位的快捷移动。
 *
 * <p>
 * 1.7.10 的 {@code PlayerControllerMP.windowClick:475-479} 会在客户端也执行一次
 * {@code slotClick}（乐观更新），所以本 mixin 在客户端与服务端都会生效，两边结果一致。
 *
 * <p>
 * 性能：没有任何锁定栏位时 {@link SlotLockMergeRules#needsInterception()} 直接返回 false，
 * 原版方法体原样执行，只有一次静态调用 + 一次 int 比较的开销。
 */
@Mixin(Container.class)
public abstract class MixinContainerMergeLock {

    @Shadow
    public List<Slot> inventorySlots;

    @Inject(method = "mergeItemStack", at = @At("HEAD"), cancellable = true)
    private void nhtoolbox$mergeRespectingLocks(final ItemStack stack, final int startIndex, final int endIndex,
        final boolean reverseOrder, final CallbackInfoReturnable<Boolean> cir) {
        if (!SlotLockMergeRules.needsInterception()) {
            return; // 无锁定：完全走原版实现
        }
        cir.setReturnValue(SlotLockMergeRules.merge(this.inventorySlots, stack, startIndex, endIndex, reverseOrder));
    }
}
