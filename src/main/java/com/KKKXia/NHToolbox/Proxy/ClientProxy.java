package com.KKKXia.NHToolbox.Proxy;

import static com.KKKXia.NHToolbox.handler.KeyBindings.TOGGLE_FLOATING_PLACE;

import net.minecraftforge.common.MinecraftForge;

import com.KKKXia.NHToolbox.handler.InputHandler;
import com.KKKXia.NHToolbox.handler.PlayerTickHandler;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);

        // 注册事件处理器
        InputHandler inputHandler = new InputHandler();
        PlayerTickHandler playerTickHandler = new PlayerTickHandler();

        MinecraftForge.EVENT_BUS.register(inputHandler);
        MinecraftForge.EVENT_BUS.register(playerTickHandler);
        FMLCommonHandler.instance()
            .bus()
            .register(inputHandler);
        FMLCommonHandler.instance()
            .bus()
            .register(playerTickHandler);
    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
        super.postInit(event);

        // 注册键绑定
        ClientRegistry.registerKeyBinding(TOGGLE_FLOATING_PLACE);
    }

    @Override
    public void serverStarting(FMLServerStartingEvent event) {
        super.serverStarting(event);
    }
}
