package com.KKKXia.NHToolbox.inventory;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import com.KKKXia.NHToolbox.NHToolbox;

/**
 * 快捷移动（shift+左键）的锁定规则：让原版 {@code Container.mergeItemStack} 不把物品放进
 * "拒绝该物品的锁定栏位"，并且在物品进入玩家背包时优先放进匹配的类型锁定格。
 *
 * <p>
 * 对方向不敏感、对容器不敏感：无论是背包内"快捷栏 -> 主背包"、还是箱子 / AE 终端 /
 * 熔炉等界面"容器 -> 背包"，只要这次合并的目标范围里含玩家背包栏位就走优先逻辑；
 * 反方向（"背包 -> 容器"）范围里没有玩家栏位，优先趟自然空转。
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
     * 与原版的差异只有两点：合并时跳过"锁定且拒绝该物品"的栏位；如果这次合并的目标范围里
     * 含玩家背包栏位，先尝试把物品放进其中"匹配的类型锁定格"。其余顺序、堆叠规则、
     * 通知调用（{@code onSlotChanged}）都与原版逐行对应。
     *
     * <p>
     * 为什么不用固定的下标区间来判断"这是往背包里放"：不同容器调用 mergeItemStack 的
     * 参数完全不同——背包内是 {@code (9, 36, false)}，箱子是
     * {@code (箱子大小, 箱子大小+36, true)}（ContainerChest，反向、先填快捷栏），
     * AE 终端等 mod 容器又是各自的一套。所以这里只按"范围内是否存在玩家背包栏位"来判定，
     * 由 {@link #playerSlotIndex} 逐槽识别；范围里没有玩家栏位时（例如"背包 -> 箱子"）
     * 优先趟只会做几十次极廉价的 instanceof 判定后空手而归。
     */
    public static boolean merge(List<Slot> slots, ItemStack stack, int startIndex, int endIndex, boolean reverseOrder) {
        if (stack == null || stack.stackSize <= 0 || startIndex >= endIndex) {
            return false;
        }
        boolean moved = false;

        // (1) 优先：并入匹配的类型锁定格（背包、箱子/AE 终端等任何"往背包里放"的合并都适用）
        moved = mergeIntoMatchingLockedSlots(slots, stack, startIndex, endIndex);
        if (stack.stackSize <= 0) {
            return true;
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
     * 第一优先：目标范围内"类型锁定且接受该物品"的玩家背包栏位。
     * 先补现有堆叠、再放空槽（与原版两趟顺序一致）。
     *
     * <p>
     * 范围内的非玩家栏位（箱子格子、AE 网络格、合成格……）会被 {@link #playerSlotIndex}
     * 过滤掉，因此"背包 -> 箱子"这类合并在这一趟不会产生任何改动。
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

    // =====================================================================
    // AE2 专用：候选目标列表的过滤 + 优先（AE2 不走 Container.mergeItemStack）
    // =====================================================================
    /**
     * 调整"候选目标栏位列表"：剔除拒绝该物品的锁定栏位，并把匹配的类型锁定格提到最前。
     *
     * <p>
     * AE2 的 {@code AEBaseContainer.transferStackInSlot} 完全自己实现快捷移动
     * （不像原版那样调用 {@code Container.mergeItemStack}），它决定"能放到哪里"的唯一入口是
     * {@code getValidDestinationSlots}，所以在这里过滤/重排就等于同时满足两条规则：
     * 空锁定格不再被塞入，且类型锁定格优先拿到匹配物品。
     *
     * <p>
     * 目标列表里没有玩家背包栏位时（例如"背包 -> ME 网络"）原样返回，零分配。
     *
     * @return 原列表（无需调整）或调整后的新列表
     */
    public static <T extends Slot> List<T> adjustDestinationSlots(List<T> slots, ItemStack stack) {
        if (slots == null || slots.isEmpty() || stack == null) {
            return slots;
        }
        SlotLockManager manager = SlotLockManager.getInstance();
        boolean hasLocked = false;
        boolean hasPreferred = false;
        for (T slot : slots) {
            int index = playerSlotIndex(slot);
            if (index < 0) {
                continue;
            }
            SlotLockState state = manager.getState(index);
            if (!state.isLocked()) {
                continue;
            }
            hasLocked = true;
            if (isPreferredTarget(state, stack)) {
                hasPreferred = true;
                break;
            }
        }
        if (!hasLocked) {
            return slots; // 目标里没有锁定格：原样返回
        }

        List<T> adjusted = new ArrayList<T>(slots.size());
        if (hasPreferred) {
            for (T slot : slots) {
                if (isPreferredTarget(slot, stack)) {
                    adjusted.add(slot); // 匹配的类型锁定格优先
                }
            }
        }
        for (T slot : slots) {
            if (isBlocked(slot, stack) || (hasPreferred && isPreferredTarget(slot, stack))) {
                continue; // 拒绝该物品的锁定格剔除；优先格已加过
            }
            adjusted.add(slot);
        }
        return adjusted;
    }

    private static boolean isPreferredTarget(Slot slot, ItemStack stack) {
        int index = playerSlotIndex(slot);
        return index >= 0 && isPreferredTarget(
            SlotLockManager.getInstance()
                .getState(index),
            stack);
    }

    private static boolean isPreferredTarget(SlotLockState state, ItemStack stack) {
        return state.getType() == SlotLockState.LockType.TYPE && state.accepts(stack);
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
