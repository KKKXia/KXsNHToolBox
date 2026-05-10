package com.KKKXia.NHToolbox.handler;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;

import org.lwjgl.input.Keyboard;

import com.KKKXia.NHToolbox.helper.BlockPlacementHelper;
import com.KKKXia.NHToolbox.manager.FloatingPlaceManager;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;

public class InputHandler {

    private static final FloatingPlaceManager placeManager = FloatingPlaceManager.getInstance();
    private boolean[] lastKeyStates = new boolean[256];

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!mc.inGameHasFocus || mc.gameSettings.showDebugInfo) {
            return;
        }

        if (KeyBindings.TOGGLE_FLOATING_PLACE.isPressed()) {
            if (!placeManager.isFloatingPlaceMode()) {
                placeManager.activateFloatingPlaceMode();
            } else if (placeManager.isAdjustMode()) {
                placeManager.resetLastPlacedBlock();
            } else {
                placeManager.deactivateFloatingPlaceMode();
            }
        }

        if (!placeManager.isFloatingPlaceMode()) {
            return;
        }

        boolean upPressed = Keyboard.isKeyDown(Keyboard.KEY_UP);
        boolean downPressed = Keyboard.isKeyDown(Keyboard.KEY_DOWN);
        boolean leftPressed = Keyboard.isKeyDown(Keyboard.KEY_LEFT);
        boolean rightPressed = Keyboard.isKeyDown(Keyboard.KEY_RIGHT);
        boolean rPressed = Keyboard.isKeyDown(Keyboard.KEY_G);

        if (upPressed && !lastKeyStates[Keyboard.KEY_UP]) {
            BlockPlacementHelper.movePlacedBlock(0, 1, 0);
        } else if (downPressed && !lastKeyStates[Keyboard.KEY_DOWN]) {
            BlockPlacementHelper.movePlacedBlock(0, -1, 0);
        } else if (leftPressed && !lastKeyStates[Keyboard.KEY_LEFT]) {
            float yaw = mc.thePlayer.rotationYaw;
            int dx = (int) Math.round(Math.sin((yaw + 90) * Math.PI / 180));
            int dz = (int) Math.round(-Math.cos((yaw + 90) * Math.PI / 180));
            BlockPlacementHelper.movePlacedBlock(dx, 0, dz);
        } else if (rightPressed && !lastKeyStates[Keyboard.KEY_RIGHT]) {
            float yaw = mc.thePlayer.rotationYaw;
            int dx = (int) Math.round(Math.sin((yaw - 90) * Math.PI / 180));
            int dz = (int) Math.round(-Math.cos((yaw - 90) * Math.PI / 180));
            BlockPlacementHelper.movePlacedBlock(dx, 0, dz);
        } else if (rPressed && !lastKeyStates[Keyboard.KEY_G]) {
            placeManager.resetLastPlacedBlock();
            mc.thePlayer.addChatMessage(new ChatComponentText("§e[浮空放置] 已重置，可以放置新方块。"));
        }

        lastKeyStates[Keyboard.KEY_UP] = upPressed;
        lastKeyStates[Keyboard.KEY_DOWN] = downPressed;
        lastKeyStates[Keyboard.KEY_LEFT] = leftPressed;
        lastKeyStates[Keyboard.KEY_RIGHT] = rightPressed;
        lastKeyStates[Keyboard.KEY_G] = rPressed;
    }
}
