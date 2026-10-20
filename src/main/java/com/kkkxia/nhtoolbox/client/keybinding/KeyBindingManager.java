package com.kkkxia.nhtoolbox.client.keybinding;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;

import com.kkkxia.nhtoolbox.NHToolbox;

import cpw.mods.fml.client.registry.ClientRegistry;

/** 模组按键的唯一入口：登记按键，并在全部注册完成后统一恢复键位、修复与原版按键的同码冲突。 */
public final class KeyBindingManager {

    private final List<KeyBinding> registered = new ArrayList<>();

    /** 登记一个模组按键（加入 Forge 按键表与本模组清单）。 */
    public void register(KeyBinding binding) {
        ClientRegistry.registerKeyBinding(binding);
        registered.add(binding);
    }

    /** 全部模块完成按键注册后调用一次。 */
    public void finishRegistration() {
        if (registered.isEmpty()) {
            return;
        }
        for (KeyBinding binding : registered) {
            applySavedKeyCode(binding);
        }
        moveModBindingsToFront();
    }

    // 1.7.10 的 GameSettings.loadOptions() 早于 mod 按键注册执行，options.txt 中保存的自定义键位不会被自动加载
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
                        int saved = Integer.parseInt(line.substring(prefix.length()).trim());
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

    // KeyBinding 构造器会把自身写入原版键表，同键码"后写入者覆盖"；把 mod 按键挪到最前，
    // 让原版按键（如中键 -98 的选取方块）重新持有原键码
    @SuppressWarnings("unchecked")
    private void moveModBindingsToFront() {
        Field keybindArray = null;
        for (String name : new String[] { "keybindArray", "field_74516_a" }) {
            try {
                keybindArray = KeyBinding.class.getDeclaredField(name);
                keybindArray.setAccessible(true);
                break;
            } catch (NoSuchFieldException ignored) {
                // 开发环境为 keybindArray，混淆环境为 srg 名，逐个尝试
            }
        }
        if (keybindArray == null) {
            return;
        }
        try {
            List<KeyBinding> bindings = (List<KeyBinding>) keybindArray.get(null);
            bindings.removeAll(registered);
            bindings.addAll(0, registered);
            KeyBinding.resetKeyBindingArrayAndHash();
        } catch (Exception e) {
            NHToolbox.LOG.warn("修复模组按键与原版按键的同码冲突失败", e);
        }
    }
}
