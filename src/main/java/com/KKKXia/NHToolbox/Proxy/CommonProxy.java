package com.KKKXia.NHToolbox.Proxy;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        // 预初始化代码
    }

    public void init(FMLInitializationEvent event) {
        // 初始化代码
    }

    public void postInit(FMLPostInitializationEvent event) {
        // 后初始化代码
    }

    public void serverStarting(FMLServerStartingEvent event) {
        // 服务器启动代码
    }
}
