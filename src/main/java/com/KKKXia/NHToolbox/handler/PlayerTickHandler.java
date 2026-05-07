package com.KKKXia.NHToolbox.handler;

import net.minecraft.client.Minecraft;
import net.minecraft.util.MovingObjectPosition;

import org.lwjgl.input.Mouse;

import com.KKKXia.NHToolbox.NHToolbox;
import com.KKKXia.NHToolbox.helper.BlockPlacementHelper;
import com.KKKXia.NHToolbox.helper.RayTraceHelper;
import com.KKKXia.NHToolbox.manager.FloatingPlaceManager;
import com.KKKXia.NHToolbox.network.PacketFloatingPlace;
import com.KKKXia.NHToolbox.network.PacketHandler;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public class PlayerTickHandler {

    private static final FloatingPlaceManager placeManager = FloatingPlaceManager.getInstance();
    private boolean wasRightButtonDown = false;

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (event.player != Minecraft.getMinecraft().thePlayer) {
            return;
        }

        handleRightClickPlacement();
    }

    private void handleRightClickPlacement() {
        if (!placeManager.isPlacingMode()) {
            return;
        }

        boolean isRightDown = Mouse.isButtonDown(1);

        if (!isRightDown || wasRightButtonDown) {
            wasRightButtonDown = isRightDown;
            return;
        }

        wasRightButtonDown = true;

        MovingObjectPosition hitResult = RayTraceHelper.getRayTraceResult();
        if (hitResult != null && hitResult.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            return;
        }

        NHToolbox.LOG.debug("FloatingPlace: Right-click in air detected, calculating position...");

        int[] pos = RayTraceHelper.getFloatingPlacementPosition();
        if (pos == null) {
            NHToolbox.LOG.debug("FloatingPlace: No valid position returned");
            return;
        }

        NHToolbox.LOG.debug("FloatingPlace: Target position = " + pos[0] + ", " + pos[1] + ", " + pos[2]);

        boolean success = BlockPlacementHelper.placeBlockAtClient(pos[0], pos[1], pos[2]);

        if (success) {
            NHToolbox.LOG.debug("FloatingPlace: Block placed successfully, entering adjust mode");
            sendPlacementPacket(pos[0], pos[1], pos[2]);
        }
    }

    private void sendPlacementPacket(int x, int y, int z) {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            int dimensionId = mc.theWorld.provider.dimensionId;
            PacketHandler.INSTANCE.sendToServer(new PacketFloatingPlace(x, y, z, dimensionId));
        } catch (Exception e) {
            NHToolbox.LOG.warn("Failed to send placement packet", e);
        }
    }
}
