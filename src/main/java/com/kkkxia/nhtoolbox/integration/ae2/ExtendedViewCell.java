package com.kkkxia.nhtoolbox.integration.ae2;

import static appeng.util.item.AEFluidStackType.FLUID_STACK_TYPE;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.AEApi;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.parts.IStorageBus;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.items.storage.ItemViewCell;
import appeng.tile.inventory.IAEStackInventory;
import appeng.util.Platform;

/**
 * 扩展显示元件 = AE2 原版显示元件物品 + NBT 标记，而不是新注册的物品。
 * 原因：AE2 终端侧边栏的 VIEW_CELL 插槽与 {@code ItemViewCell#createFilter} 都按精确物品实例/类型校验，
 * 新注册的 Item 类既放不进侧边栏，也不会被过滤逻辑识别。
 */
public final class ExtendedViewCell {

    private static final String TAG_KEY = "NHToolboxExtend";

    private ExtendedViewCell() {}

    /** AE2 原版显示元件的物品栈。 */
    public static ItemStack vanillaViewCell() {
        return AEApi.instance()
            .definitions()
            .items()
            .viewCell()
            .maybeStack(1)
            .orNull();
    }

    /** 创建带标记的扩展显示元件。 */
    public static ItemStack create() {
        ItemStack cell = vanillaViewCell();
        if (cell != null) {
            NBTTagCompound tag = cell.hasTagCompound() ? cell.getTagCompound() : new NBTTagCompound();
            tag.setBoolean(TAG_KEY, true);
            cell.setTagCompound(tag);
        }
        return cell;
    }

    /** 判断物品是否为扩展显示元件（AE2 显示元件 + 标记 NBT）。 */
    public static boolean isExtended(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemViewCell
            && stack.hasTagCompound()
            && stack.getTagCompound()
                .getBoolean(TAG_KEY);
    }

    /**
     * 若 {@code tile} 是线缆总线容器、且点击面或任一方向上有存储总线，则把总线配置栏的标记复制到元件配置栏。
     *
     * @return 是否已处理（调用方据此决定取消本次右键，避免存储总线随后打开 GUI）
     */
    public static boolean copyFromStorageBus(TileEntity tile, int side, ItemStack cellStack, EntityPlayer player) {
        if (!(tile instanceof IPartHost host)) {
            return false;
        }

        IStorageBus bus = findStorageBus(host, side);
        if (bus == null) {
            return false;
        }

        copyConfiguration(bus, cellStack, player);
        return true;
    }

    private static IStorageBus findStorageBus(IPartHost host, int side) {
        IPart part = host.getPart(ForgeDirection.getOrientation(side));
        if (part instanceof IStorageBus bus) {
            return bus;
        }

        for (ForgeDirection dir : ForgeDirection.values()) {
            IPart p = host.getPart(dir);
            if (p instanceof IStorageBus bus) {
                return bus;
            }
        }

        return null;
    }

    /** 把总线配置栏中的标记（物品或流体）复制到显示元件的配置栏；已存在的同类标记跳过，剩余格不足时提示。 */
    private static void copyConfiguration(IStorageBus bus, ItemStack cellStack, EntityPlayer player) {
        if (!(cellStack.getItem() instanceof ItemViewCell viewCell)) {
            return;
        }

        IAEStackInventory cellConfig = viewCell.getConfigAEInventory(cellStack);
        IAEStackInventory busConfig = bus.getAEInventoryByName(StorageName.CONFIG);
        if (busConfig == null) {
            return;
        }

        List<IAEStack<?>> toCopy = new ArrayList<>();
        // 流体存储总线配置栏中是原生 IAEFluidStack，且“流体包物品”形式的条目需规范化，
        // ItemViewCell.createFilter 生成的过滤列表才能匹配终端里的流体
        boolean fluidBus = bus.getStackType() == FLUID_STACK_TYPE;
        for (int i = 0; i < busConfig.getSizeInventory(); i++) {
            IAEStack<?> entry = busConfig.getAEStackInSlot(i);
            if (entry == null) {
                continue;
            }

            if (fluidBus && entry instanceof IAEItemStack ais) {
                IAEStack<?> converted = Platform.convertStackPacket(ais.getItemStack());
                if (converted instanceof IAEFluidStack) {
                    entry = converted;
                }
            }

            if (!containsType(cellConfig, entry)) {
                toCopy.add(entry);
            }
        }

        if (toCopy.isEmpty()) {
            player.addChatComponentMessage(new ChatComponentText(EnumChatFormatting.GRAY + "存储总线中没有可复制的标记"));
            return;
        }

        int emptySlots = countEmptySlots(cellConfig);
        if (toCopy.size() > emptySlots) {
            player.addChatComponentMessage(new ChatComponentText(EnumChatFormatting.RED + "空间不足"));
            return;
        }

        int slot = 0;
        for (IAEStack<?> entry : toCopy) {
            while (slot < cellConfig.getSizeInventory() && cellConfig.getAEStackInSlot(slot) != null) {
                slot++;
            }
            if (slot >= cellConfig.getSizeInventory()) {
                break;
            }
            cellConfig.putAEStackInSlot(slot, entry);
            slot++;
        }

        player.addChatComponentMessage(
            new ChatComponentText(
                EnumChatFormatting.GREEN + "已将 " + toCopy.size() + " 个标记复制到 " + cellStack.getDisplayName()));
    }

    private static boolean containsType(IAEStackInventory inventory, IAEStack<?> entry) {
        for (int i = 0; i < inventory.getSizeInventory(); i++) {
            IAEStack<?> slot = inventory.getAEStackInSlot(i);
            if (slot != null && slot.isSameType(entry)) {
                return true;
            }
        }
        return false;
    }

    private static int countEmptySlots(IAEStackInventory inventory) {
        int empty = 0;
        for (int i = 0; i < inventory.getSizeInventory(); i++) {
            if (inventory.getAEStackInSlot(i) == null) {
                empty++;
            }
        }
        return empty;
    }
}
