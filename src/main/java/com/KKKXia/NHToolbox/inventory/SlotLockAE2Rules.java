package com.KKKXia.NHToolbox.inventory;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import com.KKKXia.NHToolbox.NHToolbox;

import appeng.api.config.InsertionMode;
import appeng.util.Platform;

/**
 * AE2 专用的插入规则。
 *
 * <p>
 * 为什么需要它：ME 终端"shift+左键取出"走的是
 * {@code ContainerMEMonitorable.doAction(SHIFT_CLICK)} -> {@code InventoryAdaptor}，
 * 完全绕开容器与 {@code Container.mergeItemStack}（也绕开
 * {@code AEBaseContainer.transferStackInSlot}），所以通用规则对它无效。而且它先
 * {@code simulateAdd} 算出能放下多少、再从网络抽取、最后才真正 {@code addItems}，
 * 因此模拟与真插必须用完全相同的一套逻辑，否则会出现"抽出来了却放不下"的物品损失。
 * 本方法就是 {@code AdaptorIInventory#addItems(ItemStack, boolean, InsertionMode)} 的等价实现，
 * 额外加入两条锁定规则。
 *
 * <p>
 * {@code AdaptorPlayerInventory} 在整个 AE2 里只有 {@code InventoryAdaptor:212} 一处构造
 * （{@code new AdaptorPlayerInventory(player.inventory, false)}），槽位下标与
 * {@code InventoryPlayer} 的 0-35 一一对应，因此这里可以直接用下标查锁定状态。
 */
public final class SlotLockAE2Rules {

    /** 只打一次日志，用来证明 AE2 这条插入链路确实被接管（与通用 mergeItemStack 的日志区分开）。 */
    private static boolean loggedActivation;

    private SlotLockAE2Rules() {}

    /**
     * 与 AE2 {@code AdaptorIInventory#addItems(ItemStack, boolean, InsertionMode)} 语义一致，
     * 但会跳过"锁定且拒绝该物品"的栏位，并优先使用匹配的类型锁定格。
     *
     * @param modulate      false 表示只模拟、不改动库存（结果必须与真插一致）
     * @param insertionMode 与原版一致：非 DEFAULT 时先走一趟空格，ONLY_EMPTY 时到此为止
     * @return 放不下的剩余；null 表示全部放下
     */
    public static ItemStack addItems(IInventory inventory, ItemStack itemsToAdd, boolean modulate,
        InsertionMode insertionMode) {
        if (!loggedActivation) {
            loggedActivation = true;
            NHToolbox.LOG.info("[SlotLock] AE2 取物插入已接入锁定规则（ME 终端 / 合成终端等）");
        }
        if (itemsToAdd == null || itemsToAdd.stackSize <= 0) {
            return null;
        }
        final ItemStack left = itemsToAdd.copy();
        final int size = inventory.getSizeInventory();
        final int limit = Math.min(inventory.getInventoryStackLimit(), itemsToAdd.getMaxStackSize());

        // (0) 优先：匹配的类型锁定格（先补同类堆叠、再放空格）
        for (int pass = 0; pass < 2 && left.stackSize > 0; pass++) {
            for (int slot = 0; slot < size && left.stackSize > 0; slot++) {
                if (!isPreferredLockedSlot(slot, left)) {
                    continue;
                }
                final ItemStack existing = inventory.getStackInSlot(slot);
                if (existing == null) {
                    if (pass == 1) {
                        insertNew(inventory, slot, left, limit, modulate);
                    }
                } else if (pass == 0 && existing.stackSize < limit && Platform.isSameItemPrecise(existing, left)) {
                    mergeInto(inventory, slot, existing, left, limit, modulate);
                }
            }
        }

        // (1) 与原版一致：PREFER_EMPTY / ONLY_EMPTY 先走一趟空格
        if (insertionMode != InsertionMode.DEFAULT) {
            for (int slot = 0; slot < size && left.stackSize > 0; slot++) {
                if (!accepts(slot, left) || inventory.getStackInSlot(slot) != null) {
                    continue;
                }
                insertNew(inventory, slot, left, limit, modulate);
            }
        }
        if (insertionMode == InsertionMode.ONLY_EMPTY) {
            return left.stackSize <= 0 ? null : left;
        }

        // (2) 与原版一致：索引顺序主趟——空格放入 / 同类堆叠补齐
        for (int slot = 0; slot < size && left.stackSize > 0; slot++) {
            if (!accepts(slot, left)) {
                continue;
            }
            final ItemStack existing = inventory.getStackInSlot(slot);
            if (existing == null) {
                insertNew(inventory, slot, left, limit, modulate);
            } else if (existing.stackSize < limit && Platform.isSameItemPrecise(existing, left)) {
                mergeInto(inventory, slot, existing, left, limit, modulate);
            }
        }
        return left.stackSize <= 0 ? null : left;
    }

    private static void insertNew(IInventory inventory, int slot, ItemStack left, int limit, boolean modulate) {
        final int moved = Math.min(limit, left.stackSize);
        if (modulate) {
            final ItemStack placed = left.copy();
            placed.stackSize = moved;
            inventory.setInventorySlotContents(slot, placed);
            inventory.markDirty();
        }
        left.stackSize -= moved;
    }

    private static void mergeInto(IInventory inventory, int slot, ItemStack existing, ItemStack left, int limit,
        boolean modulate) {
        final int used = Math.min(left.stackSize, limit - existing.stackSize);
        if (modulate) {
            existing.stackSize += used;
            inventory.setInventorySlotContents(slot, existing);
            inventory.markDirty();
        }
        left.stackSize -= used;
    }

    /** 该下标是否接受此物品（锁定且拒绝 → false）。 */
    private static boolean accepts(int slot, ItemStack stack) {
        return SlotLockManager.getInstance()
            .canAccept(slot, stack);
    }

    /** 该下标是否为"锁定类型与之匹配"的类型锁定格。 */
    private static boolean isPreferredLockedSlot(int slot, ItemStack stack) {
        final SlotLockState state = SlotLockManager.getInstance()
            .getState(slot);
        return state.getType() == SlotLockState.LockType.TYPE && state.accepts(stack);
    }
}
