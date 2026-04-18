package com.KKKXia.NHToolbox.helper;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;

import com.KKKXia.NHToolbox.manager.FloatingPlaceManager;

public class BlockPlacementHelper {

    private static final FloatingPlaceManager placeManager = FloatingPlaceManager.getInstance();

    public static boolean placeBlockAt(int x, int y, int z) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.thePlayer;
        World world = mc.theWorld;

        if (player == null || world == null) {
            return false;
        }

        ItemStack itemStack = player.getHeldItem();
        if (itemStack == null) {
            return false;
        }

        // 验证物品是否可放置
        if (!isPlaceableItem(itemStack)) {
            player.addChatComponentMessage(new ChatComponentText("§c手中物品不可放置！"));
            return false;
        }

        // 检查位置是否有效
        if (!isValidPlacement(world, x, y, z)) {
            player.addChatComponentMessage(new ChatComponentText("§c放置位置无效！"));
            return false;
        }

        // 放置方块
        if (player.canPlayerEdit(x, y, z, 0, itemStack)) {
            if (world.isAirBlock(x, y, z) || world.getBlock(x, y, z)
                .isReplaceable(world, x, y, z)) {
                // 使用玩家的放置逻辑
                if (itemStack.getItem()
                    .onItemUse(itemStack, player, world, x, y, z, 1, 0.5F, 0.5F, 0.5F)) {
                    placeManager.setLastPlacedBlock(x, y, z);
                    return true;
                }
            }
        }

        return false;
    }

    public static void movePlacedBlock(int dx, int dy, int dz) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.thePlayer;
        World world = mc.theWorld;

        if (player == null || world == null) {
            return;
        }

        if (!placeManager.hasPlacedBlock()) {
            player.addChatComponentMessage(new ChatComponentText("§c请先放置一个方块！"));
            return;
        }

        int lastX = placeManager.getLastPlacedBlockX();
        int lastY = placeManager.getLastPlacedBlockY();
        int lastZ = placeManager.getLastPlacedBlockZ();

        int newX = lastX + dx;
        int newY = lastY + dy;
        int newZ = lastZ + dz;

        // 检查新位置是否有效
        if (!isValidPlacement(world, newX, newY, newZ)) {
            player.addChatComponentMessage(new ChatComponentText("§c移动位置无效，无法移动方块！"));
            return;
        }

        // 移动方块
        world.setBlockToAir(lastX, lastY, lastZ);
        ItemStack itemStack = player.getHeldItem();
        if (itemStack != null) {
            placeBlockAt(newX, newY, newZ);
        }
    }

    // 检查物品是否可放置
    private static boolean isPlaceableItem(ItemStack itemStack) {
        // 检查是否为方块或可放置物品
        return itemStack.getItem() instanceof net.minecraft.item.ItemBlock
            || itemStack.getItem() instanceof net.minecraft.item.ItemSeeds
            || itemStack.getItem() instanceof net.minecraft.item.ItemReed;
    }

    // 检查放置位置是否有效
    private static boolean isValidPlacement(World world, int x, int y, int z) {
        // 检查位置是否在世界范围内
        if (y < 0 || y >= world.getHeight()) {
            return false;
        }
        // 检查位置是否为空气或可替换方块
        return world.isAirBlock(x, y, z) || world.getBlock(x, y, z)
            .isReplaceable(world, x, y, z);
    }
}
