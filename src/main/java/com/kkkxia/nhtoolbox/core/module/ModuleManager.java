package com.kkkxia.nhtoolbox.core.module;

import java.util.ArrayList;
import java.util.List;

import com.kkkxia.nhtoolbox.NHToolbox;
import com.kkkxia.nhtoolbox.client.keybinding.KeyBindingManager;
import com.kkkxia.nhtoolbox.core.config.ConfigManager;
import com.kkkxia.nhtoolbox.network.NetworkManager;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.eventhandler.EventBus;

/**
 * 功能模块的中央驱动器：按阶段驱动生命周期，模块清单由 {@link ModuleRegistry} 提供。
 * 禁用模块（isEnabled() == false）全程零介入：不注册事件 / 按键 / 网络包，也不收到生命周期回调。
 */
public final class ModuleManager {

    private final ConfigManager config;
    private final NetworkManager network;
    private final List<IFeatureModule> modules;
    private final List<IFeatureModule> active = new ArrayList<>();

    public ModuleManager(ConfigManager config, NetworkManager network, List<IFeatureModule> modules) {
        this.config = config;
        this.network = network;
        this.modules = modules;
    }

    /** 解析配置并筛选启用模块。必须先解析配置再判定 isEnabled()，否则配置项尚未读入。 */
    public void bootstrap(boolean clientSide) {
        config.load();
        for (IFeatureModule module : modules) {
            module.registerConfig(config);
        }
        config.saveIfChanged();

        active.clear();
        for (IFeatureModule module : modules) {
            if (module.side() == ModuleSide.CLIENT && !clientSide) {
                continue; // 客户端模块不在服务端启动
            }
            if (module.isEnabled()) {
                active.add(module);
            }
        }
    }

    /** 为已启用模块注册网络包、事件与按键。 */
    public void register(boolean clientSide, EventBus forgeBus, EventBus fmlBus, KeyBindingManager keyBindings) {
        for (IFeatureModule module : active) {
            module.registerNetwork(network);
            module.registerEvents(forgeBus, fmlBus);
            if (clientSide && keyBindings != null) {
                module.registerKeyBindings(keyBindings);
            }
        }
        NHToolbox.LOG.info("已启用 {} / {} 个功能模块", active.size(), modules.size());
    }

    public void preInit(FMLPreInitializationEvent event) {
        for (IFeatureModule module : active) {
            module.preInit(event);
        }
    }

    public void init(FMLInitializationEvent event) {
        for (IFeatureModule module : active) {
            module.init(event);
        }
    }

    public void postInit(FMLPostInitializationEvent event) {
        for (IFeatureModule module : active) {
            module.postInit(event);
        }
    }

    public void serverStarting(FMLServerStartingEvent event) {
        for (IFeatureModule module : active) {
            module.serverStarting(event);
        }
    }
}
