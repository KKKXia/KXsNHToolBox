package com.KKKXia.NHToolbox.inventory;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import com.KKKXia.NHToolbox.NHToolbox;

import de.eydamos.backpack.inventory.slot.SlotBackpack;

/**
 * 快捷移动（shift+左键）与合并的锁定规则：不把物品放进"拒绝该物品的锁定栏位"，
 * 并且优先放进"匹配的类型锁定格"。
 *
 * <p>
 * 两个锁定空间都支持：玩家背包栏位（0-35）与背包模组的背包格（打开背包后看到的那一片）。
 * 判定完全按槽位本身做（{@link #lockKey}），因此与方向无关、与具体容器无关：
 * 背包内"快捷栏 ↔ 主背包"、箱子 / AE 终端 / 熔炉"容器 ↔ 玩家背包"、
 * 背包界面里"玩家背包 ↔ 模组背包"、"模组背包 → 快捷栏"都走同一套规则；
 * 范围里没有锁定栏位时，优先趟只做几十次极廉价的 instanceof 判定后空手而归。
 *
 * <p>
 * 本类**会同时运行在客户端容器与服务端容器上**（1.7.10 的
 * {@code PlayerControllerMP.windowClick:475-479} 客户端也会执行一次 {@code slotClick}），
 * 因此这里绝不能引用客户端专属类（Minecraft / GuiScreen / GL 等）。
 *
 * <p>
 * 性能：锁定的登记表是纯数组，判定只做几次整数比较与虚调用；没有任何锁定时会话
 * （{@link #needsInterception()}）直接返回 false，原版实现原样执行，零额外开销。
 */
public final class SlotLockMergeRules {

    private SlotLockMergeRules() {}

    /** 是否已经打过"mixin 已生效"的一次性日志。 */
    private static boolean loggedActivation;

    /** 背包模组路径的一次性激活日志标记。 */
    private static boolean loggedBackpackActivation;

    /** 背包模组界面首次经过锁定规则时打一条日志，便于确认这条路确实被接管。 */
    public static void logBackpackActivation() {
        if (!loggedBackpackActivation) {
            loggedBackpackActivation = true;
            NHToolbox.LOG.info("[SlotLock] 背包模组（Minecraft-Backpack-Mod）界面已接入锁定规则");
        }
    }

    /** 是否有任何栏位处于锁定状态；没有则本 mixin 完全不介入。 */
    public static boolean needsInterception() {
        boolean anyLock = SlotLockManager.getInstance()
            .hasAnyLock();
        if (anyLock && !loggedActivation) {
            loggedActivation = true;
            // 只打一次：证明 mergeItemStack 的 mixin 确实被应用（否则快捷移动不会经过这里）
            NHToolbox.LOG.info("[SlotLock] 快捷移动已接入栏位锁定规则");
        }
        return anyLock;
    }

    /** 原版语义的合并（不检查 {@code slot.isItemValid}）。 */
    public static boolean merge(List<Slot> slots, ItemStack stack, int startIndex, int endIndex, boolean reverseOrder) {
        return merge(slots, stack, startIndex, endIndex, reverseOrder, false);
    }

    /**
     * 供 mixin 调用的入口：直接接收容器，槽位表在这里取。
     *
     * <p>
     * 为什么不让 mixin 用 {@code @Shadow} 访问 {@code inventorySlots}：该字段声明在
     * {@code Container} 上，注解处理器无法在子类里解析它（会警告
     * "Cannot find target for @Shadow field" 且不生成 SRG 映射），生产环境的混淆名会让注入失败。
     * 放在普通类里访问则由构建期 reobf 自动处理。
     */
    public static boolean merge(Container container, ItemStack stack, int startIndex, int endIndex,
        boolean reverseOrder, boolean checkIsItemValid) {
        return merge(container.inventorySlots, stack, startIndex, endIndex, reverseOrder, checkIsItemValid);
    }

    /**
     * 与 {@code Container.mergeItemStack} 语义一致的合并实现，额外遵守栏位锁定。
     *
     * <p>
     * 与原版的差异只有三点：合并时跳过"锁定且拒绝该物品"的栏位；先把物品送进范围内
     * "匹配的类型锁定格"；{@code checkIsItemValid} 为 true 时额外尊重槽位自身的接受判定
     * （背包模组的 {@code ContainerAdvanced.mergeItemStack} 就带这个检查，用于禁止背包套背包）。
     * 其余顺序、堆叠规则、通知调用（{@code onSlotChanged}）都与原版逐行对应。
     */
    public static boolean merge(List<Slot> slots, ItemStack stack, int startIndex, int endIndex, boolean reverseOrder,
        boolean checkIsItemValid) {
        if (stack == null || stack.stackSize <= 0 || startIndex >= endIndex) {
            return false;
        }
        boolean moved;

        // (1) 优先：并入匹配的类型锁定格（任何"往锁定栏位里放"的合并都适用）
        moved = mergeIntoMatchingLockedSlots(slots, stack, startIndex, endIndex, checkIsItemValid);
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
                // existing != stack：某些容器的合并区间会包含源栏位本身，自我合并会让数量翻倍
                if (existing != null && existing != stack
                    && existing.getItem() == stack.getItem()
                    && (!stack.getHasSubtypes() || stack.getItemDamage() == existing.getItemDamage())
                    && ItemStack.areItemStackTagsEqual(stack, existing)
                    && !isBlocked(slot, stack)
                    && (!checkIsItemValid || slot.isItemValid(stack))) {
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
                if (slot.getStack() == null && !isBlocked(slot, stack)
                    && (!checkIsItemValid || slot.isItemValid(stack))) {
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
     * 第一优先：本次合并的<b>目标锁定空间</b>里"类型锁定且接受该物品"的栏位
     * （先补现有堆叠、再放空槽）。
     *
     * <p>
     * 为什么按"空间"而不是按传入区间搜索：背包模组把"从背包取到玩家背包"拆成两次调用——
     * 先 {@code mergeItemStack(stack, 快捷栏区间, true)}，失败才 {@code mergeItemStack(stack, 主物品栏区间, false)}。
     * 如果只在传入区间里找锁定格，主物品栏里的锁定格永远抢不过"还有空位的快捷栏"，
     * 于是物品不会进锁定格。这里先判定本次合并的目标空间（区间里出现玩家栏位就是玩家空间，
     * 出现背包格就是背包空间），再在整个容器的该空间里找匹配的锁定格；
     * 多个匹配时按容器的槽位顺序取第一个。
     *
     * <p>
     * 区间里没有可锁定栏位时（例如"玩家 -> 箱子"，目标是箱子格）直接返回，不做任何优先处理。
     */
    private static boolean mergeIntoMatchingLockedSlots(List<Slot> slots, ItemStack stack, int startIndex, int endIndex,
        boolean checkIsItemValid) {
        boolean playerSpace = false;
        boolean backpackSpace = false;
        for (int k = startIndex; k < endIndex && !(playerSpace && backpackSpace); k++) {
            int key = lockKey(slots.get(k));
            if (key < 0) {
                continue;
            }
            if (key >= SlotLockManager.BACKPACK_BASE) {
                backpackSpace = true;
            } else {
                playerSpace = true;
            }
        }
        if (!playerSpace && !backpackSpace) {
            return false;
        }

        SlotLockManager manager = SlotLockManager.getInstance();
        boolean moved = false;
        for (int pass = 0; pass < 2 && stack.stackSize > 0; pass++) {
            for (int k = 0; k < slots.size() && stack.stackSize > 0; k++) {
                Slot slot = slots.get(k);
                if (checkIsItemValid && !slot.isItemValid(stack)) {
                    continue;
                }
                int key = lockKey(slot);
                if (key < 0 || (key >= SlotLockManager.BACKPACK_BASE ? !backpackSpace : !playerSpace)) {
                    continue; // 不属于本次合并的目标空间
                }
                if (!isPreferredTarget(manager.getState(key), stack)) {
                    continue;
                }
                ItemStack existing = slot.getStack();
                if (existing == stack) {
                    continue; // 源栏位自己：并进去会让数量翻倍
                }
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

    /** 该栏位是否因锁定而拒绝此物品（未锁定的栏位一律不拦）。 */
    public static boolean isBlocked(Slot slot, ItemStack stack) {
        int key = lockKey(slot);
        return key >= 0 && !SlotLockManager.getInstance()
            .canAccept(key, stack);
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
     * {@code getValidDestinationSlots}，所以在这里过滤/重排就等于同时满足两条规则。
     *
     * <p>
     * 目标列表里没有锁定栏位时原样返回，零分配。
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
            int key = lockKey(slot);
            if (key < 0) {
                continue;
            }
            SlotLockState state = manager.getState(key);
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
        int key = lockKey(slot);
        return key >= 0 && isPreferredTarget(
            SlotLockManager.getInstance()
                .getState(key),
            stack);
    }

    private static boolean isPreferredTarget(SlotLockState state, ItemStack stack) {
        return state.getType() == SlotLockState.LockType.TYPE && state.accepts(stack);
    }

    /**
     * 解析槽位对应的锁定键，不属于任何可锁定栏位时返回 -1。
     *
     * <ul>
     * <li>背包模组的背包格（{@code SlotBackpack}）-> {@link SlotLockManager#backpackKey}，
     * 下标即背包内容索引（两种背包 GUI 工厂都是这么建的）</li>
     * <li>玩家背包栏位（{@link InventoryPlayer}）-> 0-35；
     * 护甲格(36-39)与合成格被范围检查排除，创造模式的 {@code CreativeSlot} 包装槽用
     * 原版 API {@code isSlotInInventory} 反查真实下标</li>
     * </ul>
     *
     * <p>
     * 不依赖 {@code Minecraft.thePlayer}，服务端线程也能用（服务端侧容器同样会执行这部分逻辑）。
     */
    public static int lockKey(Slot slot) {
        if (slot instanceof SlotBackpack) {
            int index = slot.getSlotIndex();
            return index >= 0 && index < SlotLockManager.BACKPACK_SLOT_COUNT ? SlotLockManager.backpackKey(index) : -1;
        }
        IInventory inventory = slot.inventory;
        if (!(inventory instanceof InventoryPlayer)) {
            return -1;
        }
        int index = slot.getSlotIndex();
        if (index >= 0 && index < SlotLockManager.SLOT_COUNT && slot.isSlotInInventory(inventory, index)) {
            return SlotLockManager.playerKey(index);
        }
        for (int i = 0; i < SlotLockManager.SLOT_COUNT; i++) {
            if (slot.isSlotInInventory(inventory, i)) {
                return SlotLockManager.playerKey(i);
            }
        }
        return -1;
    }
}
