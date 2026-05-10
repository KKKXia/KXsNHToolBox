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
}
