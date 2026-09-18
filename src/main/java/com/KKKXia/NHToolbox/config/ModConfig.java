package com.KKKXia.NHToolbox.config;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

import com.KKKXia.NHToolbox.NHToolbox;

public class ModConfig {

    public static Configuration config;

    public static double rayTraceDistance = 1.0D;
    public static boolean enableParticles = true;
    public static boolean enablePreviewBox = true;
    public static boolean consumeItemsInSurvival = true;
    /** 背包栏位锁定总开关（默认关闭）。 */
    public static boolean enableSlotLock = false;

    public static void init(File configFile) {
        config = new Configuration(configFile);
        syncConfig();
    }

    public static void syncConfig() {
        try {
            config.load();

            rayTraceDistance = config.getFloat(
                "rayTraceDistance",
                Configuration.CATEGORY_GENERAL,
                1.0F,
                1.0F,
                20.0F,
                "Maximum distance for block placement ray trace\n浮空放置模式指示框距离玩家的距离");

            enableParticles = config.getBoolean(
                "enableParticles",
                Configuration.CATEGORY_GENERAL,
                true,
                "Enable particle effects when placing/moving blocks");

            enablePreviewBox = config.getBoolean(
                "enablePreviewBox",
                Configuration.CATEGORY_GENERAL,
                true,
                "Enable wireframe preview box for placement position");

            consumeItemsInSurvival = config.getBoolean(
                "consumeItemsInSurvival",
                Configuration.CATEGORY_GENERAL,
                true,
                "Consume items from inventory when placing in survival mode");

            enableSlotLock = config.getBoolean(
                "enableSlotLock",
                Configuration.CATEGORY_GENERAL,
                false,
                "Enable backpack slot locking (toggle a slot with the 'Lock Inventory Slot' hotkey, default middle mouse)\n"
                    + "Locking remembers the item in the slot: the slot then only accepts the same item, and keeps showing a faded icon after it is emptied.\n"
                    + "An empty slot cannot be locked. Disabled by default. Requires game restart after changing.\n"
                    + "背包栏位锁定总开关：用快捷键（默认鼠标中键）锁定栏位，锁定时记住该栏位里的物品。\n"
                    + "锁定后该栏位只能放入同种物品，物品取空后仍显示淡化图标；空栏位无法锁定。\n"
                    + "默认关闭，修改后需重启游戏生效。");

            if (config.hasChanged()) {
                config.save();
            }
        } catch (Exception e) {
            NHToolbox.LOG.error("Failed to load config", e);
        }
    }

    public static double getRayTraceDistance() {
        return rayTraceDistance;
    }

    public static boolean isParticlesEnabled() {
        return enableParticles;
    }

    public static boolean isPreviewBoxEnabled() {
        return enablePreviewBox;
    }

    public static boolean isConsumeItemsInSurvival() {
        return consumeItemsInSurvival;
    }

    /** 背包栏位锁定是否启用（默认关闭）。 */
    public static boolean isSlotLockEnabled() {
        return enableSlotLock;
    }
}
