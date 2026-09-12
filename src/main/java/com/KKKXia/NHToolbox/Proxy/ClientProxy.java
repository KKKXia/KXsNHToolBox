package com.KKKXia.NHToolbox.Proxy;

import static com.KKKXia.NHToolbox.handler.KeyBindings.TOGGLE_FLOATING_PLACE;

import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import com.KKKXia.NHToolbox.config.ModConfig;
import com.KKKXia.NHToolbox.handler.InputHandler;
import com.KKKXia.NHToolbox.handler.KeyBindings;
import com.KKKXia.NHToolbox.handler.PlayerTickHandler;
import com.KKKXia.NHToolbox.handler.SlotLockHandler;
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
        SlotLockHandler slotLockHandler = new SlotLockHandler();

        MinecraftForge.EVENT_BUS.register(inputHandler);
        MinecraftForge.EVENT_BUS.register(playerTickHandler);
        MinecraftForge.EVENT_BUS.register(slotLockHandler);

        FMLCommonHandler.instance()
            .bus()
            .register(inputHandler);
        FMLCommonHandler.instance()
            .bus()
            .register(playerTickHandler);
        FMLCommonHandler.instance()
            .bus()
            .register(slotLockHandler);

        MinecraftForge.EVENT_BUS.register(new RenderHandler());
        MinecraftForge.EVENT_BUS.register(new InteractHandler());
    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
        super.postInit(event);
        ClientRegistry.registerKeyBinding(TOGGLE_FLOATING_PLACE);
        // 1.7.10 的 loadOptions 在 mod 按键注册前运行，options.txt 中保存的 mod 按键值
        // 不会被自动加载；这里恢复，避免每次启动都回落默认键（改键"不生效"的根因之一）。
        // 开关开启时会顺带创建并恢复"锁定背包栏位"的按键。
        KeyBindings.loadSavedBindings();
        // 背包栏位锁定：由配置开关控制（默认关闭）。关闭时连 KeyBinding 都不创建——
        // KeyBinding 构造器会把自己写进原版全局键表，默认键 -98 会抢走 keyBindPickBlock
        // 的中键条目，导致世界里"选取方块"失效（详见 KeyBindings 注释）。
        // registerLockSlot() 内部会重建键表，因此必须在恢复自定义键位之后调用。
        if (ModConfig.isSlotLockEnabled()) {
            KeyBindings.registerLockSlot();
        }
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
