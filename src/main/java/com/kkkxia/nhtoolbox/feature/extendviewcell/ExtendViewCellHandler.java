package com.kkkxia.nhtoolbox.feature.extendviewcell;

import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import com.kkkxia.nhtoolbox.integration.ae2.ExtendedViewCell;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** 手持扩展显示元件右键存储总线时复制标记；并为其补一行物品说明。 */
public class ExtendViewCellHandler {

    @SubscribeEvent
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK || event.world.isRemote) {
            return;
        }

        ItemStack held = event.entityPlayer.getHeldItem();
        if (!ExtendedViewCell.isExtended(held)) {
            return;
        }

        TileEntity tile = event.world.getTileEntity(event.x, event.y, event.z);
        if (!ExtendedViewCell.copyFromStorageBus(tile, event.face, held, event.entityPlayer)) {
            return;
        }

        // 本次右键已被本元件处理，阻止存储总线随后打开 GUI
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onItemTooltip(ItemTooltipEvent event) {
        if (ExtendedViewCell.isExtended(event.itemStack)) {
            event.toolTip
                .add(EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal("nhtoolbox.extendViewCell.lore"));
        }
    }
}
