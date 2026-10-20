package com.kkkxia.nhtoolbox;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.kkkxia.nhtoolbox.proxy.CommonProxy;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

@Mod(
    modid = NHToolbox.MODID,
    version = Tags.VERSION,
    name = "NHToolbox",
    acceptedMinecraftVersions = "[1.7.10]",
    // 声明对 AE2 的依赖，确保 FML 将本模组排在 Applied Energistics 2 之后加载：
    // 否则依赖 AE2 的功能（如扩展显示元件）在 preInit 里访问 AEConfig.instance 时会抛 NullPointerException。
    dependencies = "required-after:appliedenergistics2")
public class NHToolbox {

    public static final String MODID = "NHToolbox";
    public static final Logger LOG = LogManager.getLogger(MODID);

    @SidedProxy(
        clientSide = "com.kkkxia.nhtoolbox.proxy.ClientProxy",
        serverSide = "com.kkkxia.nhtoolbox.proxy.CommonProxy")
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
