package com.kkkxia.nhtoolbox.feature.slotlock;

import net.minecraft.client.settings.KeyBinding;

import com.kkkxia.nhtoolbox.client.keybinding.KeyBindingManager;
import com.kkkxia.nhtoolbox.core.config.ConfigManager;
import com.kkkxia.nhtoolbox.core.module.IFeatureModule;
import com.kkkxia.nhtoolbox.core.module.ModuleSide;

import cpw.mods.fml.common.eventhandler.EventBus;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 背包栏位锁定模块：按键、界面拦截与锁定数据执行都在客户端，服务端不采集，因此加载侧为 CLIENT。
 */
public class SlotLockModule implements IFeatureModule {

    /** 背包栏位锁定默认键：鼠标中键（-100 左键 / -99 右键 / -98 中键，与原版 keyBindPickBlock 同值）。 */
    private static final int DEFAULT_LOCK_KEY_CODE = -98;

    private static boolean enabled;

    /**
     * 背包栏位锁定按键：懒创建，只有功能开启时才会被构造。
     *
     * <p>
     * 为什么必须懒创建：{@link KeyBinding} 的构造函数会把自己写进原版全局键表
     * （KeyBinding.java:88-89 {@code keybindArray.add(this); hash.addKey(keyCode, this);}），
     * 而 {@code IntHashMap.addKey} 对同一键码是"后写入者覆盖"。默认键 -98 与原版
     * {@code keyBindPickBlock}（GameSettings.java:214）冲突，所以只要构造了它，世界的"选取方块"
     * 就再也收不到按下状态——开关关闭时构造会白白破坏原版功能。
     */
    @SideOnly(Side.CLIENT)
    private static KeyBinding lockSlot;

    @Override
    public String id() {
        return "slotlock";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public ModuleSide side() {
        return ModuleSide.CLIENT;
    }

    @Override
    public void registerConfig(ConfigManager config) {
        enabled = config.getBoolean(
            "enableSlotLock",
            ConfigManager.CATEGORY_GENERAL,
            false,
            "Enable backpack slot locking (toggle a slot with the 'Lock Inventory Slot' hotkey, default middle mouse)\n"
                + "Locking remembers the item in the slot: the slot then only accepts the same item, and keeps showing a faded icon after it is emptied.\n"
                + "An empty slot cannot be locked. Disabled by default. Requires game restart after changing.\n"
                + "背包栏位锁定总开关：用快捷键（默认鼠标中键）锁定栏位，锁定时记住该栏位里的物品。\n"
                + "锁定后该栏位只能放入同种物品，物品取空后仍显示淡化图标；空栏位无法锁定。\n"
                + "默认关闭，修改后需重启游戏生效。");
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void registerKeyBindings(KeyBindingManager keyBindings) {
        if (!enabled) {
            return; // 关闭时连 KeyBinding 都不构造，原版中键（选取方块）保持原状
        }
        keyBindings.register(getLockSlot());
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void registerEvents(EventBus forgeBus, EventBus fmlBus) {
        SlotLockHandler handler = new SlotLockHandler();
        forgeBus.register(handler);
        fmlBus.register(handler);
    }

    /** 该功能是否启用（默认关闭），供 mixin 与界面层使用。 */
    public static boolean isLockEnabled() {
        return enabled;
    }

    /** 获取（必要时创建）锁定按键。功能关闭时不会被调用，因此不会污染原版键表。 */
    @SideOnly(Side.CLIENT)
    private static KeyBinding getLockSlot() {
        if (lockSlot == null) {
            lockSlot = new KeyBinding("key.nhtoolbox.lock_slot", DEFAULT_LOCK_KEY_CODE, "key.categories.nhtoolbox");
        }
        return lockSlot;
    }

    /** 当前锁定键码；未创建时返回默认值。 */
    public static int getLockSlotKeyCode() {
        return lockSlot == null ? DEFAULT_LOCK_KEY_CODE : lockSlot.getKeyCode();
    }
}
