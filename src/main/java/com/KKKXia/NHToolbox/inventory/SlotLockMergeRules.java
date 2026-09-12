package com.KKKXia.NHToolbox.inventory;

import java.util.List;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import com.KKKXia.NHToolbox.NHToolbox;

/**
 * 快捷移动（shift+左键）的锁定规则：让原版 {@code Container.mergeItemStack} 不把物品放进
 * "拒绝该物品的锁定栏位"，并在"快捷栏 -> 主背包"这一次合并里优先使用匹配的类型锁定格。
 *
 * <p>
 * 由 {@link com.KKKXia.NHToolbox.mixin.MixinContainerMergeLock} 调用。1.7.10 的
 * {@code PlayerControllerMP.windowClick:475-479} 在客户端也会执行一次 {@code slotClick}，
 * 所以本类**会同时运行在客户端容器与服务端容器上**；因此这里绝不能引用客户端专属类
 * （Minecraft / GuiScreen / GL 等），否则专用服务端会 NoClassDefFoundError。
 *
 * <p>
 * 性能：锁定的登记表是纯数组，判定只做几次整数比较与虚调用；没有任何锁定时会话
 * （{@link #needsInterception()}）直接返回 false，原版实现原样执行，零额外开销。
 */
public final class SlotLockMergeRules {

    /** 玩家主背包在 {@code ContainerPlayer.transferStackInSlot} 里的合并区间（快捷栏->背包，ContainerPlayer:177）。 */
    private static final int MAIN_INVENTORY_START = 9;
    private static final int MAIN_INVENTORY_END = 36;

    private SlotLockMergeRules() {}

    /** 是否已经打过"mixin 已生效"的一次性日志。 */
    private static boolean loggedActivation;

    /** 是否有任何栏位处于锁定状态；没有则本 mixin 完全不介入。 */
    public static boolean needsInterception() {
        boolean anyLock = SlotLockManager.getInstance()
            .hasAnyLock();
        if (anyLock && !loggedActivation) {
            loggedActivation = true;
            // 只打一次：证明 Container.mergeItemStack 的 mixin 确实被应用（否则快捷移动不会经过这里）
            NHToolbox.LOG.info("[SlotLock] 快捷移动已接入栏位锁定规则");
        }
        return anyLock;
    }

    /**
     * 与 {@code Container.mergeItemStack} 语义一致的合并实现，额外遵守栏位锁定。
     *
     * <p>
     * 与原版的差异只有两点：合并时跳过"锁定且拒绝该物品"的栏位；当这次合并正是
     * "快捷栏 -> 主背包"时，先尝试把物品放进匹配的类型锁定格。其余顺序、堆叠规则、
     * 通知调用（{@code onSlotChanged}）都与原版逐行对应。
     */
    public static boolean merge(List<Slot> slots, ItemStack stack, int startIndex, int endIndex, boolean reverseOrder) {
        if (stack == null || stack.stackSize <= 0 || startIndex >= endIndex) {
            return false;
        }
        boolean moved = false;

        // (1) 优先：并入匹配的类型锁定格（用户要求——"先检查背包中是否有被锁定的 Type 格与之匹配"）
        if (!reverseOrder && startIndex == MAIN_INVENTORY_START && endIndex == MAIN_INVENTORY_END) {
            moved = mergeIntoMatchingLockedSlots(slots, stack, startIndex, endIndex);
            if (stack.stackSize <= 0) {
                return true;
            }
        }

        // (2) 原版第一趟：并入已有同类堆叠（跳过拒绝该物品的锁定栏位）
        int k = reverseOrder ? endIndex - 1 : startIndex;
        if (stack.isStackable()) {
            while (stack.stackSize > 0 && (reverseOrder ? k >= startIndex : k < endIndex)) {
                Slot slot = slots.get(k);
                ItemStack existing = slot.getStack();
                // 先做与原版完全一致的同类堆叠判定，命中后才查锁定（把虚调用留给真正会合并的情况）
                if (existing != null && existing.getItem() == stack.getItem()
                    && (!stack.getHasSubtypes() || stack.getItemDamage() == existing.getItemDamage())
                    && ItemStack.areItemStackTagsEqual(stack, existing)
                    && !isBlocked(slot, stack)) {
                    int total = existing.stackSize + stack.stackSize;
                    if (total <= stack.getMaxStackSize()) {
                        stack.stackSize = 0;
                        existing.stackSize = total;
                        slot.onSlotChanged();
                        moved = true;
                    } else if (existing.stackSize < stack.getMaxStackSize()) {
                        stack.stackSize -= stack.getMaxStackSize() - existing.stackSize;
                        existing.stackSize = stack.getMaxStackSize();
                        slot.onSlotChanged();
                        moved = true;
                    }
                }
                k += reverseOrder ? -1 : 1;
            }
        }

        // (3) 原版第二趟：放进第一个空格（跳过拒绝该物品的锁定栏位）
        if (stack.stackSize > 0) {
            k = reverseOrder ? endIndex - 1 : startIndex;
            while (reverseOrder ? k >= startIndex : k < endIndex) {
                Slot slot = slots.get(k);
                if (slot.getStack() == null && !isBlocked(slot, stack)) {
                    slot.putStack(stack.copy());
                    slot.onSlotChanged();
                    stack.stackSize = 0;
                    moved = true;
                    break;
                }
                k += reverseOrder ? -1 : 1;
            }
        }
        return moved;
    }

    /**
     * 第一优先：类型锁定且接受该物品的栏位。先补现有堆叠、再放空槽（与原版两趟顺序一致）。
     */
    private static boolean mergeIntoMatchingLockedSlots(List<Slot> slots, ItemStack stack, int startIndex,
        int endIndex) {
        SlotLockManager manager = SlotLockManager.getInstance();
        boolean moved = false;
        for (int pass = 0; pass < 2 && stack.stackSize > 0; pass++) {
            for (int k = startIndex; k < endIndex && stack.stackSize > 0; k++) {
                Slot slot = slots.get(k);
                int index = playerSlotIndex(slot);
                if (index < 0) {
                    continue;
                }
                SlotLockState state = manager.getState(index);
                if (state.getType() != SlotLockState.LockType.TYPE || !state.accepts(stack)) {
                    continue;
                }
                ItemStack existing = slot.getStack();
                if (existing == null) {
                    if (pass == 1) {
                        slot.putStack(stack.copy());
                        slot.onSlotChanged();
                        stack.stackSize = 0;
                        moved = true;
                    }
                } else if (pass == 0 && existing.getItem() == stack.getItem()
                    && (!stack.getHasSubtypes() || stack.getItemDamage() == existing.getItemDamage())
                    && ItemStack.areItemStackTagsEqual(stack, existing)) {
                        int total = existing.stackSize + stack.stackSize;
                        if (total <= stack.getMaxStackSize()) {
                            stack.stackSize = 0;
                            existing.stackSize = total;
                            slot.onSlotChanged();
                            moved = true;
                        } else if (existing.stackSize < stack.getMaxStackSize()) {
                            stack.stackSize -= stack.getMaxStackSize() - existing.stackSize;
                            existing.stackSize = stack.getMaxStackSize();
                            slot.onSlotChanged();
                            moved = true;
                        }
                    }
            }
        }
        return moved;
    }

    /** 该栏位是否因锁定而拒绝此物品（非玩家背包栏位一律不拦）。 */
    private static boolean isBlocked(Slot slot, ItemStack stack) {
        int index = playerSlotIndex(slot);
        return index >= 0 && !SlotLockManager.getInstance()
            .canAccept(index, stack);
    }

    /**
     * 解析玩家背包索引（0-35），非玩家栏位返回 -1。
     *
     * <p>
     * 与客户端渲染用的 {@code SlotLockGuiSupport.playerSlotIndex} 的区别：这里不依赖
     * {@code Minecraft.thePlayer}（服务端线程拿不到客户端玩家），只认
     * {@link InventoryPlayer} 类型的库存；护甲格(36-39)与合成格会被范围检查排除。
     * 创造模式的 {@code CreativeSlot} 包装槽用原版 API {@code isSlotInInventory} 反查真实索引。
     */
    public static int playerSlotIndex(Slot slot) {
        IInventory inventory = slot.inventory;
        if (!(inventory instanceof InventoryPlayer)) {
            return -1;
        }
        int index = slot.getSlotIndex();
        if (index >= 0 && index < SlotLockManager.SLOT_COUNT && slot.isSlotInInventory(inventory, index)) {
            return index;
        }
        for (int i = 0; i < SlotLockManager.SLOT_COUNT; i++) {
            if (slot.isSlotInInventory(inventory, i)) {
                return i;
            }
        }
        return -1;
    }
}
