package com.KKKXia.NHToolbox.mixin;

import java.util.List;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.KKKXia.NHToolbox.inventory.SlotLockMergeRules;

import appeng.container.AEBaseContainer;
import appeng.container.slot.AppEngSlot;

/**
 * 让 AE2 系列界面（ME 终端、合成终端、样板终端、接口等）的快捷移动遵守背包栏位锁定。
 *
 * <p>
 * 为什么需要单独一个 mixin：AE2 的 {@code AEBaseContainer.transferStackInSlot:526} 完全自己实现
 * 了快捷移动（自己找堆叠、自己找空格、自己 putStack），**从不调用**
 * {@code Container.mergeItemStack}，所以 {@code MixinContainerMergeLock} 对它无效——
 * 症状就是"从 ME 终端取出的物品会被塞进类型不匹配的锁定格"。
 *
 * <p>
 * 注入点选在 {@code getValidDestinationSlots:470}（返回候选目标列表）之后：那是 AE2 决定
 * "这个物品能放到哪些栏位"的唯一入口，{@code transferStackInSlot} 拿到列表后只做遍历，
 * 列表里没有的栏位不会被使用。因此在这里剔除/重排即可同时实现：
 * 拒绝该物品的锁定格不出现，匹配的类型锁定格排到最前（优先填充）。
 *
 * <p>
 * 该方法返回的是新建的 {@code ArrayList}（AEBaseContainer:471），调用方只做遍历，
 * 因此替换返回值是安全的。属于通用代码（服务端也会执行快捷移动），登记在 mixin 配置的
 * 通用列表里；本模组已声明 {@code required-after:appliedenergistics2}，AE2 必然存在。
 */
@Mixin(AEBaseContainer.class)
public abstract class MixinAEBaseContainerLock {

    // remap = false：AE2 不是 Minecraft 类，它的方法名没有混淆映射（生产环境也保持原名）
    @Inject(method = "getValidDestinationSlots", at = @At("RETURN"), cancellable = true, remap = false)
    private void nhtoolbox$adjustDestinationSlots(final boolean isPlayerSideSlot, final ItemStack stackInSlot,
        final CallbackInfoReturnable<List<AppEngSlot>> cir) {
        final List<AppEngSlot> slots = cir.getReturnValue();
        if (slots == null || slots.isEmpty() || !SlotLockMergeRules.needsInterception()) {
            return;
        }
        final List<AppEngSlot> adjusted = SlotLockMergeRules.adjustDestinationSlots(slots, stackInSlot);
        if (adjusted != slots) {
            cir.setReturnValue(adjusted);
        }
    }
}
