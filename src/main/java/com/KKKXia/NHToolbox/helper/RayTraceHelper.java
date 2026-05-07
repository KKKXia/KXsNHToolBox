package com.KKKXia.NHToolbox.helper;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import com.KKKXia.NHToolbox.config.ModConfig;

public class RayTraceHelper {

    public static MovingObjectPosition getRayTraceResult() {
        return getRayTraceResult(ModConfig.getRayTraceDistance());
    }

    public static MovingObjectPosition getRayTraceResult(double distance) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.thePlayer;
        World world = mc.theWorld;

        if (player == null || world == null) {
            return null;
        }

        Vec3 eyePos = getEyePosition(player);
        Vec3 lookEnd = getLookEndPosition(player, distance);
        return world.rayTraceBlocks(eyePos, lookEnd, true);
    }

    public static Vec3 getEyePosition(EntityPlayer player) {
        return Vec3.createVectorHelper(player.posX, player.posY + (double) player.getEyeHeight(), player.posZ);
    }

    public static Vec3 getLookEndPosition(EntityPlayer player, double distance) {
        Vec3 eyePos = getEyePosition(player);
        float yawCos = (float) Math.cos(-player.rotationYaw * 0.017453292F - (float) Math.PI);
        float yawSin = (float) Math.sin(-player.rotationYaw * 0.017453292F - (float) Math.PI);
        float pitchCos = (float) -Math.cos(-player.rotationPitch * 0.017453292F);
        float pitchSin = (float) Math.sin(-player.rotationPitch * 0.017453292F);
        float dirX = yawSin * pitchCos;
        float dirZ = yawCos * pitchCos;
        return eyePos.addVector(dirX * distance, pitchSin * distance, dirZ * distance);
    }

    public static int[] getFloatingPlacementPosition() {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.thePlayer;
        if (player == null) {
            return null;
        }

        MovingObjectPosition hitResult = getRayTraceResult();

        if (hitResult != null && hitResult.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            int x = hitResult.blockX;
            int y = hitResult.blockY;
            int z = hitResult.blockZ;
            switch (hitResult.sideHit) {
                case 0:
                    y--;
                    break;
                case 1:
                    y++;
                    break;
                case 2:
                    z--;
                    break;
                case 3:
                    z++;
                    break;
                case 4:
                    x--;
                    break;
                case 5:
                    x++;
                    break;
            }
            return new int[] { x, y, z };
        }

        Vec3 lookEnd = getLookEndPosition(player, ModConfig.getRayTraceDistance());
        return new int[] { (int) Math.floor(lookEnd.xCoord), (int) Math.floor(lookEnd.yCoord),
            (int) Math.floor(lookEnd.zCoord) };
    }

    public static int[] getPlacementPreviewPosition() {
        int[] pos = getFloatingPlacementPosition();
        if (pos == null) {
            return null;
        }

        Minecraft mc = Minecraft.getMinecraft();
        World world = mc.theWorld;
        if (world == null) {
            return pos;
        }

        if (world.isAirBlock(pos[0], pos[1], pos[2]) || world.getBlock(pos[0], pos[1], pos[2])
            .isReplaceable(world, pos[0], pos[1], pos[2])) {
            return pos;
        }

        return null;
    }
}
