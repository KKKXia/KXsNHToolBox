package com.KKKXia.NHToolbox.handler;

import java.lang.reflect.Field;
import java.util.List;

import net.minecraft.client.settings.KeyBinding;

import org.lwjgl.input.Keyboard;

import com.KKKXia.NHToolbox.NHToolbox;

public class KeyBindings {

    public static final KeyBinding TOGGLE_FLOATING_PLACE = new KeyBinding(
        "key.floating_place.toggle",
        Keyboard.KEY_G,
        "key.categories.nhtoolbox");

    /** 背包栏位锁定：默认鼠标中键（-100 左键 / -99 右键 / -98 中键，与原版 keyBindPickBlock 同值）。 */
    public static final KeyBinding LOCK_SLOT = new KeyBinding(
        "key.nhtoolbox.lock_slot",
        -98,
        "key.categories.nhtoolbox");

    /**
     * 修复与原版 keyBindPickBlock（-98 中键）的同码冲突。
     *
     * <p>
     * KeyBinding 的内部静态哈希表（keyCode -> KeyBinding）按 keybindArray 注册顺序
     * "后注册者覆盖"：LOCK_SLOT 构造于原版 GameSettings 之后、排在 keybindArray 末尾，
     * 会抢走 -98 条目，导致原版"选取方块"收不到按键事件
     * （Minecraft.runTick 第 2025/2047 行通过 keyBindPickBlock.isPressed() 消费）。
     * 把 LOCK_SLOT 挪到列表最前并重建哈希，让 keyBindPickBlock 重新持有 -98。
     * 本功能不依赖 LOCK_SLOT 的 pressTime（鼠标键直接比对按钮号），因此不受影响。
     */
    @SuppressWarnings("unchecked")
    public static void fixPickBlockCollision() {
        Field keybindArray = null;
        for (String name : new String[] { "keybindArray", "field_74516_a" }) {
            try {
                keybindArray = KeyBinding.class.getDeclaredField(name);
                keybindArray.setAccessible(true);
                break;
            } catch (NoSuchFieldException ignored) {
                // 尝试下一个名称
            }
        }
        if (keybindArray == null) {
            return;
        }
        try {
            List<KeyBinding> bindings = (List<KeyBinding>) keybindArray.get(null);
            bindings.remove(LOCK_SLOT);
            bindings.add(0, LOCK_SLOT);
            KeyBinding.resetKeyBindingArrayAndHash();
        } catch (Exception e) {
            NHToolbox.LOG.warn("修复 keyBindPickBlock 键位冲突失败，原版选取方块可能失效", e);
        }
    }
}
