package com.KKKXia.NHToolbox.network;

import com.KKKXia.NHToolbox.NHToolbox;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

public class PacketHandler {

    public static final SimpleNetworkWrapper INSTANCE = NetworkRegistry.INSTANCE.newSimpleChannel(NHToolbox.MODID);
    private static int nextPacketId = 0;

    public static void init() {
        INSTANCE
            .registerMessage(PacketFloatingPlace.Handler.class, PacketFloatingPlace.class, nextPacketId++, Side.SERVER);
    }
}
