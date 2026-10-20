package com.kkkxia.nhtoolbox.feature.floatingplace.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;

import com.kkkxia.nhtoolbox.NHToolbox;
import com.kkkxia.nhtoolbox.feature.floatingplace.BlockPlacementHelper;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** 客户端放置成功后通知服务端在对应位置执行实际放置。 */
public class PacketFloatingPlace implements IMessage {

    private int x;
    private int y;
    private int z;
    private int dimensionId;

    public PacketFloatingPlace() {}

    public PacketFloatingPlace(int x, int y, int z, int dimensionId) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.dimensionId = dimensionId;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.x = buf.readInt();
        this.y = buf.readInt();
        this.z = buf.readInt();
        this.dimensionId = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
        buf.writeInt(dimensionId);
    }

    public static class Handler implements IMessageHandler<PacketFloatingPlace, IMessage> {

        @Override
        public IMessage onMessage(PacketFloatingPlace message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            World world = player.worldObj;

            if (world.provider.dimensionId != message.dimensionId) {
                return null;
            }

            BlockPlacementHelper.placeBlockOnServer(world, player, message.x, message.y, message.z);

            NHToolbox.LOG.info("Server placed block at: " + message.x + ", " + message.y + ", " + message.z);
            return null;
        }
    }
}
