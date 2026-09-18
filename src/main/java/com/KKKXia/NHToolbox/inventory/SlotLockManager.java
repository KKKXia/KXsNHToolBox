package com.KKKXia.NHToolbox.inventory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

import com.KKKXia.NHToolbox.NHToolbox;

/**
 * 栏位锁定状态的管理单例，纯客户端，支持两个"锁定空间"：
 *
 * <ul>
 * <li><b>玩家背包</b>：键 0-35（0-8 快捷栏、9-35 主物品栏）</li>
 * <li><b>背包模组的背包</b>：键 1000-1127，对应打开背包后看到的背包格 0-N
 * （Minecraft-Backpack-Mod 的容量可配置，最大 128 格）</li>
 * </ul>
 *
 * <p>
 * 锁定按<b>栏位下标</b>记录，不区分具体是哪一件背包（与玩家栏位的设计一致）：
 * 换一个同规格的背包打开，锁定的仍是同样的第 n 格。
 *
 * <p>
 * 状态持久化到 {@code config/NHToolbox/slotLocks.dat}（NBT 压缩格式）：
 * 每次切换后立即保存，进入世界后加载；文件缺失或损坏时重置为全部未锁定。
 * 玩家栏位沿用旧键名 {@code slotN}，背包栏位用 {@code backpackN}，旧存档可直接读取；
 * 旧档里已废弃的"空锁定"记录会在加载时被清理成未锁定。
 */
public final class SlotLockManager {

    /** 玩家背包栏位数量：0-8 快捷栏 + 9-35 主物品栏。 */
    public static final int SLOT_COUNT = 36;

    /** 背包模组单个背包的最大栏位数（ConfigurationBackpack 允许 1-128）。 */
    public static final int BACKPACK_SLOT_COUNT = 128;

    /** 背包栏位键的起始值，与玩家栏位键（0-35）分开，避免混淆。 */
    public static final int BACKPACK_BASE = 1000;

    private static final String FILE_NAME = "slotLocks.dat";

    private static final SlotLockManager INSTANCE = new SlotLockManager();

    private final SlotLockState[] playerStates = new SlotLockState[SLOT_COUNT];
    private final SlotLockState[] backpackStates = new SlotLockState[BACKPACK_SLOT_COUNT];

    /** 已锁定栏位数量（两个空间合计），供 mixin 做 O(1) 早退（0 表示功能未被使用）。 */
    private int lockedCount;

    private SlotLockManager() {
        clearAll();
    }

    public static SlotLockManager getInstance() {
        return INSTANCE;
    }

    /** 玩家背包栏位下标 -> 锁定键。 */
    public static int playerKey(int index) {
        return index;
    }

    /** 背包（模组）栏位下标 -> 锁定键。 */
    public static int backpackKey(int index) {
        return BACKPACK_BASE + index;
    }

    /** 是否存在任何锁定栏位；false 时所有锁定相关逻辑都不介入。 */
    public boolean hasAnyLock() {
        return lockedCount > 0;
    }

    public boolean isLocked(int key) {
        return state(key).isLocked();
    }

    public SlotLockState getState(int key) {
        return state(key);
    }

    /** 该栏位是否接受此物品（键非法或未锁定一律接受）。 */
    public boolean canAccept(int key, ItemStack stack) {
        return state(key).accepts(stack);
    }

    /**
     * 切换锁定状态：未锁定 -> 类型锁定（记住栏位里那件物品的种类）；已锁定 -> 解锁。
     * 切换后立即落盘。
     *
     * <p>
     * 空栏位无法锁定：类型锁定必须有一件实物来确定"只收哪一种"（早先的"空锁定"状态已移除），
     * 因此空栏位按下锁定键视为无事发生，由调用方决定如何提示。
     *
     * @return 状态是否真的发生了变化；false 表示键非法或栏位为空且未锁定
     */
    public boolean toggle(int key, ItemStack currentStack) {
        SlotLockState[] states = statesFor(key);
        if (states == null) {
            return false;
        }
        int index = indexIn(key);
        if (states[index].isLocked()) {
            states[index] = SlotLockState.NONE;
        } else {
            if (currentStack == null) {
                return false; // 空栏位：没有种类可记，什么也不锁
            }
            states[index] = SlotLockState.ofType(currentStack);
        }
        recount();
        save();
        return true;
    }

    public void load() {
        File file = lockFile();
        if (!file.isFile()) {
            clearAll();
            return;
        }
        int deprecated = 0;
        // 流必须关闭：每次切换都会走一次 save()，句柄泄漏会在长时间游戏后累积
        try (FileInputStream in = new FileInputStream(file)) {
            NBTTagCompound root = CompressedStreamTools.readCompressed(in);
            deprecated = loadSpace(root, "slot", playerStates);
            deprecated += loadSpace(root, "backpack", backpackStates);
            recount();
        } catch (Exception e) {
            clearAll();
            NHToolbox.LOG.warn("读取栏位锁定状态失败，已重置为全部未锁定", e);
            return;
        }
        // 旧档里的"空锁定"记录已无对应状态（见 SlotLockState.DEPRECATED_EMPTY_ID）：
        // 读进来只会是未锁定，这里回写一次把死数据清掉（流的关闭放在前面，避免读写句柄叠在一起）
        if (deprecated > 0) {
            NHToolbox.LOG.info("[SlotLock] 忽略并清理了 {} 条旧版空锁定记录（该状态已移除），对应栏位视为未锁定", deprecated);
            save();
        }
    }

    /** 从 {@code root} 里按 {@code prefix+下标} 读一片锁定空间，返回其中已废弃的空锁定记录数。 */
    private static int loadSpace(NBTTagCompound root, String prefix, SlotLockState[] target) {
        int deprecated = 0;
        for (int i = 0; i < target.length; i++) {
            NBTTagCompound tag = root.getCompoundTag(prefix + i);
            if (SlotLockState.isDeprecatedEntry(tag)) {
                deprecated++;
            }
            target[i] = SlotLockState.fromNBT(tag);
        }
        return deprecated;
    }

    public void save() {
        NBTTagCompound root = new NBTTagCompound();
        for (int i = 0; i < SLOT_COUNT; i++) {
            root.setTag("slot" + i, playerStates[i].toNBT());
        }
        for (int i = 0; i < BACKPACK_SLOT_COUNT; i++) {
            root.setTag("backpack" + i, backpackStates[i].toNBT());
        }
        try (FileOutputStream out = new FileOutputStream(lockFile())) {
            CompressedStreamTools.writeCompressed(root, out);
        } catch (Exception e) {
            NHToolbox.LOG.warn("保存栏位锁定状态失败", e);
        }
    }

    private void clearAll() {
        for (int i = 0; i < SLOT_COUNT; i++) {
            playerStates[i] = SlotLockState.NONE;
        }
        for (int i = 0; i < BACKPACK_SLOT_COUNT; i++) {
            backpackStates[i] = SlotLockState.NONE;
        }
        recount();
    }

    private void recount() {
        int count = 0;
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (playerStates[i].isLocked()) {
                count++;
            }
        }
        for (int i = 0; i < BACKPACK_SLOT_COUNT; i++) {
            if (backpackStates[i].isLocked()) {
                count++;
            }
        }
        lockedCount = count;
    }

    /** 取出键对应的状态；键非法时返回 NONE（即"未锁定、接受一切"）。 */
    private SlotLockState state(int key) {
        SlotLockState[] states = statesFor(key);
        return states == null ? SlotLockState.NONE : states[indexIn(key)];
    }

    private static SlotLockState[] statesFor(int key) {
        SlotLockManager manager = INSTANCE;
        if (key >= 0 && key < SLOT_COUNT) {
            return manager.playerStates;
        }
        if (key >= BACKPACK_BASE && key < BACKPACK_BASE + BACKPACK_SLOT_COUNT) {
            return manager.backpackStates;
        }
        return null;
    }

    private static int indexIn(int key) {
        return key >= BACKPACK_BASE ? key - BACKPACK_BASE : key;
    }

    private static File lockFile() {
        File configDir = new File(Minecraft.getMinecraft().mcDataDir, "config/NHToolbox");
        configDir.mkdirs();
        return new File(configDir, FILE_NAME);
    }
}
