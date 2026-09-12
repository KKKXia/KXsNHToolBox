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
 * 玩家背包 36 格（0-8 快捷栏、9-35 主物品栏）锁定状态的管理单例，纯客户端。
 *
 * <p>
 * 状态持久化到 {@code config/NHToolbox/slotLocks.dat}（NBT 压缩格式）：
 * 每次切换后立即保存，进入世界后加载；文件缺失或损坏时重置为全部未锁定。
 */
public final class SlotLockManager {

    /** 背包栏位数量：0-8 快捷栏 + 9-35 主物品栏。 */
    public static final int SLOT_COUNT = 36;

    private static final String FILE_NAME = "slotLocks.dat";

    private static final SlotLockManager INSTANCE = new SlotLockManager();

    private final SlotLockState[] states = new SlotLockState[SLOT_COUNT];

    /** 已锁定栏位数量，供 {@code mergeItemStack} 的 mixin 做 O(1) 早退（0 表示功能未被使用）。 */
    private int lockedCount;

    private SlotLockManager() {
        clearAll();
    }

    public static SlotLockManager getInstance() {
        return INSTANCE;
    }

    /** 是否存在任何锁定栏位；false 时所有锁定相关逻辑都不介入。 */
    public boolean hasAnyLock() {
        return lockedCount > 0;
    }

    public boolean isLocked(int index) {
        return isValid(index) && states[index].isLocked();
    }

    public SlotLockState getState(int index) {
        return isValid(index) ? states[index] : SlotLockState.NONE;
    }

    public boolean canAccept(int index, ItemStack stack) {
        return !isValid(index) || states[index].accepts(stack);
    }

    /**
     * 切换锁定状态：未锁定 -> 有物品则类型锁定（记住物品种类），无物品则空锁定；
     * 已锁定 -> 解锁。切换后立即落盘。
     */
    public void toggle(int index, ItemStack currentStack) {
        if (!isValid(index)) {
            return;
        }
        if (!states[index].isLocked()) {
            states[index] = currentStack == null ? SlotLockState.EMPTY : SlotLockState.ofType(currentStack);
        } else {
            states[index] = SlotLockState.NONE;
        }
        recount();
        save();
    }

    public void load() {
        File file = lockFile();
        if (!file.isFile()) {
            clearAll();
            return;
        }
        // 流必须关闭：每次切换都会走一次 save()，句柄泄漏会在长时间游戏后累积
        try (FileInputStream in = new FileInputStream(file)) {
            NBTTagCompound root = CompressedStreamTools.readCompressed(in);
            for (int i = 0; i < SLOT_COUNT; i++) {
                states[i] = SlotLockState.fromNBT(root.getCompoundTag("slot" + i));
            }
            recount();
        } catch (Exception e) {
            clearAll();
            NHToolbox.LOG.warn("读取背包栏位锁定状态失败，已重置为全部未锁定", e);
        }
    }

    public void save() {
        NBTTagCompound root = new NBTTagCompound();
        for (int i = 0; i < SLOT_COUNT; i++) {
            root.setTag("slot" + i, states[i].toNBT());
        }
        try (FileOutputStream out = new FileOutputStream(lockFile())) {
            CompressedStreamTools.writeCompressed(root, out);
        } catch (Exception e) {
            NHToolbox.LOG.warn("保存背包栏位锁定状态失败", e);
        }
    }

    private void clearAll() {
        for (int i = 0; i < SLOT_COUNT; i++) {
            states[i] = SlotLockState.NONE;
        }
        recount();
    }

    private void recount() {
        int count = 0;
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (states[i].isLocked()) {
                count++;
            }
        }
        lockedCount = count;
    }

    private static boolean isValid(int index) {
        return index >= 0 && index < SLOT_COUNT;
    }

    private static File lockFile() {
        File configDir = new File(Minecraft.getMinecraft().mcDataDir, "config/NHToolbox");
        configDir.mkdirs();
        return new File(configDir, FILE_NAME);
    }
}
