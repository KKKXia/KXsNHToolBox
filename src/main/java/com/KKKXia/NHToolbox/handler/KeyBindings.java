package com.KKKXia.NHToolbox.handler;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Field;
import java.util.List;

import net.minecraft.client.Minecraft;
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
     * 恢复本模组按键在 options.txt 中保存的自定义键位。
     *
     * <p>
     * 1.7.10 的经典缺陷：GameSettings.loadOptions() 在 Minecraft 构造时运行，
     * 此时 mod 按键尚未注册（ClientRegistry.registerKeyBinding 在 postInit 才执行），
     * 因此 options.txt 里保存的 mod 按键值永远不会被自动加载，每次启动都回落默认键
     * （症状：在控制界面改键后重启，改动的键失效）。这里在注册后自行读取并应用
     * （键名格式：key_ + KeyBinding 描述，如 key_key.nhtoolbox.lock_slot:37）。
     */
    public static void loadSavedBindings() {
        applySavedKeyCode(TOGGLE_FLOATING_PLACE);
        applySavedKeyCode(LOCK_SLOT);
    }

    private static void applySavedKeyCode(KeyBinding binding) {
        try {
            File options = new File(Minecraft.getMinecraft().mcDataDir, "options.txt");
            if (!options.isFile()) {
                return;
            }
            String prefix = "key_" + binding.getKeyDescription() + ":";
            try (BufferedReader reader = new BufferedReader(new FileReader(options))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith(prefix)) {
                        int saved = Integer.parseInt(
                            line.substring(prefix.length())
                                .trim());
                        if (saved != binding.getKeyCode() && saved != binding.getKeyCodeDefault()) {
                            binding.setKeyCode(saved);
                            NHToolbox.LOG.info("从 options.txt 恢复按键 {} -> {}", binding.getKeyDescription(), saved);
                        }
                        return;
                    }
                }
            }
        } catch (Exception e) {
            NHToolbox.LOG.warn("读取 options.txt 中 {} 的保存键位失败", binding.getKeyDescription(), e);
        }
    }

    /**
     * 修复与原版 keyBindPickBlock（默认 -98 中键）的同码冲突。
     *
     * <p>
     * KeyBinding 的内部静态哈希表（keyCode -> KeyBinding）按 keybindArray 注册顺序
     * "后注册者覆盖"：LOCK_SLOT 构造于原版 GameSettings 之后、排在 keybindArray 末尾，
     * 会抢走 -98 条目，导致原版"选取方块"收不到按键事件
     * （Minecraft.runTick 第 2025/2047 行通过 keyBindPickBlock.isPressed() 消费）。
     * 把 LOCK_SLOT 挪到列表最前并重建哈希，让 keyBindPickBlock 重新持有 -98。
     * 本功能不依赖 LOCK_SLOT 的 pressTime（鼠标键直接比对按钮号、键盘键直接比对
     * Keyboard.getEventKey()），因此不受影响。
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
