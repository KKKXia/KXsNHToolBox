package com.KKKXia.NHToolbox.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.KKKXia.NHToolbox.inventory.SlotLockInvTweaksRules;

import invtweaks.InvTweaksContainerManager;
import invtweaks.api.container.ContainerSection;

/**
 * 让 InventoryTweaks 的快捷移动把物品优先放进匹配的锁定格。
 *
 * <p>
 * 为什么挂这里：IT 的快捷移动（空格 + 左键"全部搬走"、ctrl + 左键"搬一个"等）最终都收口到
 * {@code InvTweaksContainerManager.move} / {@code moveSome}，目标格由 IT 自己算
 * （先找能并进去的同类堆叠、再找第一个空格），完全不认识我们的锁定，
 * 于是搬过来的物品会落在普通格子里。
 *
 * <p>
 * 在 HEAD 处把目标下标换成 {@link SlotLockInvTweaksRules#preferredDestination} 给出的
 * "优先锁定格"下标，然后交给 IT 原方法执行——点击序列仍由 IT 发出，
 * 客户端与服务端的容器状态因此保持一致，不会出现两边不同步。
 *
 * <p>
 * 递归安全：改完目标后再次调用 {@code move}，此时优先格就是新的目标格，
 * {@code preferredDestination} 会得出同样的下标 → 条件不成立 → 直接走 IT 原实现，
 * 不会无限递归。只有快捷移动期间才把目标<b>改向优先锁定格</b>
 * （见 {@link MixinInvTweaksShortcutsLock}）；"目标格被锁定拒绝 → 换一个能放的空格"这条兜底
 * 对所有调用都生效，因为它只是把必定会被守卫拦下的放置挪到合法位置，不会打乱整理的排列
 * （整理已被 {@link MixinInvTweaksSortingLock} 告知哪些格子不能用）。
 *
 * <p>
 * {@code remap = false}：InventoryTweaks 不是 Minecraft 类，方法名没有混淆映射。
 * 仅客户端，登记在 mixin 配置的 client 列表里。
 */
@Mixin(InvTweaksContainerManager.class)
public abstract class MixinInvTweaksContainerManagerLock {

    @Inject(method = "move", at = @At("HEAD"), cancellable = true, remap = false)
    private void nhtoolbox$preferLockedDestination(final ContainerSection srcSection, final int srcIndex,
        final ContainerSection destSection, final int destIndex, final CallbackInfoReturnable<Boolean> cir) {
        InvTweaksContainerManager manager = (InvTweaksContainerManager) (Object) this;
        int preferred = SlotLockInvTweaksRules
            .preferredDestination(manager, srcSection, srcIndex, destSection, destIndex, true);
        if (preferred == destIndex) {
            return; // 不需要改（含传入 DROP_SLOT 的丢弃、以及目标区段没有锁定格的情况）
        }
        SlotLockInvTweaksRules.logActivation();
        cir.setReturnValue(manager.move(srcSection, srcIndex, destSection, preferred));
    }

    @Inject(method = "moveSome", at = @At("HEAD"), cancellable = true, remap = false)
    private void nhtoolbox$preferLockedDestinationSome(final ContainerSection srcSection, final int srcIndex,
        final ContainerSection destSection, final int destIndex, final int amount,
        final CallbackInfoReturnable<Boolean> cir) {
        InvTweaksContainerManager manager = (InvTweaksContainerManager) (Object) this;
        int preferred = SlotLockInvTweaksRules
            .preferredDestination(manager, srcSection, srcIndex, destSection, destIndex, true);
        if (preferred == destIndex) {
            return;
        }
        SlotLockInvTweaksRules.logActivation();
        cir.setReturnValue(manager.moveSome(srcSection, srcIndex, destSection, preferred, amount));
    }
}
