package com.KKKXia.NHToolbox.inventory;

import java.util.List;

import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import com.KKKXia.NHToolbox.NHToolbox;

import invtweaks.InvTweaksContainerManager;
import invtweaks.InvTweaksContainerSectionManager;
import invtweaks.api.container.ContainerSection;

/**
 * InventoryTweaks 的锁定规则，管两件事：<b>快捷移动的目标格</b>与<b>整理时不碰锁定栏位</b>。
 *
 * <h3>一、快捷移动（空格 / ctrl 等 + 左键）</h3>
 *
 * 本包默认配置里 {@code shortcutKeyEverything=SPACE}（空格 + 左键把整片区域搬空）、
 * {@code shortcutKeyAllItems=LCONTROL+LSHIFT}（搬同种物品）、{@code shortcutKeyOneItem=LCONTROL}（搬一个）。
 * 这些移动全部由 {@code InvTweaksHandlerShortcuts} 自己决定目标格
 * （{@code getNextTargetIndex}：先找能并进去的同类堆叠、再找第一个空格），既不经过
 * {@code Container.mergeItemStack}，也不认识我们的锁定，所以症状是"快捷搬过来的物品不会进锁定格"。
 *
 * <p>
 * 这里挂在 {@link InvTweaksContainerManager#move} / {@code moveSome} 的入口上，把 IT 挑好的目标格换成
 * "该物品的优先锁定格"（同种且未堆满的锁定格 &gt; 空的锁定格），换完之后 IT 仍然按自己的流程点击，
 * 客户端与服务端的容器状态保持一致。
 * <b>只在快捷移动期间改</b>（{@code handleShortcut} 执行期间）：整理、自动补货、拾取归位同样调用
 * {@code move}，但它们按下标刻意摆放每个格子，改目标会破坏结果。
 *
 * <h3>二、整理（InventoryTweaks 的排序按钮 / 快捷键）</h3>
 *
 * IT 的整理算法自己带着一套"锁定栏位"概念（{@code InvTweaksHandlerSorting.lockPriorities}：
 * 优先级大于 0 的栏位不会被搬空，也不会被塞入别的物品，而"同种物品并进锁定格"是它本来就有的行为）。
 * 但那个数组只对玩家背包区段从配置里取值，<b>箱子 / 背包格区段永远是全 0</b>，于是整理会把物品往我们
 * 锁定的背包格里塞——放置被 {@code SlotLockClickGuard} 拦下后物品卡在光标上，IT 的补救逻辑
 * （{@code InvTweaksContainerManager.move} 末尾的"找个空位放下"）就把它丢进玩家背包，
 * 表现为"整理后随机物品跑进玩家物品栏"。
 *
 * <p>
 * 因此 {@link #applyLocksToSorting} 在每次整理开始时把我们的锁定栏位以最高优先级叠加进去，
 * 整理从此绕开这些格子：不搬空、不塞入，同种物品仍可并入。长度按区段大小重建，
 * 顺带避开 IT 用 999 长的默认数组遍历时越界取槽的隐患。
 *
 * <p>
 * 仅客户端：本类引用的是 IT 的客户端容器管理器，只由 client 列表里的 mixin 调用。
 */
public final class SlotLockInvTweaksRules {

    /** 叠加给 IT 整理的锁定优先级：高于所有规则（规则的优先级是 1000000 量级），即"任何规则都动不了"。 */
    private static final int LOCKED_FOR_SORTING = Integer.MAX_VALUE;

    /** 是否正处于 InvTweaks 快捷移动（{@code handleShortcut}）的执行过程中。客户端单线程访问。 */
    private static boolean shortcutActive;

    /** 只打一次日志，便于确认这条路确实被接管。 */
    private static boolean loggedActivation;

    /** 整理路径的一次性日志标记。 */
    private static boolean loggedSortActivation;

    private SlotLockInvTweaksRules() {}

    /** 进入快捷移动：{@code MixinInvTweaksShortcutsLock} 在 HEAD 调用。 */
    public static void beginShortcut() {
        shortcutActive = true;
    }

    /** 离开快捷移动：{@code MixinInvTweaksShortcutsLock} 在 RETURN 调用。 */
    public static void endShortcut() {
        shortcutActive = false;
    }

    /**
     * 计算本次移动真正应该使用的目标下标。
     *
     * @param preferLockedTarget 是否允许把目标改成"优先锁定格"（只有快捷移动才允许；整理必须保持 IT 的排列）
     * @return 优先锁定格在目标区段里的下标；没有更合适的格子时原样返回 {@code destIndex}
     */
    public static int preferredDestination(InvTweaksContainerManager manager, ContainerSection srcSection, int srcIndex,
        ContainerSection destSection, int destIndex, boolean preferLockedTarget) {
        if (manager == null || srcSection == null
            || destSection == null
            || destIndex == InvTweaksContainerManager.DROP_SLOT) {
            return destIndex; // 丢弃：不介入
        }
        if (!SlotLockManager.getInstance()
            .hasAnyLock()) {
            return destIndex;
        }
        List<Slot> destSlots = manager.getSlots(destSection);
        if (destSlots == null || destSlots.isEmpty()) {
            return destIndex;
        }
        Slot sourceSlot = manager.getSlot(srcSection, srcIndex);
        if (sourceSlot == null) {
            return destIndex;
        }
        ItemStack source = sourceSlot.getStack();
        if (source == null || source.stackSize <= 0) {
            return destIndex;
        }

        // 目标区段属于哪个锁定空间（玩家背包 / 背包模组的背包）；一段都没有可锁定栏位就不介入
        boolean playerSpace = false;
        boolean backpackSpace = false;
        for (Slot slot : destSlots) {
            int key = SlotLockMergeRules.lockKey(slot);
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
            return destIndex;
        }

        // (1) 优先：匹配的类型锁定格——先补现有堆叠，再放空格（仅快捷移动）
        if (preferLockedTarget && shortcutActive) {
            for (int pass = 0; pass < 2; pass++) {
                for (int i = 0; i < destSlots.size(); i++) {
                    Slot slot = destSlots.get(i);
                    if (slot == sourceSlot) {
                        continue;
                    }
                    int key = SlotLockMergeRules.lockKey(slot);
                    if (key < 0 || !(key >= SlotLockManager.BACKPACK_BASE ? backpackSpace : playerSpace)
                        || !SlotLockMergeRules.prefersItem(key, source)
                        || !slot.isItemValid(source)) {
                        continue;
                    }
                    ItemStack existing = slot.getStack();
                    if (pass == 0) {
                        if (existing != null
                            && existing.stackSize < Math.min(existing.getMaxStackSize(), source.getMaxStackSize())
                            && SlotLockMergeRules.isSameStack(existing, source)) {
                            return i;
                        }
                    } else if (existing == null) {
                        return i;
                    }
                }
            }
        }

        // (2) 兜底：IT 挑中的格子被锁定拒绝时换一个能放的空格。
        // 否则我们自己的 Container.slotClick 守卫会拦下这次放置，物品卡在光标上，
        // IT 的补救逻辑就会把它丢进玩家背包——正是"整理后物品乱跑"的来源之一。
        Slot destSlot = destIndex >= 0 && destIndex < destSlots.size() ? destSlots.get(destIndex) : null;
        if (destSlot != null && isRejectedByLock(destSlot, source)) {
            for (int i = 0; i < destSlots.size(); i++) {
                Slot slot = destSlots.get(i);
                if (slot == sourceSlot || slot.getStack() != null
                    || isRejectedByLock(slot, source)
                    || !slot.isItemValid(source)) {
                    continue;
                }
                return i;
            }
        }
        return destIndex;
    }

    /**
     * 把我们的锁定栏位叠加到 IT 整理的 {@code lockPriorities} 上。
     *
     * <p>
     * 返回新数组而不是就地改：IT 对箱子 / 背包格区段用的是静态共享的默认数组，
     * 就地改会污染其它区段与后续整理。
     *
     * @return 叠加后的新数组；当前区段没有锁定栏位时返回原数组（零分配）
     */
    public static int[] applyLocksToSorting(InvTweaksContainerSectionManager sectionManager, int[] lockPriorities,
        int size) {
        if (sectionManager == null || size <= 0
            || !SlotLockManager.getInstance()
                .hasAnyLock()) {
            return lockPriorities;
        }
        List<Slot> slots = sectionManager.getSlots();
        if (slots == null || slots.isEmpty()) {
            return lockPriorities;
        }

        int count = Math.min(size, slots.size());
        int[] merged = null;
        for (int i = 0; i < count; i++) {
            int key = SlotLockMergeRules.lockKey(slots.get(i));
            if (key < 0 || !SlotLockManager.getInstance()
                .isLocked(key)) {
                continue;
            }
            if (merged == null) {
                // 只复制一次，并保留 IT 自己的锁定优先级（玩家背包区段来自规则文件的 locked 规则）
                merged = new int[count];
                if (lockPriorities != null) {
                    System.arraycopy(lockPriorities, 0, merged, 0, Math.min(count, lockPriorities.length));
                }
                logSortActivation();
            }
            merged[i] = LOCKED_FOR_SORTING;
        }
        return merged == null ? lockPriorities : merged;
    }

    /** 该栏位是否被锁定拒绝放这个物品。 */
    private static boolean isRejectedByLock(Slot slot, ItemStack stack) {
        int key = SlotLockMergeRules.lockKey(slot);
        return key >= 0 && !SlotLockManager.getInstance()
            .canAccept(key, stack);
    }

    /** 首次真正改变快捷移动的目标格时打一条日志。 */
    public static void logActivation() {
        if (!loggedActivation) {
            loggedActivation = true;
            NHToolbox.LOG.info("[SlotLock] InventoryTweaks 快捷移动（空格/ctrl 等 + 左键）已接入锁定规则");
        }
    }

    /** 首次让整理绕开锁定栏位时打一条日志。 */
    private static void logSortActivation() {
        if (!loggedSortActivation) {
            loggedSortActivation = true;
            NHToolbox.LOG.info("[SlotLock] InventoryTweaks 整理已接入锁定规则：锁定栏位不参与整理");
        }
    }
}
