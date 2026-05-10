package com.KKKXia.NHToolbox.manager;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;

import com.KKKXia.NHToolbox.NHToolbox;

public class FloatingPlaceManager {

    public enum Mode {
        IDLE,
        PLACING,
        ADJUSTING
    }

    private static FloatingPlaceManager instance;
    private Mode currentMode = Mode.IDLE;
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

    public Mode getCurrentMode() {
        return currentMode;
    }

    public boolean isFloatingPlaceMode() {
        return currentMode != Mode.IDLE;
    }

    public boolean isPlacingMode() {
        return currentMode == Mode.PLACING;
    }

    public boolean isAdjustMode() {
        return currentMode == Mode.ADJUSTING;
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
        this.currentMode = Mode.ADJUSTING;
        NHToolbox.LOG.info("Block placed at: " + x + ", " + y + ", " + z + " - entering adjust mode");
    }

    public void resetLastPlacedBlock() {
        this.lastPlacedBlockX = -1;
        this.lastPlacedBlockY = -1;
        this.lastPlacedBlockZ = -1;
        this.currentMode = Mode.PLACING;
    }

    public boolean hasPlacedBlock() {
        return lastPlacedBlockX != -1 && lastPlacedBlockY != -1 && lastPlacedBlockZ != -1;
    }

    public void activateFloatingPlaceMode() {
        this.currentMode = Mode.PLACING;
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        if (player != null) {
            player.addChatComponentMessage(new ChatComponentText("§a[浮空放置] 模式已激活！右键空中放置方块，方向键调整位置。"));
        }
        NHToolbox.LOG.info("Floating place mode activated (PLACING)");
    }

    public void deactivateFloatingPlaceMode() {
        this.currentMode = Mode.IDLE;
        this.lastPlacedBlockX = -1;
        this.lastPlacedBlockY = -1;
        this.lastPlacedBlockZ = -1;
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        if (player != null) {
            player.addChatComponentMessage(new ChatComponentText("§c[浮空放置] 模式已关闭！"));
        }
        NHToolbox.LOG.info("Floating place mode deactivated");
    }
}
