package com.kkkxia.nhtoolbox.core.module;

import com.kkkxia.nhtoolbox.client.keybinding.KeyBindingManager;
import com.kkkxia.nhtoolbox.core.config.ConfigManager;
import com.kkkxia.nhtoolbox.network.NetworkManager;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.eventhandler.EventBus;

/**
 * 功能模块契约：每个功能实现为一个自包含模块，由 {@link ModuleManager} 显式登记并驱动。
 * 除 id/side/isEnabled 外的接入点可按需覆写，未覆写即表示不参与该环节。
 */
public interface IFeatureModule {

    String id();

    boolean isEnabled();

    ModuleSide side();

    default void registerConfig(ConfigManager config) {}

    default void registerNetwork(NetworkManager network) {}

    default void registerEvents(EventBus forgeBus, EventBus fmlBus) {}

    default void registerKeyBindings(KeyBindingManager keyBindings) {}

    default void preInit(FMLPreInitializationEvent event) {}

    default void init(FMLInitializationEvent event) {}

    default void postInit(FMLPostInitializationEvent event) {}

    default void serverStarting(FMLServerStartingEvent event) {}
}
