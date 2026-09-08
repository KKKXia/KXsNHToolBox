package com.KKKXia.NHToolbox.inventory;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/**
 * 单个背包栏位的锁定状态（不可变值对象）。
 *
 * <p>
 * 三种状态：{@link LockType#NONE} 未锁定、{@link LockType#EMPTY} 空锁定（拒收一切）、
 * {@link LockType#TYPE} 类型锁定（只接收同一种物品，槽位取空后显示淡化图标）。
 */
public final class SlotLockState {

    public enum LockType {

        /** 未锁定：允许一切。 */
        NONE,

        /** 空锁定：该栏位为空时锁定，之后任何物品都不能放入。 */
        EMPTY,

        /** 类型锁定：只能放入同一种物品（可堆叠），物品被取走后仍锁定该类型。 */
        TYPE
    }

    public static final SlotLockState NONE = new SlotLockState(LockType.NONE, null);
    public static final SlotLockState EMPTY = new SlotLockState(LockType.EMPTY, null);

    private final LockType type;
    /** 仅 {@link LockType#TYPE} 时非空，记录锁定的物品种类（stackSize 恒为 1）。 */
    private final ItemStack template;

    private SlotLockState(LockType type, ItemStack template) {
        this.type = type;
        this.template = template;
    }

    public static SlotLockState ofType(ItemStack template) {
        ItemStack copied = template == null ? null : template.copy();
        if (copied != null) {
            copied.stackSize = 1;
        }
        return new SlotLockState(LockType.TYPE, copied);
    }

    public LockType getType() {
        return type;
    }

    /** 返回模板物品的副本（供渲染使用），仅 TYPE 锁定返回非空。 */
    public ItemStack getTemplate() {
        return template == null ? null : template.copy();
    }

    public boolean isLocked() {
        return type != LockType.NONE;
    }

    /**
     * 判断给定堆叠是否允许被放入该栏位。
     *
     * <p>
     * 同种判定使用 {@link ItemStack#isItemEqual(ItemStack)}：物品 ID + 损伤值，
     * 忽略 stackSize 与 NBT，与原版可堆叠规则一致（注意不要用
     * {@link ItemStack#areItemStacksEqual(ItemStack, ItemStack)}，它额外比较
     * stackSize，会导致同种物品因数量不同而被拒绝）。
     */
    public boolean accepts(ItemStack candidate) {
        switch (type) {
            case TYPE:
                return template != null && candidate != null && template.isItemEqual(candidate);
            case EMPTY:
                return false;
            case NONE:
            default:
                return true;
        }
    }

    public NBTTagCompound toNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setByte("type", (byte) type.ordinal());
        if (type == LockType.TYPE && template != null) {
            NBTTagCompound itemTag = new NBTTagCompound();
            template.writeToNBT(itemTag);
            tag.setTag("template", itemTag);
        }
        return tag;
    }

    /** 从 NBT 恢复；格式非法时静默降级为未锁定。 */
    public static SlotLockState fromNBT(NBTTagCompound tag) {
        if (tag == null) {
            return NONE;
        }
        int type = tag.getByte("type");
        if (type == LockType.EMPTY.ordinal()) {
            return EMPTY;
        }
        if (type == LockType.TYPE.ordinal()) {
            ItemStack template = ItemStack.loadItemStackFromNBT(tag.getCompoundTag("template"));
            return template == null ? NONE : ofType(template);
        }
        return NONE;
    }
}
