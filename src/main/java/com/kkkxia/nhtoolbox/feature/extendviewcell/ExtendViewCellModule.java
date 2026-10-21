package com.kkkxia.nhtoolbox.feature.extendviewcell;

import net.minecraft.item.ItemStack;

import com.kkkxia.nhtoolbox.core.module.IFeatureModule;
import com.kkkxia.nhtoolbox.core.module.ModuleSide;
import com.kkkxia.nhtoolbox.integration.ae2.ExtendedViewCell;

import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.eventhandler.EventBus;
import gregtech.api.util.GTModHandler;

/** 扩展显示元件模块：显示元件 + 存储总线无序合成，右键存储总线可复制其标记。 */
public class ExtendViewCellModule implements IFeatureModule {

    @Override
    public String id() {
        return "extendviewcell";
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
    public void registerEvents(EventBus forgeBus, EventBus fmlBus) {
        forgeBus.register(new ExtendViewCellHandler());
    }

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        ItemStack viewCell = ExtendedViewCell.vanillaViewCell();
        if (viewCell != null) {
            GTModHandler.addShapelessCraftingRecipe(ExtendedViewCell.create(), new Object[] { viewCell });
        }
    }
}
