package com.kkkxia.nhtoolbox.feature.floatingplace;

import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** 浮空放置模式下拦截右键空气，避免物品被使用掉。 */
public class FloatingPlaceInteractHandler {

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
