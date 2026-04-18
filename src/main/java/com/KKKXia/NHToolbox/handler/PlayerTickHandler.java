package com.KKKXia.NHToolbox.handler;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;

import com.KKKXia.NHToolbox.helper.BlockPlacementHelper;
import com.KKKXia.NHToolbox.helper.RayTraceHelper;
import com.KKKXia.NHToolbox.manager.FloatingPlaceManager;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public class PlayerTickHandler {

    private static final FloatingPlaceManager placeManager = FloatingPlaceManager.getInstance();
    private ItemStack lastHeldItem = null;

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player == Minecraft.getMinecraft().thePlayer) {
            handleItemChange(event.player);
            handleRightClickPlacement();
        }
    }

    private void handleItemChange(EntityPlayer player) {
        ItemStack currentHeldItem = player.getHeldItem();
        if (placeManager.isFloatingPlaceMode() && lastHeldItem != null
            && (currentHeldItem == null || currentHeldItem.getItem() != lastHeldItem.getItem())) {
            placeManager.deactivateFloatingPlaceMode();
        }
        lastHeldItem = currentHeldItem;
    }

    private void handleRightClickPlacement() {
        if (placeManager.isFloatingPlaceMode() && Minecraft.getMinecraft().gameSettings.keyBindUseItem.isPressed()) {
            MovingObjectPosition hitResult = RayTraceHelper.getRayTraceResult();
            if (hitResult != null) {
                // 计算放置位置
                int x = hitResult.blockX;
                int y = hitResult.blockY;
                int z = hitResult.blockZ;

                // 根据命中面调整位置
                switch (hitResult.sideHit) {
                    case 0:
                        y--;
                        break; // 底部
                    case 1:
                        y++;
                        break; // 顶部
                    case 2:
                        z--;
                        break; // 北面
                    case 3:
                        z++;
                        break; // 南面
                    case 4:
                        x--;
                        break; // 西面
                    case 5:
                        x++;
                        break; // 东面
                }

                BlockPlacementHelper.placeBlockAt(x, y, z);
            }
        }
    }
}
