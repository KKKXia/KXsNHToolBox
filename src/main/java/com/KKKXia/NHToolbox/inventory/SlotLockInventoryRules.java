package com.KKKXia.NHToolbox.inventory;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

import com.KKKXia.NHToolbox.NHToolbox;

/**
 * "物品进入玩家背包"这条链路的锁定规则。
 *
 * <p>
 * 覆盖所有直接调用 {@link InventoryPlayer#addItemStackToInventory(ItemStack)} 的路径——
 * 典型的就是<b>从世界里捡起物品</b>（{@code EntityItem.onCollideWithPlayer}），
 * 以及 {@code /give}、机器产物、合成剩余物等。原版这条链路走的是
 * {@code InventoryPlayer.storePartialItemStack}（先补同类堆叠、再找第一个空格，快捷栏优先），
 * 完全绕开容器与 {@code mergeItemStack}，所以锁定规则对它无效：
 * 症状就是"捡起来的物品不会进锁定格"。
 *
 * <p>
 * 这里做的是"预放置"：先把物品送进匹配的类型锁定格，剩下的再交回原版逻辑，
 * 因此不会改变"背包满了放不下"等既有语义。
 *
 * <p>
 * 本类会同时运行在客户端与服务端（拾取判定只在服务端跑），因此不引用客户端专属类。
 */
public final class SlotLockInventoryRules {

    /** 只打一次日志，便于确认这条路确实被接管。 */
    private static boolean loggedActivation;

    private SlotLockInventoryRules() {}

    /**
     * 把物品优先放进玩家背包里"匹配的类型锁定格"。
     *
     * <p>
     * 两种锁定格都算优先目标：已经装着同种物品、还没堆满的（先补进去），以及空的锁定格。
     * 有耐久的物品（{@code getMaxStackSize() == 1}）与 {@code addItemStackToInventory} 一致，
     * 整叠放进一个空格。
     *
     * @param inventory 玩家背包
     * @param stack     待放入的物品；被放入的部分会从 {@code stack.stackSize} 里扣掉
     * @return 是否已经把整叠放进锁定格（true 时调用方可以当作"已全部放入"直接返回）
     */
    public static boolean insertIntoLockedSlots(InventoryPlayer inventory, ItemStack stack) {
        if (inventory == null || stack == null || stack.stackSize <= 0) {
            return false;
        }
        SlotLockManager manager = SlotLockManager.getInstance();
        if (!manager.hasAnyLock()) {
            return false;
        }

        // 与 addItemStackToInventory 的耐久分支一致：这类物品不并堆叠，整叠占一个空格
        boolean wholeStack = stack.getMaxStackSize() <= 1;
        int limit = Math.min(inventory.getInventoryStackLimit(), stack.getMaxStackSize());

        for (int pass = 0; pass < 2 && stack.stackSize > 0; pass++) {
            for (int i = 0; i < SlotLockManager.SLOT_COUNT && stack.stackSize > 0; i++) {
                if (!SlotLockMergeRules.prefersItem(i, stack)) {
                    continue; // 不是"类型锁定且接受该物品"的栏位
                }
                ItemStack existing = inventory.getStackInSlot(i);
                if (wholeStack) {
                    if (existing == null) {
                        ItemStack placed = ItemStack.copyItemStack(stack);
                        inventory.setInventorySlotContents(i, placed);
                        stack.stackSize = 0;
                    }
                    continue;
                }
                if (pass == 0) {
                    // 第一趟：补进已有的同类堆叠
                    if (existing != null && existing.stackSize < limit
                        && SlotLockMergeRules.isSameStack(existing, stack)) {
                        int moved = Math.min(stack.stackSize, limit - existing.stackSize);
                        existing.stackSize += moved;
                        stack.stackSize -= moved;
                        inventory.setInventorySlotContents(i, existing);
                    }
                } else if (existing == null) {
                    // 第二趟：放进空的锁定格
                    int moved = Math.min(limit, stack.stackSize);
                    ItemStack placed = stack.copy();
                    placed.stackSize = moved;
                    inventory.setInventorySlotContents(i, placed);
                    stack.stackSize -= moved;
                }
            }
        }

        if (stack.stackSize <= 0) {
            logActivation();
            return true;
        }
        return false;
    }

    /** 首次真正往锁定格里放东西时打一条日志。 */
    private static void logActivation() {
        if (!loggedActivation) {
            loggedActivation = true;
            NHToolbox.LOG.info("[SlotLock] 拾取/入包已接入锁定规则：匹配物品优先进入锁定栏位");
        }
    }
}
