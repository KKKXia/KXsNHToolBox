package com.KKKXia.NHToolbox.inventory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 玩家背包 36 格（0-8 快捷栏、9-35 主物品栏）锁定状态的管理单例，纯客户端。
 *
 * <p>
 * 状态持久化到 {@code config/NHToolbox/slotLocks.dat}（NBT 压缩格式）：
 * 每次切换后立即保存，进入世界后加载；文件缺失或损坏时静默重置为全部未锁定。
 */
public final class SlotLockManager {

    private static final Logger LOG = LogManager.getLogger("NHToolbox.SlotLock");

    /** 背包栏位数量：0-8 快捷栏 + 9-35 主物品栏。 */
    public static final int SLOT_COUNT = 36;

    private static final SlotLockManager INSTANCE = new SlotLockManager();

    private final SlotLockState[] states = new SlotLockState[SLOT_COUNT];

    private SlotLockManager() {
        for (int i = 0; i < SLOT_COUNT; i++) {
            states[i] = SlotLockState.NONE;
        }
    }

    public static SlotLockManager getInstance() {
        return INSTANCE;
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
        save();
    }

    public void load() {
        try {
            NBTTagCompound root = CompressedStreamTools.readCompressed(new FileInputStream(lockFile()));
            for (int i = 0; i < SLOT_COUNT; i++) {
                states[i] = SlotLockState.fromNBT(root.getCompoundTag("slot" + i));
            }
        } catch (Exception ignored) {
            // 文件缺失/损坏：静默重置，不打断游戏
            for (int i = 0; i < SLOT_COUNT; i++) {
                states[i] = SlotLockState.NONE;
            }
        }
    }

    public void save() {
        try {
            NBTTagCompound root = new NBTTagCompound();
            for (int i = 0; i < SLOT_COUNT; i++) {
                root.setTag("slot" + i, states[i].toNBT());
            }
            CompressedStreamTools.writeCompressed(root, new FileOutputStream(lockFile()));
        } catch (Exception e) {
            LOG.warn("保存背包栏位锁定状态失败", e);
        }
    }

    private static boolean isValid(int index) {
        return index >= 0 && index < SLOT_COUNT;
    }

    private static File lockFile() {
        File configDir = new File(Minecraft.getMinecraft().mcDataDir, "config/NHToolbox");
        configDir.mkdirs();
        return new File(configDir, "slotLocks.dat");
    }
}
