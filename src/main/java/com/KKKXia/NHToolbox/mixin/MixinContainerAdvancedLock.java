package com.KKKXia.NHToolbox.mixin;

import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.KKKXia.NHToolbox.inventory.SlotLockManager;
import com.KKKXia.NHToolbox.inventory.SlotLockMergeRules;

import de.eydamos.backpack.inventory.container.ContainerAdvanced;

/**
 * 让背包模组（Minecraft-Backpack-Mod）的界面遵守栏位锁定。
 *
 * <p>
 * 为什么需要单独一个 mixin：{@code ContainerAdvanced} 为了"忽略被禁用的槽位"而
 * <b>覆写</b>了 {@code Container.mergeItemStack}（第 205 行，一份原版副本 + 额外调用
 * {@code slot.isItemValid}），覆写之后注入基类的 {@code MixinContainerMergeLock} 就不再执行——
 * 而这个容器同时装着背包格（{@code SlotBackpack}）与玩家格子（原版 {@code Slot}），
 * 于是"shift+左键在背包与玩家背包之间移动"整条链路都没有锁定规则。
 *
 * <p>
 * 注入点在该覆写的 HEAD，转发给 {@link SlotLockMergeRules#merge}（按槽位自身判定，
 * 因此玩家栏位与背包栏位一起生效：拒绝该物品的锁定格会被跳过，匹配的类型锁定格优先）。
 * {@code checkIsItemValid = true}：背包模组原有的 {@code slot.isItemValid} 检查
 * （禁止背包套背包等）被完整保留。
 *
 * <p>
 * 顺带覆盖：自动拾取 {@code BackpackUtil.pickupItem -> ContainerPickup.pickupItem} 也调用
 * 同一个 {@code mergeItemStack}，因此拾取时会跳过空锁定格、并优先填匹配的类型锁定格。
 *
 * <p>
 * 本类刻意不 {@code @Shadow} 任何字段：槽位表由 {@link SlotLockMergeRules} 这个普通类去读，
 * 免得注解处理器解析不了继承自 {@code Container} 的字段（那会导致生产环境注入失败）。
 */
@Mixin(ContainerAdvanced.class)
public abstract class MixinContainerAdvancedLock {

    @Inject(method = "mergeItemStack", at = @At("HEAD"), cancellable = true)
    private void nhtoolbox$mergeRespectingLocks(final ItemStack sourceStack, final int firstSlot, final int lastSlot,
        final boolean backwards, final CallbackInfoReturnable<Boolean> cir) {
        if (!SlotLockManager.getInstance()
            .hasAnyLock()) {
            return; // 没有任何锁定：完全走背包模组自己的实现
        }
        SlotLockMergeRules.logBackpackActivation();
        cir.setReturnValue(
            SlotLockMergeRules.merge((Container) (Object) this, sourceStack, firstSlot, lastSlot, backwards, true));
    }
}
