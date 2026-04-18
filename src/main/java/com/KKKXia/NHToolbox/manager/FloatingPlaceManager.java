package com.KKKXia.NHToolbox.manager;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;

public class FloatingPlaceManager {

    private static FloatingPlaceManager instance;
    private boolean isFloatingPlaceMode = false;
    private int lastPlacedBlockX = -1;
    private int lastPlacedBlockY = -1;
    private int lastPlacedBlockZ = -1;

    private FloatingPlaceManager() {}

    public static FloatingPlaceManager getInstance() {
        if (instance == null) {
            instance = new FloatingPlaceManager();
        }
        return instance;
    }

    public boolean isFloatingPlaceMode() {
        return isFloatingPlaceMode;
    }

    public void setFloatingPlaceMode(boolean mode) {
        this.isFloatingPlaceMode = mode;
    }

    public int getLastPlacedBlockX() {
        return lastPlacedBlockX;
    }

    public int getLastPlacedBlockY() {
        return lastPlacedBlockY;
    }

    public int getLastPlacedBlockZ() {
        return lastPlacedBlockZ;
    }

    public void setLastPlacedBlock(int x, int y, int z) {
        this.lastPlacedBlockX = x;
        this.lastPlacedBlockY = y;
        this.lastPlacedBlockZ = z;
    }

    public void resetLastPlacedBlock() {
        this.lastPlacedBlockX = -1;
        this.lastPlacedBlockY = -1;
        this.lastPlacedBlockZ = -1;
    }

    public void activateFloatingPlaceMode() {
        setFloatingPlaceMode(true);
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        if (player != null) {
            player.addChatComponentMessage(new ChatComponentText("§a浮空放置模式已激活！"));
        }
    }

    public void deactivateFloatingPlaceMode() {
        setFloatingPlaceMode(false);
        resetLastPlacedBlock();
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        if (player != null) {
            player.addChatComponentMessage(new ChatComponentText("§c浮空放置模式已关闭！"));
        }
    }

    public boolean hasPlacedBlock() {
        return lastPlacedBlockX != -1 && lastPlacedBlockY != -1 && lastPlacedBlockZ != -1;
    }
}
