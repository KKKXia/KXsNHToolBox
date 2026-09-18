package com.KKKXia.NHToolbox.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.KKKXia.NHToolbox.inventory.SlotLockInvTweaksRules;

import invtweaks.InvTweaksHandlerShortcuts;

/**
 * 标记 InventoryTweaks 快捷移动的执行区间，供
 * {@link MixinInvTweaksContainerManagerLock} 判断"这次搬运是不是玩家按快捷键发起的"。
 *
 * <p>
 * {@code handleShortcut()} 是快捷移动唯一的入口（{@code InvTweaks.handleShortcuts}
 * 在玩家按下快捷键并点击时调用它），整理 / 自动补货 / 拾取归位都不走这里，
 * 因此用它当开关最干净：只有快捷移动期间才允许改目标格。
 *
 * <p>
 * {@code handleShortcut} 内部已经把异常吃掉了，正常情况下 RETURN 一定执行；
 * 万一有 Throwable 逃逸导致标志位残留，最坏结果是整理时也按优先锁定格摆放
 * （不影响物品安全），这里不再额外加超时保护。
 *
 * <p>
 * {@code remap = false}：InventoryTweaks 不是 Minecraft 类，方法名没有混淆映射。
 * 仅客户端，登记在 mixin 配置的 client 列表里。
 */
@Mixin(InvTweaksHandlerShortcuts.class)
public abstract class MixinInvTweaksShortcutsLock {

    @Inject(method = "handleShortcut", at = @At("HEAD"), remap = false)
    private void nhtoolbox$beginShortcut(final CallbackInfo ci) {
        SlotLockInvTweaksRules.beginShortcut();
    }

    @Inject(method = "handleShortcut", at = @At("RETURN"), remap = false)
    private void nhtoolbox$endShortcut(final CallbackInfo ci) {
        SlotLockInvTweaksRules.endShortcut();
    }
}
