package com.kkkxia.nhtoolbox.network;

import com.kkkxia.nhtoolbox.NHToolbox;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

/** 网络层唯一入口：整个模组共用一个通道（通道名 = modid），数据包 id 按注册顺序自增分配。 */
public final class NetworkManager {

    private final SimpleNetworkWrapper channel = NetworkRegistry.INSTANCE.newSimpleChannel(NHToolbox.MODID);
    private int nextPacketId;

    public <REQ extends IMessage, REPLY extends IMessage> void registerPacket(
        Class<REQ> packetClass,
        Class<? extends IMessageHandler<REQ, REPLY>> handlerClass,
        Side side) {
        channel.registerMessage(handlerClass, packetClass, nextPacketId++, side);
    }

    /** 底层通道，供模块发送数据包。 */
    public SimpleNetworkWrapper channel() {
        return channel;
    }
}
