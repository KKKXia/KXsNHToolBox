package com.KKKXia.NHToolbox;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.KKKXia.NHToolbox.Proxy.CommonProxy;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

@Mod(modid = NHToolbox.MODID, version = NHToolbox.VERSION, name = "NHToolbox", acceptedMinecraftVersions = "[1.7.10]")
public class NHToolbox {

    public static final String MODID = "NHToolbox";
    public static final String VERSION = "0.1.0";
    public static final Logger LOG = LogManager.getLogger(MODID);

    @SidedProxy(
        clientSide = "com.KKKXia.NHToolbox.Proxy.ClientProxy",
        serverSide = "com.KKKXia.NHToolbox.Proxy.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        proxy.serverStarting(event);
    }
}
