package com.KKKXia.NHToolbox.helper;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

public class RayTraceHelper {

    private static final double MAX_RAY_DISTANCE = 5.0D;

    public static MovingObjectPosition getRayTraceResult() {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.thePlayer;
        World world = mc.theWorld;

        if (player == null || world == null) {
            return null;
        }

        float f = player.rotationPitch;
        float f1 = player.rotationYaw;
        double d0 = player.posX;
        double d1 = player.posY + (double) player.getEyeHeight();
        double d2 = player.posZ;
        Vec3 vec3 = Vec3.createVectorHelper(d0, d1, d2);
        float f2 = (float) Math.cos(-f1 * 0.017453292F - (float) Math.PI);
        float f3 = (float) Math.sin(-f1 * 0.017453292F - (float) Math.PI);
        float f4 = (float) -Math.cos(-f * 0.017453292F);
        float f5 = (float) Math.sin(-f * 0.017453292F);
        float f6 = f3 * f4;
        float f7 = f2 * f4;
        Vec3 vec31 = vec3
            .addVector((double) f6 * MAX_RAY_DISTANCE, (double) f5 * MAX_RAY_DISTANCE, (double) f7 * MAX_RAY_DISTANCE);
        return world.rayTraceBlocks(vec3, vec31, true);
    }

    public static MovingObjectPosition getRayTraceResult(double distance) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.thePlayer;
        World world = mc.theWorld;

        if (player == null || world == null) {
            return null;
        }

        float f = player.rotationPitch;
        float f1 = player.rotationYaw;
        double d0 = player.posX;
        double d1 = player.posY + (double) player.getEyeHeight();
        double d2 = player.posZ;
        Vec3 vec3 = Vec3.createVectorHelper(d0, d1, d2);
        float f2 = (float) Math.cos(-f1 * 0.017453292F - (float) Math.PI);
        float f3 = (float) Math.sin(-f1 * 0.017453292F - (float) Math.PI);
        float f4 = (float) -Math.cos(-f * 0.017453292F);
        float f5 = (float) Math.sin(-f * 0.017453292F);
        float f6 = f3 * f4;
        float f7 = f2 * f4;
        Vec3 vec31 = vec3.addVector((double) f6 * distance, (double) f5 * distance, (double) f7 * distance);
        return world.rayTraceBlocks(vec3, vec31, true);
    }
}
