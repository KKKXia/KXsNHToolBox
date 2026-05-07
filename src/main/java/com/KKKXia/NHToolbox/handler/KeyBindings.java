package com.KKKXia.NHToolbox.handler;

import net.minecraft.client.settings.KeyBinding;

import org.lwjgl.input.Keyboard;

public class KeyBindings {

    public static final KeyBinding TOGGLE_FLOATING_PLACE = new KeyBinding(
        "key.floating_place.toggle",
        Keyboard.KEY_G,
        "key.categories.nhtoolbox");

    public static final KeyBinding[] ALL_KEY_BINDINGS = { TOGGLE_FLOATING_PLACE };
}
