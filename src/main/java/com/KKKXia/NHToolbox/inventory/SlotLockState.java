package com.KKKXia.NHToolbox.inventory;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/**
 * 单个背包栏位的锁定状态（不可变值对象）。
 *
 * <p>
 * 两种状态：{@link LockType#NONE} 未锁定、{@link LockType#TYPE} 类型锁定
 * （只接收同一种物品，槽位被取空后显示淡化图标）。
 *
 * <p>
 * 早先还有第三种"空锁定"（栏位为空时锁定，之后拒收一切）。它没有物品作为
 * "只收哪一种"的依据，实用价值有限，却要在每条插入链路上多写一条分支，
 * 因此已整体移除；旧存档里残留的空锁定记录（编码 {@link #DEPRECATED_EMPTY_ID}）
 * 读取时静默降级为未锁定。
 */
public final class SlotLockState {

    /**
     * 锁定类型。每个常量带一个稳定的存档编码（{@link #getId()}），
     * 落盘不依赖 {@code ordinal()}，因此重排常量顺序不会破坏既有存档。
     */
    public enum LockType {

        /** 未锁定：允许一切。 */
        NONE(0),

        /** 类型锁定：只能放入同一种物品（可堆叠），物品被取走后仍锁定该类型。 */
        TYPE(2);

        private final int id;

        LockType(int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }

        /** 未知编码（含已废弃的空锁定）一律降级为 {@link #NONE}。 */
        public static LockType fromId(int id) {
            for (LockType type : values()) {
                if (type.id == id) {
                    return type;
                }
            }
            return NONE;
        }
    }

    /**
     * 已废弃的"空锁定"存档编码。编号刻意不复用：旧存档里 id=1 的记录只会被读成未锁定，
     * 不会被误读成别的状态（{@link LockType#TYPE} 因此保持 id=2 不变）。
     */
    public static final int DEPRECATED_EMPTY_ID = 1;

    public static final SlotLockState NONE = new SlotLockState(LockType.NONE, null);

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

    /**
     * 直接访问模板物品，供渲染使用（只读，不要修改返回的栈）。
     *
     * <p>
     * 不要在这里 copy()：{@code ItemStack.copy()} 会连带深拷贝 NBT
     * （ItemStack.java:390-400），而残影每帧都要取一次模板，逐帧深拷贝纯属浪费。
     */
    public ItemStack peekTemplate() {
        return template;
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
        if (type == LockType.TYPE) {
            return template != null && candidate != null && template.isItemEqual(candidate);
        }
        return true; // 未锁定
    }

    public NBTTagCompound toNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setByte("type", (byte) type.getId());
        if (type == LockType.TYPE && template != null) {
            NBTTagCompound itemTag = new NBTTagCompound();
            template.writeToNBT(itemTag);
            tag.setTag("template", itemTag);
        }
        return tag;
    }

    /** 从 NBT 恢复；格式非法或属于已废弃的空锁定（编码 {@link #DEPRECATED_EMPTY_ID}）时降级为未锁定。 */
    public static SlotLockState fromNBT(NBTTagCompound tag) {
        if (tag == null) {
            return NONE;
        }
        if (LockType.fromId(tag.getByte("type")) == LockType.TYPE) {
            ItemStack template = ItemStack.loadItemStackFromNBT(tag.getCompoundTag("template"));
            return template == null ? NONE : ofType(template);
        }
        return NONE;
    }

    /**
     * 该 NBT 是否是已废弃的空锁定记录。
     *
     * <p>
     * 仅供 {@link SlotLockManager#load()} 统计并清理旧档使用，因此不做成公开 API。
     */
    static boolean isDeprecatedEntry(NBTTagCompound tag) {
        return tag != null && tag.hasKey("type") && tag.getByte("type") == DEPRECATED_EMPTY_ID;
    }
}
