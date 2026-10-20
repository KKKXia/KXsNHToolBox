package com.kkkxia.nhtoolbox.proxy;

import com.kkkxia.nhtoolbox.client.keybinding.KeyBindingManager;

import cpw.mods.fml.common.event.FMLPostInitializationEvent;

/** 客户端代理：提供按键管理器，并在模块完成按键注册后统一恢复键位、修复同码冲突。 */
public class ClientProxy extends CommonProxy {

    private final KeyBindingManager keyBindings = new KeyBindingManager();

    @Override
    protected boolean isClientSide() {
        return true;
    }

    @Override
    protected KeyBindingManager keyBindings() {
        return keyBindings;
    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
        super.postInit(event);
        keyBindings.finishRegistration();
    }
}
