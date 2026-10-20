package com.kkkxia.nhtoolbox.core.module;

import java.util.ArrayList;
import java.util.List;

import com.kkkxia.nhtoolbox.feature.extendviewcell.ExtendViewCellModule;
import com.kkkxia.nhtoolbox.feature.floatingplace.FloatingPlaceModule;
import com.kkkxia.nhtoolbox.feature.slotlock.SlotLockModule;

/** 功能模块的唯一注册点：新增功能时在此按加载顺序登记（显式清单，不做扫描 / 反射）。 */
public final class ModuleRegistry {

    private ModuleRegistry() {}

    public static List<IFeatureModule> modules() {
        List<IFeatureModule> modules = new ArrayList<>();
        modules.add(new FloatingPlaceModule());
        modules.add(new ExtendViewCellModule());
        modules.add(new SlotLockModule());
        return modules;
    }
}
