package com.kkkxia.nhtoolbox.feature.floatingplace;

import com.kkkxia.nhtoolbox.client.keybinding.KeyBindingManager;
import com.kkkxia.nhtoolbox.core.config.ConfigManager;
import com.kkkxia.nhtoolbox.core.module.IFeatureModule;
import com.kkkxia.nhtoolbox.core.module.ModuleSide;
import com.kkkxia.nhtoolbox.feature.floatingplace.network.PacketFloatingPlace;
import com.kkkxia.nhtoolbox.feature.floatingplace.network.PacketFloatingPlaceMove;
import com.kkkxia.nhtoolbox.network.NetworkManager;

import cpw.mods.fml.common.eventhandler.EventBus;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 浮空放置模块：客户端负责输入/预览与预测放置，服务端负责实际放置与移动。
 * 因此模块加载侧为 COMMON（两端都要注册数据包），客户端专属方法用 @SideOnly(CLIENT) 隔离，
 * 服务端调用时回落到 {@link IFeatureModule} 的默认空实现。
 */
public class FloatingPlaceModule implements IFeatureModule {

    private static double rayTraceDistance = 1.0D;
    private static boolean particlesEnabled = true;
    private static boolean previewBoxEnabled = true;
    private static boolean consumeItemsInSurvival = true;
    private static NetworkManager network;

    @Override
    public String id() {
        return "floatingplace";
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public ModuleSide side() {
        return ModuleSide.COMMON;
    }

    @Override
    public void registerConfig(ConfigManager config) {
        rayTraceDistance = config.getDouble(
            "rayTraceDistance",
            ConfigManager.CATEGORY_GENERAL,
            1.0D,
            1.0D,
            20.0D,
            "Maximum distance for block placement ray trace\n浮空放置模式指示框距离玩家的距离");
        particlesEnabled = config.getBoolean(
            "enableParticles",
            ConfigManager.CATEGORY_GENERAL,
            true,
            "Enable particle effects when placing/moving blocks");
        previewBoxEnabled = config.getBoolean(
            "enablePreviewBox",
            ConfigManager.CATEGORY_GENERAL,
            true,
            "Enable wireframe preview box for placement position");
        consumeItemsInSurvival = config.getBoolean(
            "consumeItemsInSurvival",
            ConfigManager.CATEGORY_GENERAL,
            true,
            "Consume items from inventory when placing in survival mode");
    }

    @Override
    public void registerNetwork(NetworkManager network) {
        FloatingPlaceModule.network = network;
        network.registerPacket(PacketFloatingPlace.class, PacketFloatingPlace.Handler.class, Side.SERVER);
        network.registerPacket(PacketFloatingPlaceMove.class, PacketFloatingPlaceMove.Handler.class, Side.SERVER);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void registerEvents(EventBus forgeBus, EventBus fmlBus) {
        InputHandler inputHandler = new InputHandler();
        forgeBus.register(inputHandler);
        fmlBus.register(inputHandler);

        PlayerTickHandler tickHandler = new PlayerTickHandler();
        forgeBus.register(tickHandler);
        fmlBus.register(tickHandler);

        forgeBus.register(new FloatingPlaceRenderHandler());
        forgeBus.register(new FloatingPlaceInteractHandler());
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void registerKeyBindings(KeyBindingManager keyBindings) {
        keyBindings.register(FloatingPlaceKeys.TOGGLE);
    }

    static NetworkManager network() {
        return network;
    }

    static double rayTraceDistance() {
        return rayTraceDistance;
    }

    static boolean particlesEnabled() {
        return particlesEnabled;
    }

    static boolean previewBoxEnabled() {
        return previewBoxEnabled;
    }

    static boolean consumeItemsInSurvival() {
        return consumeItemsInSurvival;
    }
}
