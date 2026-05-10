package com.KKKXia.NHToolbox.Proxy;

import static com.KKKXia.NHToolbox.handler.KeyBindings.TOGGLE_FLOATING_PLACE;

import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import com.KKKXia.NHToolbox.handler.InputHandler;
import com.KKKXia.NHToolbox.handler.PlayerTickHandler;
import com.KKKXia.NHToolbox.manager.FloatingPlaceManager;
import com.KKKXia.NHToolbox.render.FloatingPlaceRenderer;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);

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

        MinecraftForge.EVENT_BUS.register(new RenderHandler());
        MinecraftForge.EVENT_BUS.register(new InteractHandler());
    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
        super.postInit(event);
        ClientRegistry.registerKeyBinding(TOGGLE_FLOATING_PLACE);
    }

    @Override
    public void serverStarting(FMLServerStartingEvent event) {
        super.serverStarting(event);
    }

    public static class RenderHandler {

        @SubscribeEvent
        public void onRenderWorldLast(RenderWorldLastEvent event) {
            FloatingPlaceRenderer.renderPreviewBox(event.partialTicks);
        }
    }

    public static class InteractHandler {

        private static final FloatingPlaceManager placeManager = FloatingPlaceManager.getInstance();

        @SubscribeEvent
        public void onPlayerInteract(PlayerInteractEvent event) {
            if (!placeManager.isFloatingPlaceMode()) {
                return;
            }

            if (event.world.isRemote && event.action == PlayerInteractEvent.Action.RIGHT_CLICK_AIR) {
                event.setCanceled(true);
            }
        }
    }
}
