package com.KKKXia.NHToolbox.mixin;

import net.minecraft.client.gui.inventory.GuiContainer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.KKKXia.NHToolbox.NHToolbox;
import com.KKKXia.NHToolbox.config.ModConfig;
import com.KKKXia.NHToolbox.inventory.SlotLockGuiSupport;

/**
 * 让所有容器界面（箱子、熔炉、工作台、AE 终端、GT 机器……）都显示玩家背包栏位的锁定状态。
 *
 * <p>
 * 注入点选在 {@code GuiContainer.drawScreen} 中对 {@code drawGuiContainerForegroundLayer}
 * 的那次虚调用之后，而不是注入 {@code drawGuiContainerForegroundLayer} 本身：
 * 原版多数界面（{@code GuiInventory:64}、{@code GuiFurnace}、{@code GuiChest}……）都覆写了
 * 前景层且**不调用 super**，注入基类方法会整个漏掉它们；注入 drawScreen 的调用点则对任何
 * 覆写都成立。
 *
 * <p>
 * 这个位置与原版绘制顺序一致：槽位物品(GuiContainer:114) -> 前景层(134) -> 手持物品 -> tooltip(186)，
 * 所以既能盖住物品图标又不会遮挡 tooltip；此时矩阵已按 (guiLeft, guiTop) 平移，
 * 光照状态也与前景层入口完全一致（LIGHTING 关、LIGHT0/LIGHT1/COLOR_MATERIAL 开）。
 *
 * <p>
 * 仅客户端：{@code GuiContainer} 是 {@code @SideOnly(Side.CLIENT)}，因此本 mixin 登记在
 * mixin 配置的 client 列表里。
 */
@Mixin(GuiContainer.class)
public abstract class MixinGuiContainerLockOverlay {

    /** 只打一次日志，用于证明本 mixin 确实被应用（否则快捷/显示都不会经过这里）。 */
    private static boolean nhtoolbox$loggedActivation;

    @Inject(
        method = "drawScreen",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/inventory/GuiContainer;drawGuiContainerForegroundLayer(II)V",
            shift = At.Shift.AFTER))
    private void nhtoolbox$renderSlotLocks(final int mouseX, final int mouseY, final float partialTicks,
        final CallbackInfo ci) {
        if (!nhtoolbox$loggedActivation && ModConfig.isSlotLockEnabled()) {
            nhtoolbox$loggedActivation = true;
            NHToolbox.LOG.info("[SlotLock] 容器界面锁定显示已接入（所有 GuiContainer：箱子/熔炉/终端等）");
        }
        SlotLockGuiSupport.renderLocks((GuiContainer) (Object) this);
    }
}
