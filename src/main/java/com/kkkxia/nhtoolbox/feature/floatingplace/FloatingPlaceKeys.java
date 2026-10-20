package com.kkkxia.nhtoolbox.feature.floatingplace;

import net.minecraft.client.settings.KeyBinding;

import org.lwjgl.input.Keyboard;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** 浮空放置按键（默认 G）。 */
@SideOnly(Side.CLIENT)
final class FloatingPlaceKeys {

    static final KeyBinding TOGGLE = new KeyBinding(
        "key.floating_place.toggle",
        Keyboard.KEY_G,
        "key.categories.nhtoolbox");

    private FloatingPlaceKeys() {}
}
