package com.KKKXia.NHToolbox.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.KKKXia.NHToolbox.inventory.SlotLockInvTweaksRules;

import invtweaks.InvTweaksContainerSectionManager;
import invtweaks.InvTweaksHandlerSorting;

/**
 * 让 InventoryTweaks 的整理绕开被锁定的栏位。
 *
 * <p>
 * IT 的整理算法本来就带一套"锁定栏位"概念（{@code lockPriorities}：大于 0 的栏位不会被搬空、
 * 也不会被塞入别的物品，同种物品仍可并进去），但那个数组只对玩家背包区段从规则文件取值，
 * 箱子 / 背包格区段一律是全 0 的静态默认数组——也就是说，背包模组背包里的锁定格在整理时
 * 完全不被尊重。整理把别的物品往锁定格里塞 → 我们自己的 {@code Container.slotClick} 守卫拦下这次放置
 * → 物品卡在光标上 → IT 的补救逻辑（找一个空位放下）把它丢进玩家背包，
 * 症状就是"整理后随机物品跑进玩家物品栏、越整理越乱"。
 *
 * <p>
 * 注入点选在 {@code sort()} 的 HEAD：所有整理入口（GUI 排序按钮、排序快捷键、API）都是
 * {@code new InvTweaksHandlerSorting(...).sort()}，一处收口；而构造函数只准备规则与缓存，
 * 不读取 {@code lockPriorities}，所以在 HEAD 替换数组不会漏掉任何判定。
 *
 * <p>
 * {@code @Shadow(remap = false)}：这些字段声明在 InventoryTweaks 自己的类里，没有混淆映射。
 * 仅客户端，登记在 mixin 配置的 client 列表里。
 */
@Mixin(InvTweaksHandlerSorting.class)
public abstract class MixinInvTweaksSortingLock {

    @Shadow(remap = false)
    private InvTweaksContainerSectionManager containerMgr;

    @Shadow(remap = false)
    private int[] lockPriorities;

    @Shadow(remap = false)
    private int size;

    @Inject(method = "sort", at = @At("HEAD"), remap = false)
    private void nhtoolbox$respectLocksWhenSorting(final CallbackInfo ci) {
        this.lockPriorities = SlotLockInvTweaksRules
            .applyLocksToSorting(this.containerMgr, this.lockPriorities, this.size);
    }
}
