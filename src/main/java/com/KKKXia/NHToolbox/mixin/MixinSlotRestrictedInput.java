package com.KKKXia.NHToolbox.mixin;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.IBlockAccess;

import com.google.common.base.Optional;

import appeng.api.definitions.IItemDefinition;
import appeng.api.definitions.IItems;
import appeng.container.slot.SlotRestrictedInput;
import appeng.items.storage.ItemViewCell;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 修复：AE 终端侧边栏的 VIEW_CELL 插槽只接受 AE2 原版显示元件。
 * <p>
 * {@link SlotRestrictedInput#isItemValid} 中 {@code VIEW_CELL} 分支使用
 * {@code items.viewCell().isSameAs(stack)} 做精确物品匹配，导致独立注册的
 * {@link ItemViewCell} 子类（如 ExtendItemViewCell）无法放入侧边栏，过滤功能随之失效。
 * <p>
 * 这里通过 {@link Redirect} 替换 {@code IItems.viewCell()} 的返回值：返回一个委托包装，
 * 其 {@code isSameAs} 在通过 AE2 原版校验之外，额外接受任意 {@link ItemViewCell} 子类。
 * 注入点唯一（isItemValid 中只有一处调用 {@code viewCell()}），不依赖 ordinal。
 */
@Mixin(SlotRestrictedInput.class)
public class MixinSlotRestrictedInput {

    @Unique
    private static IItemDefinition nhtoolbox$viewCellDefinition;

    @Redirect(
        method = "isItemValid",
        at = @At(value = "INVOKE", target = "Lappeng/api/definitions/IItems;viewCell()Lappeng/api/definitions/IItemDefinition;"))
    private IItemDefinition nhtoolbox$resolveViewCellDefinition(final IItems items) {
        final IItemDefinition original = items.viewCell();
        if (nhtoolbox$viewCellDefinition == null || nhtoolbox$viewCellDefinition == original) {
            nhtoolbox$viewCellDefinition = new nhtoolbox$ExtendedViewCellDefinition(original);
        }
        return nhtoolbox$viewCellDefinition;
    }

    /** 委托原始定义；仅 isSameAs 增加对 ItemViewCell 子类的兼容。 */
    @Unique
    private static final class nhtoolbox$ExtendedViewCellDefinition implements IItemDefinition {

        private final IItemDefinition delegate;

        nhtoolbox$ExtendedViewCellDefinition(final IItemDefinition delegate) {
            this.delegate = delegate;
        }

        @Override
        public Optional<Item> maybeItem() {
            return this.delegate.maybeItem();
        }

        @Override
        public Optional<ItemStack> maybeStack(final int stackSize) {
            return this.delegate.maybeStack(stackSize);
        }

        @Override
        public boolean isEnabled() {
            return this.delegate.isEnabled();
        }

        @Override
        public boolean isSameAs(final ItemStack comparableStack) {
            return (comparableStack != null && comparableStack.getItem() instanceof ItemViewCell)
                    || this.delegate.isSameAs(comparableStack);
        }

        @Override
        public boolean isSameAs(final IBlockAccess world, final int x, final int y, final int z) {
            return this.delegate.isSameAs(world, x, y, z);
        }
    }
}
