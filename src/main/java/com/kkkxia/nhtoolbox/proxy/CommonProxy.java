package com.kkkxia.nhtoolbox.proxy;

import net.minecraftforge.common.MinecraftForge;

import com.kkkxia.nhtoolbox.client.keybinding.KeyBindingManager;
import com.kkkxia.nhtoolbox.core.config.ConfigManager;
import com.kkkxia.nhtoolbox.core.module.ModuleManager;
import com.kkkxia.nhtoolbox.core.module.ModuleRegistry;
import com.kkkxia.nhtoolbox.network.NetworkManager;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

/** 公共代理：创建 mod 级服务（配置、网络、模块驱动器），并把生命周期转发给 {@link ModuleManager}。 */
public class CommonProxy {

    private ConfigManager config;
    private NetworkManager network;
    private ModuleManager moduleManager;

    public void preInit(FMLPreInitializationEvent event) {
        config = new ConfigManager(event.getSuggestedConfigurationFile());
        network = new NetworkManager();
        moduleManager = new ModuleManager(config, network, ModuleRegistry.modules());
        moduleManager.bootstrap(isClientSide());
        moduleManager.register(
            isClientSide(),
            MinecraftForge.EVENT_BUS,
            FMLCommonHandler.instance()
                .bus(),
            keyBindings());
        moduleManager.preInit(event);
    }

    public void init(FMLInitializationEvent event) {
        moduleManager.init(event);
    }

    public void postInit(FMLPostInitializationEvent event) {
        moduleManager.postInit(event);
    }

    public void serverStarting(FMLServerStartingEvent event) {
        moduleManager.serverStarting(event);
    }

    /** 由 {@link ClientProxy} 覆盖。 */
    protected boolean isClientSide() {
        return false;
    }

    /** 由 {@link ClientProxy} 覆盖；服务端为 null。 */
    protected KeyBindingManager keyBindings() {
        return null;
    }
}
