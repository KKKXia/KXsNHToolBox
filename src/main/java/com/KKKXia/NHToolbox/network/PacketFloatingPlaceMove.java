package com.KKKXia.NHToolbox.network;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

import com.KKKXia.NHToolbox.NHToolbox;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class PacketFloatingPlaceMove implements IMessage {

    private int oldX;
    private int oldY;
    private int oldZ;
    private int newX;
    private int newY;
    private int newZ;
    private int dimensionId;

    public PacketFloatingPlaceMove() {}

    public PacketFloatingPlaceMove(int oldX, int oldY, int oldZ, int newX, int newY, int newZ, int dimensionId) {
        this.oldX = oldX;
        this.oldY = oldY;
        this.oldZ = oldZ;
        this.newX = newX;
        this.newY = newY;
        this.newZ = newZ;
        this.dimensionId = dimensionId;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.oldX = buf.readInt();
        this.oldY = buf.readInt();
        this.oldZ = buf.readInt();
        this.newX = buf.readInt();
        this.newY = buf.readInt();
        this.newZ = buf.readInt();
        this.dimensionId = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(oldX);
        buf.writeInt(oldY);
        buf.writeInt(oldZ);
        buf.writeInt(newX);
        buf.writeInt(newY);
        buf.writeInt(newZ);
        buf.writeInt(dimensionId);
    }

    public static class Handler implements IMessageHandler<PacketFloatingPlaceMove, IMessage> {

        @Override
        public IMessage onMessage(PacketFloatingPlaceMove message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            World world = player.worldObj;

            if (world.provider.dimensionId != message.dimensionId) {
                return null;
            }

            int oldX = message.oldX, oldY = message.oldY, oldZ = message.oldZ;
            int newX = message.newX, newY = message.newY, newZ = message.newZ;

            if (oldX == newX && oldY == newY && oldZ == newZ) {
                return null;
            }

            Block oldBlock = world.getBlock(oldX, oldY, oldZ);
            if (oldBlock == null || oldBlock == Blocks.air) {
                return null;
            }

            if (newY < 0 || newY >= world.getHeight()) {
                return null;
            }

            if (!world.isAirBlock(newX, newY, newZ) && !world.getBlock(newX, newY, newZ)
                .isReplaceable(world, newX, newY, newZ)) {
                return null;
            }

            if (!player.canPlayerEdit(oldX, oldY, oldZ, 0, player.getHeldItem())
                || !player.canPlayerEdit(newX, newY, newZ, 0, player.getHeldItem())) {
                return null;
            }

            AxisAlignedBB aabb = oldBlock.getCollisionBoundingBoxFromPool(world, newX, newY, newZ);
            if (aabb != null) {
                aabb = aabb.offset(newX, newY, newZ);
                if (!world.getCollidingBoundingBoxes(player, aabb)
                    .isEmpty()) {
                    return null;
                }
            }

            int oldMetadata = world.getBlockMetadata(oldX, oldY, oldZ);

            NBTTagCompound tileNbt = null;
            TileEntity oldTile = world.getTileEntity(oldX, oldY, oldZ);
            if (oldTile != null) {
                tileNbt = new NBTTagCompound();
                oldTile.writeToNBT(tileNbt);
            }

            world.setBlockToAir(oldX, oldY, oldZ);
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

            world
                .playSoundEffect(newX + 0.5D, newY + 0.5D, newZ + 0.5D, oldBlock.stepSound.func_150496_b(), 1.0F, 0.8F);

            NHToolbox.LOG.info(
                "Server moved block from (" + oldX
                    + ","
                    + oldY
                    + ","
                    + oldZ
                    + ") to ("
                    + newX
                    + ","
                    + newY
                    + ","
                    + newZ
                    + ")");

            return null;
        }
    }
}
