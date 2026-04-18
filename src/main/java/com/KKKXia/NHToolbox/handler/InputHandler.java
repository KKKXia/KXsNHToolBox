package com.KKKXia.NHToolbox.handler;

import net.minecraft.client.Minecraft;

import org.lwjgl.input.Keyboard;

import com.KKKXia.NHToolbox.helper.BlockPlacementHelper;
import com.KKKXia.NHToolbox.manager.FloatingPlaceManager;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;

public class InputHandler {

    private static final FloatingPlaceManager placeManager = FloatingPlaceManager.getInstance();

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.inGameHasFocus && !mc.gameSettings.showDebugInfo) {
            // 处理浮空放置模式切换
            if (KeyBindings.TOGGLE_FLOATING_PLACE.isPressed()) {
                if (placeManager.isFloatingPlaceMode()) {
                    placeManager.deactivateFloatingPlaceMode();
                } else {
                    placeManager.activateFloatingPlaceMode();
                }
            }

            // 处理方向键移动
            if (placeManager.isFloatingPlaceMode()) {
                if (Keyboard.isKeyDown(Keyboard.KEY_UP)) {
                    BlockPlacementHelper.movePlacedBlock(0, 1, 0);
                } else if (Keyboard.isKeyDown(Keyboard.KEY_DOWN)) {
                    BlockPlacementHelper.movePlacedBlock(0, -1, 0);
                } else if (Keyboard.isKeyDown(Keyboard.KEY_LEFT)) {
                    // 向玩家左手方向移动
                    float yaw = mc.thePlayer.rotationYaw;
                    int dx = (int) Math.round(Math.sin((yaw + 90) * Math.PI / 180));
                    int dz = (int) Math.round(-Math.cos((yaw + 90) * Math.PI / 180));
                    BlockPlacementHelper.movePlacedBlock(dx, 0, dz);
                } else if (Keyboard.isKeyDown(Keyboard.KEY_RIGHT)) {
                    // 向玩家右手方向移动
                    float yaw = mc.thePlayer.rotationYaw;
                    int dx = (int) Math.round(Math.sin((yaw - 90) * Math.PI / 180));
                    int dz = (int) Math.round(-Math.cos((yaw - 90) * Math.PI / 180));
                    BlockPlacementHelper.movePlacedBlock(dx, 0, dz);
                }
            }
        }
    }
}
