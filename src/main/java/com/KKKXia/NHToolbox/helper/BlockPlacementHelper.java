package com.KKKXia.NHToolbox.helper;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;

import com.KKKXia.NHToolbox.NHToolbox;
import com.KKKXia.NHToolbox.config.ModConfig;
import com.KKKXia.NHToolbox.manager.FloatingPlaceManager;
import com.KKKXia.NHToolbox.network.PacketFloatingPlaceMove;
import com.KKKXia.NHToolbox.network.PacketHandler;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class BlockPlacementHelper {

    private static final FloatingPlaceManager placeManager = FloatingPlaceManager.getInstance();

    @SideOnly(Side.CLIENT)
    public static boolean placeBlockAtClient(int x, int y, int z) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.thePlayer;
        World world = mc.theWorld;

        if (!validatePlacement(world, player, x, y, z)) {
            return false;
        }

        ItemStack itemStack = player.getHeldItem();
        if (placeBlockDirectly(world, player, itemStack, x, y, z)) {
            placeManager.setLastPlacedBlock(x, y, z);
            spawnPlaceParticles(world, x, y, z);
            return true;
        }

        player.addChatComponentMessage(new ChatComponentText("§c[浮空放置] 方块放置失败！"));
        return false;
    }

    public static boolean placeBlockOnServer(World world, EntityPlayerMP player, int x, int y, int z) {
        if (!validatePlacement(world, player, x, y, z)) {
            return false;
        }

        ItemStack itemStack = player.getHeldItem();
        if (placeBlockDirectly(world, player, itemStack, x, y, z)) {
            world.playSoundEffect(
                x + 0.5D,
                y + 0.5D,
                z + 0.5D,
                world.getBlock(x, y, z).stepSound.func_150496_b(),
                1.0F,
                0.8F);
            return true;
        }
        return false;
    }

    private static boolean validatePlacement(World world, EntityPlayer player, int x, int y, int z) {
        if (player == null || world == null) {
            return false;
        }

        if (y < 0 || y >= world.getHeight()) {
            player.addChatComponentMessage(new ChatComponentText("§c[浮空放置] 超出世界边界！"));
            return false;
        }

        ItemStack itemStack = player.getHeldItem();
        if (itemStack == null) {
            player.addChatComponentMessage(new ChatComponentText("§c[浮空放置] 手中没有物品！"));
            return false;
        }

        if (!isPlaceableItem(itemStack)) {
            player.addChatComponentMessage(new ChatComponentText("§c[浮空放置] 手中物品不可放置！"));
            return false;
        }

        if (!world.isAirBlock(x, y, z) && !world.getBlock(x, y, z)
            .isReplaceable(world, x, y, z)) {
            player.addChatComponentMessage(new ChatComponentText("§c[浮空放置] 目标位置已被占用！"));
            return false;
        }

        if (!player.canPlayerEdit(x, y, z, 0, itemStack)) {
            player.addChatComponentMessage(new ChatComponentText("§c[浮空放置] 没有权限在此位置放置方块！"));
            return false;
        }

        Block block = getBlockFromItem(itemStack);
        if (block != null) {
            AxisAlignedBB aabb = block.getCollisionBoundingBoxFromPool(world, x, y, z);
            if (aabb != null) {
                aabb = aabb.offset(x, y, z);
                if (!world.getCollidingBoundingBoxes(player, aabb)
                    .isEmpty()) {
                    player.addChatComponentMessage(new ChatComponentText("§c[浮空放置] 目标位置被实体阻挡！"));
                    return false;
                }
            }
        }

        return true;
    }

    private static boolean placeBlockDirectly(World world, EntityPlayer player, ItemStack itemStack, int x, int y,
        int z) {
        if (itemStack == null) {
            return false;
        }

        if (itemStack.getItem() instanceof ItemBlock) {
            ItemBlock itemBlock = (ItemBlock) itemStack.getItem();
            Block block = itemBlock.field_150939_a;

            if (block == null || block == Blocks.air) {
                return false;
            }

            int metadata = itemBlock.getMetadata(itemStack.getItemDamage());

            if (itemBlock.placeBlockAt(itemStack, player, world, x, y, z, 1, 0.5F, 0.5F, 0.5F, metadata)) {
                if (!player.capabilities.isCreativeMode && ModConfig.isConsumeItemsInSurvival()) {
                    itemStack.stackSize--;
                    if (itemStack.stackSize <= 0) {
                        player.inventory.mainInventory[player.inventory.currentItem] = null;
                    }
                }
                return true;
            }
            return false;
        }

        try {
            if (itemStack.getItem()
                .onItemUse(itemStack, player, world, x, y, z, 1, 0.5F, 0.5F, 0.5F)) {
                return true;
            }
        } catch (Exception e) {
            NHToolbox.LOG.warn("Exception during onItemUse for item: " + itemStack.getDisplayName(), e);
        }

        return false;
    }

    @SideOnly(Side.CLIENT)
    public static void movePlacedBlock(int dx, int dy, int dz) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.thePlayer;
        World world = mc.theWorld;

        if (player == null || world == null) {
            return;
        }

        if (!placeManager.hasPlacedBlock()) {
            player.addChatComponentMessage(new ChatComponentText("§c[浮空放置] 请先放置一个方块！"));
            return;
        }

        int lastX = placeManager.getLastPlacedBlockX();
        int lastY = placeManager.getLastPlacedBlockY();
        int lastZ = placeManager.getLastPlacedBlockZ();

        Block oldBlock = world.getBlock(lastX, lastY, lastZ);
        int oldMetadata = world.getBlockMetadata(lastX, lastY, lastZ);

        if (oldBlock == null || oldBlock == Blocks.air) {
            placeManager.resetLastPlacedBlock();
            player.addChatComponentMessage(new ChatComponentText("§c[浮空放置] 上次放置的方块已不存在！"));
            return;
        }

        int newX = lastX + dx;
        int newY = lastY + dy;
        int newZ = lastZ + dz;

        if (newY < 0 || newY >= world.getHeight()) {
            player.addChatComponentMessage(new ChatComponentText("§c[浮空放置] 无法移动到世界边界外！"));
            return;
        }

        if (!world.isAirBlock(newX, newY, newZ) && !world.getBlock(newX, newY, newZ)
            .isReplaceable(world, newX, newY, newZ)) {
            player.addChatComponentMessage(new ChatComponentText("§c[浮空放置] 目标位置有方块阻挡，无法移动！"));
            return;
        }

        AxisAlignedBB aabb = oldBlock.getCollisionBoundingBoxFromPool(world, newX, newY, newZ);
        if (aabb != null) {
            aabb = aabb.offset(newX, newY, newZ);
            if (!world.getCollidingBoundingBoxes(player, aabb)
                .isEmpty()) {
                player.addChatComponentMessage(new ChatComponentText("§c[浮空放置] 目标位置被实体阻挡，无法移动！"));
                return;
            }
        }

        NBTTagCompound tileNbt = null;
        TileEntity oldTile = world.getTileEntity(lastX, lastY, lastZ);
        if (oldTile != null) {
            tileNbt = new NBTTagCompound();
            oldTile.writeToNBT(tileNbt);
            oldTile.invalidate();
        }

        world.setBlockToAir(lastX, lastY, lastZ);
        world.setBlock(newX, newY, newZ, oldBlock, oldMetadata, 3);

        if (tileNbt != null) {
            TileEntity newTile = world.getTileEntity(newX, newY, newZ);
            if (newTile != null) {
                tileNbt.setInteger("x", newX);
                tileNbt.setInteger("y", newY);
                tileNbt.setInteger("z", newZ);
                newTile.readFromNBT(tileNbt);
                newTile.markDirty();
            }
        }

        if (world.getBlock(newX, newY, newZ) == oldBlock) {
            placeManager.setLastPlacedBlock(newX, newY, newZ);
            world
                .playSoundEffect(newX + 0.5D, newY + 0.5D, newZ + 0.5D, oldBlock.stepSound.func_150496_b(), 1.0F, 0.8F);
            spawnMoveParticles(world, newX, newY, newZ);
            sendMovePacket(lastX, lastY, lastZ, newX, newY, newZ);
        } else {
            world.setBlock(lastX, lastY, lastZ, oldBlock, oldMetadata, 3);
            if (tileNbt != null) {
                TileEntity restoredTile = world.getTileEntity(lastX, lastY, lastZ);
                if (restoredTile != null) {
                    tileNbt.setInteger("x", lastX);
                    tileNbt.setInteger("y", lastY);
                    tileNbt.setInteger("z", lastZ);
                    restoredTile.readFromNBT(tileNbt);
                    restoredTile.markDirty();
                }
            }
            player.addChatComponentMessage(new ChatComponentText("§c[浮空放置] 方块移动失败！"));
        }
    }

    @SideOnly(Side.CLIENT)
    private static void spawnPlaceParticles(World world, int x, int y, int z) {
        if (!ModConfig.isParticlesEnabled()) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        for (int i = 0; i < 8; i++) {
            double px = x + world.rand.nextDouble();
            double py = y + world.rand.nextDouble() * 0.5D;
            double pz = z + world.rand.nextDouble();
            double vx = (world.rand.nextDouble() - 0.5D) * 0.2D;
            double vy = world.rand.nextDouble() * 0.2D;
            double vz = (world.rand.nextDouble() - 0.5D) * 0.2D;
            mc.theWorld.spawnParticle("happyVillager", px, py, pz, vx, vy, vz);
        }
    }

    @SideOnly(Side.CLIENT)
    private static void spawnMoveParticles(World world, int x, int y, int z) {
        if (!ModConfig.isParticlesEnabled()) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        for (int i = 0; i < 6; i++) {
            double px = x + 0.5D + (world.rand.nextDouble() - 0.5D) * 1.2D;
            double py = y + 0.5D + (world.rand.nextDouble() - 0.5D) * 1.2D;
            double pz = z + 0.5D + (world.rand.nextDouble() - 0.5D) * 1.2D;
            double vx = (world.rand.nextDouble() - 0.5D) * 0.1D;
            double vy = (world.rand.nextDouble() - 0.5D) * 0.1D;
            double vz = (world.rand.nextDouble() - 0.5D) * 0.1D;
            mc.theWorld.spawnParticle("portal", px, py, pz, vx, vy, vz);
        }
    }

    @SideOnly(Side.CLIENT)
    private static void sendMovePacket(int oldX, int oldY, int oldZ, int newX, int newY, int newZ) {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            int dimensionId = mc.theWorld.provider.dimensionId;
            PacketHandler.INSTANCE
                .sendToServer(new PacketFloatingPlaceMove(oldX, oldY, oldZ, newX, newY, newZ, dimensionId));
        } catch (Exception e) {
            NHToolbox.LOG.warn("Failed to send move packet", e);
        }
    }

    private static Block getBlockFromItem(ItemStack itemStack) {
        if (itemStack != null && itemStack.getItem() instanceof ItemBlock) {
            return ((ItemBlock) itemStack.getItem()).field_150939_a;
        }
        return null;
    }

    private static boolean isPlaceableItem(ItemStack itemStack) {
        return itemStack.getItem() instanceof ItemBlock || itemStack.getItem() instanceof net.minecraft.item.ItemSeeds
            || itemStack.getItem() instanceof net.minecraft.item.ItemReed;
    }
}
