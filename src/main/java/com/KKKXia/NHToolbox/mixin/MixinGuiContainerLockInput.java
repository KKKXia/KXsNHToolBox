package com.KKKXia.NHToolbox.mixin;

import java.util.Set;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.KKKXia.NHToolbox.inventory.SlotLockGuiSupport;

/**
 * 所有容器界面（背包、箱子、熔炉、工作台、AE 终端、GT 机器……）的锁定输入拦截。
 *
 * <p>
 * 早先的实现是把 {@code GuiInventory} / {@code GuiContainerCreative} 通过
 * {@code GuiOpenEvent} 替换成子类来覆写输入方法，只能覆盖这两个界面；现在改成对
 * {@link GuiContainer} 本身注入，一处收口、所有容器一致。
 *
 * <p>
 * 注入点都在 HEAD 且 cancellable：命中锁定规则时直接取消原版方法，等于"这次点击/按键
 * 没有发生"，因此不会发出 {@code C0EPacketClickWindow}，服务端也就无从得知。
 *
 * <ul>
 * <li>{@code mouseClicked}：切换锁定 / 吞掉会把物品放进锁定格的那次按下</li>
 * <li>{@code mouseClickMove}：不把锁定槽加入原版拖拽目标集合</li>
 * <li>{@code mouseMovedOrUp}：松开在锁定槽上时吞掉并清理原版拖拽状态</li>
 * <li>{@code keyTyped}：按下锁定键且确实切换了锁定时吞掉该键</li>
 * <li>{@code checkHotbarKeys}：数字键 1-9 的快捷交换会绕过 {@code keyTyped} 的其它分支
 * （例如创造模式搜索页签直接调用它），所以在这里拦，返回 true 表示"已处理"</li>
 * </ul>
 *
 * <p>
 * 仅客户端：{@code GuiContainer} 是 {@code @SideOnly(Side.CLIENT)}，本 mixin 登记在
 * mixin 配置的 client 列表里。
 */
@Mixin(GuiContainer.class)
public abstract class MixinGuiContainerLockInput {

    @Shadow
    protected int guiLeft;

    @Shadow
    protected int guiTop;

    /** 原版拖拽标志（GuiContainer.field_147007_t）。 */
    @Shadow
    protected boolean field_147007_t;

    /** 原版拖拽目标集合（GuiContainer.field_147008_s）。 */
    @Shadow
    @Final
    protected Set<Slot> field_147008_s;

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void nhtoolbox$mouseClicked(final int mouseX, final int mouseY, final int mouseButton,
        final CallbackInfo ci) {
        if (SlotLockGuiSupport.handleMouseClick(gui(), mouseX, mouseY, mouseButton, this.guiLeft, this.guiTop)) {
            ci.cancel(); // 已切换锁定，或已吞掉不兼容的放入/拖拽起始
        }
    }

    @Inject(method = "mouseClickMove", at = @At("HEAD"), cancellable = true)
    private void nhtoolbox$mouseClickMove(final int mouseX, final int mouseY, final int clickedMouseButton,
        final long timeSinceLastClick, final CallbackInfo ci) {
        if (SlotLockGuiSupport.isHeldItemBlockedAt(gui(), mouseX, mouseY, this.guiLeft, this.guiTop)) {
            ci.cancel(); // 不把锁定槽加入原版拖拽目标集合
        }
    }

    @Inject(method = "mouseMovedOrUp", at = @At("HEAD"), cancellable = true)
    private void nhtoolbox$mouseMovedOrUp(final int mouseX, final int mouseY, final int state, final CallbackInfo ci) {
        if (SlotLockGuiSupport.isHeldItemBlockedAt(gui(), mouseX, mouseY, this.guiLeft, this.guiTop)) {
            this.field_147007_t = false;
            this.field_147008_s.clear();
            ci.cancel(); // 吞掉松开：held 留在手上，同时清掉原版拖拽残留状态
        }
    }

    @Inject(method = "keyTyped", at = @At("HEAD"), cancellable = true)
    private void nhtoolbox$keyTyped(final char typedChar, final int keyCode, final CallbackInfo ci) {
        if (!SlotLockGuiSupport.isLockKeyCode(keyCode)) {
            return;
        }
        GuiContainer gui = gui();
        // 只有真的切换了锁定才吞键，否则会把别处的输入（如创造模式搜索框字符）吃掉
        if (SlotLockGuiSupport.handleKeyboardToggle(
            gui,
            SlotLockGuiSupport.mouseX(gui),
            SlotLockGuiSupport.mouseY(gui),
            this.guiLeft,
            this.guiTop)) {
            ci.cancel();
        }
    }

    @Inject(method = "checkHotbarKeys", at = @At("HEAD"), cancellable = true)
    private void nhtoolbox$checkHotbarKeys(final int keyCode, final CallbackInfoReturnable<Boolean> cir) {
        GuiContainer gui = gui();
        if (SlotLockGuiSupport.isHotbarSwapBlocked(
            gui,
            keyCode,
            SlotLockGuiSupport.mouseX(gui),
            SlotLockGuiSupport.mouseY(gui),
            this.guiLeft,
            this.guiTop)) {
            cir.setReturnValue(true); // 视为已处理：原版不交换，物品也不会被塞进锁定格
        }
    }

    @Unique
    private GuiContainer gui() {
        return (GuiContainer) (Object) this;
    }
}
