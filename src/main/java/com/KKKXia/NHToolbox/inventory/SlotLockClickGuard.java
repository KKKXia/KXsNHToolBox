package com.KKKXia.NHToolbox.inventory;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import com.KKKXia.NHToolbox.NHToolbox;

/**
 * {@code Container.slotClick} 层的锁定守卫：所有"程序化搬运"都从这里经过——
 * InventoryTweaks 整理、NEI 快速移动、其它 mod 直接构造点击，因此在这一层拦一次即可全覆盖。
 * 单机时集成服务端执行的是同一份代码，两端返回值一致，不会产生回滚或校正风暴。
 *
 * <p>
 * 只守"会往栏位里放东西"的操作：
 * <ul>
 * <li>{@code mode 0}（普通左/右键）且<b>光标上有物品</b>：放入、合并、交换都会把光标物品
 * 写进目标栏位。光标为空时只可能是取出/双击收集/丢弃，一律放行。</li>
 * <li>{@code mode 2}（数字键 1-9 交换）：两个方向都守——快捷栏那一格的物品会被放进目标栏位，
 * 目标栏位的物品也会被放进快捷栏那一格。</li>
 * </ul>
 *
 * <p>
 * 刻意不处理的几种 mode：
 * <ul>
 * <li>{@code mode 1}（快捷移动）：走 {@code transferStackInSlot -> mergeItemStack}，
 * 已由 {@link SlotLockMergeRules} 覆盖；</li>
 * <li>{@code mode 3}（选取方块）：只写光标，不写栏位；</li>
 * <li>{@code mode 4}（丢弃）与 {@code mode 6}（双击收集）：都是"取出"；</li>
 * <li>{@code mode 5}（拖拽分配）：在容器层与私有阶段状态（{@code field_94536_g} /
 * {@code field_94537_h}）耦合较深，HEAD 取消会打断阶段推进；玩家拖拽已由 GUI 层拦截，
 * 程序化拖拽极罕见，故此处不动。</li>
 * </ul>
 *
 * <p>
 * 本类会同时运行在客户端容器与服务端容器上，因此不引用任何客户端专属类。
 */
public final class SlotLockClickGuard {

    /** 只打一次日志，便于确认这条路确实被接管。 */
    private static boolean loggedActivation;

    private SlotLockClickGuard() {}

    /**
     * 这次 {@code slotClick} 是否应当被整次吞掉（等价于"什么都没发生"）。
     *
     * @param container     执行点击的容器
     * @param slotId        目标槽位号，-999 表示容器外
     * @param clickedButton 鼠标键 / 快捷栏下标 / 拖拽阶段（随 mode 而定）
     * @param mode          点击类型
     * @param player        玩家
     */
    public static boolean shouldBlock(Container container, int slotId, int clickedButton, int mode,
        EntityPlayer player) {
        if (player == null || player.inventory == null || container == null) {
            return false;
        }
        InventoryPlayer inventory = player.inventory;

        if (mode == 2) {
            return blocksHotbarSwap(container, slotId, clickedButton, inventory);
        }
        if (mode != 0) {
            return false;
        }
        ItemStack cursor = inventory.getItemStack();
        if (cursor == null) {
            return false; // 光标为空：不会往栏位里放东西
        }
        Slot slot = slotAt(container, slotId);
        if (slot == null) {
            return false;
        }
        int key = SlotLockMergeRules.lockKey(slot);
        return key >= 0 && !SlotLockManager.getInstance()
            .canAccept(key, cursor);
    }

    /** 数字键交换：目标栏位拒绝快捷栏物品，或锁定快捷栏格拒绝目标栏位物品，都要拦。 */
    private static boolean blocksHotbarSwap(Container container, int slotId, int clickedButton,
        InventoryPlayer inventory) {
        if (clickedButton < 0 || clickedButton > 8) {
            return false;
        }
        Slot slot = slotAt(container, slotId);
        if (slot == null) {
            return false;
        }
        SlotLockManager manager = SlotLockManager.getInstance();
        int key = SlotLockMergeRules.lockKey(slot);
        if (key >= 0) {
            ItemStack hotbarStack = inventory.getStackInSlot(clickedButton);
            if (hotbarStack != null && !manager.canAccept(key, hotbarStack)) {
                return true;
            }
        }
        ItemStack slotStack = slot.getStack();
        return slotStack != null && !manager.canAccept(SlotLockManager.playerKey(clickedButton), slotStack);
    }

    private static Slot slotAt(Container container, int slotId) {
        if (slotId < 0 || slotId >= container.inventorySlots.size()) {
            return null;
        }
        return container.inventorySlots.get(slotId);
    }

    /** slotClick 层首次拦下点击时打一条日志，便于确认这条路确实被接管。 */
    public static void logActivation() {
        if (!loggedActivation) {
            loggedActivation = true;
            NHToolbox.LOG.info("[SlotLock] 已接管 Container.slotClick：程序化搬运（整理 / 快速移动等）同样遵守锁定");
        }
    }
}
