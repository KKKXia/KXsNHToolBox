package com.kkkxia.nhtoolbox.core.config;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

import com.kkkxia.nhtoolbox.NHToolbox;

/**
 * 配置的唯一入口：模块经本类读写配置项，不直接接触 Forge 的 {@link Configuration}。
 * 配置文件名（config/NHToolbox.cfg）与键名 / 默认值需与旧版保持一致，以兼容旧档。
 */
public final class ConfigManager {

    public static final String CATEGORY_GENERAL = Configuration.CATEGORY_GENERAL;

    private final Configuration configuration;

    public ConfigManager(File configFile) {
        this.configuration = new Configuration(configFile);
    }

    public void load() {
        try {
            configuration.load();
        } catch (Exception e) {
            NHToolbox.LOG.error("读取配置失败", e);
        }
    }

    /** 仅在配置有改动时落盘。 */
    public void saveIfChanged() {
        try {
            if (configuration.hasChanged()) {
                configuration.save();
            }
        } catch (Exception e) {
            NHToolbox.LOG.error("保存配置失败", e);
        }
    }

    public double getDouble(String key, String category, double defaultValue, double min, double max, String comment) {
        return configuration.getFloat(key, category, (float) defaultValue, (float) min, (float) max, comment);
    }

    public boolean getBoolean(String key, String category, boolean defaultValue, String comment) {
        return configuration.getBoolean(key, category, defaultValue, comment);
    }

    public int getInt(String key, String category, int defaultValue, int min, int max, String comment) {
        return configuration.getInt(key, category, defaultValue, min, max, comment);
    }

    public String getString(String key, String category, String defaultValue, String comment) {
        return configuration.getString(key, category, defaultValue, comment);
    }
}
