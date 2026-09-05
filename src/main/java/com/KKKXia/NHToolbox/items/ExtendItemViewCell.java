package com.KKKXia.NHToolbox.items;

import static appeng.util.item.AEFluidStackType.FLUID_STACK_TYPE;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.Action;

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
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.registry.GameRegistry;

/**
 * 扩展显示元件 —— 实现为“AE2 原版显示元件（{@link ItemViewCell}）物品 + NBT 标记”的变体，
 * 而非独立注册的新物品。
 * <p>
 * 原因：AE2 终端侧边栏的 VIEW_CELL 插槽使用 <b>精确物品实例</b> 校验
 * （{@code items.viewCell().isSameAs(stack)}，内部比较 Item 实例），
 * 且过滤逻辑 {@link ItemViewCell#createFilter} 使用 {@code instanceof ItemViewCell} 判断。
 * 因此任何独立注册的新 Item 类（无论是否继承 ItemViewCell，还是直接实现 ICellWorkbenchItem）
 * 都既无法放入侧边栏，也不会被过滤逻辑识别。
 * <p>
 * 本方案下：放入侧边栏、物品/流体过滤、Cell Workbench 兼容等全部走 AE2 原版路径（天然可用）；
 * 新增功能（手持本元件右键存储总线，将总线配置栏中的物品/流体全部复制到元件配置栏，
 * 空间不足时聊天栏提示“空间不足”）通过 {@link PlayerInteractEvent} 实现。
 */
public final class ExtendItemViewCell {

    /** 扩展元件的 NBT 标记键 */
    private static final String TAG_KEY = "NHToolboxExtend";

    private ExtendItemViewCell() {}

    /** 初始化：注册事件处理器与合成配方（显示元件 + 存储总线 = 扩展显示元件） */
    public static void init() {
        MinecraftForge.EVENT_BUS.register(new Handler());

        final ItemStack viewCell = AEApi.instance().definitions().items().viewCell().maybeStack(1).orNull();
        final ItemStack storageBus = AEApi.instance().definitions().parts().storageBus().maybeStack(1).orNull();
        if (viewCell != null && storageBus != null) {
            GameRegistry.addShapelessRecipe(createExtendedViewCell(), viewCell, storageBus);
        }
    }

    /** 创建带标记的扩展显示元件 */
    public static ItemStack createExtendedViewCell() {
        final ItemStack cell = AEApi.instance().definitions().items().viewCell().maybeStack(1).orNull();
        if (cell != null) {
            final NBTTagCompound tag = cell.hasTagCompound() ? cell.getTagCompound() : new NBTTagCompound();
            tag.setBoolean(TAG_KEY, true);
            cell.setTagCompound(tag);
        }
        return cell;
    }

    /** 判断物品是否为扩展显示元件（AE2 显示元件 + 标记 NBT） */
    public static boolean isExtendedViewCell(final ItemStack stack) {
        return stack != null
                && stack.getItem() instanceof ItemViewCell
                && stack.hasTagCompound()
                && stack.getTagCompound().getBoolean(TAG_KEY);
    }

    /**
     * 事件处理器：
     * <ul>
     * <li>{@link PlayerInteractEvent}：手持扩展显示元件右键存储总线时，复制总线配置栏标记到元件，
     * 并取消事件以阻止存储总线 GUI 打开。</li>
     * <li>{@link ItemTooltipEvent}：为扩展显示元件添加说明信息。</li>
     * </ul>
     */
    public static final class Handler {

        @SubscribeEvent
        public void onPlayerInteract(final PlayerInteractEvent event) {
            if (event.action != Action.RIGHT_CLICK_BLOCK || event.world.isRemote) {
                return;
            }

            final ItemStack held = event.entityPlayer.getHeldItem();
            if (!isExtendedViewCell(held)) {
                return;
            }

            final TileEntity te = event.world.getTileEntity(event.x, event.y, event.z);
            if (!(te instanceof IPartHost host)) {
                return;
            }

            final IStorageBus bus = findStorageBus(host, event.face);
            if (bus == null) {
                return;
            }

            copyConfiguration(bus, held, event.entityPlayer);
            // 本次右键已被本元件处理，阻止存储总线随后打开 GUI
            event.setCanceled(true);
        }

        @SubscribeEvent
        public void onItemTooltip(final ItemTooltipEvent event) {
            if (isExtendedViewCell(event.itemStack)) {
                event.toolTip.add(
                        EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal("nhtoolbox.extendViewCell.lore"));
            }
        }
    }

    /** 从ME线缆总线容器中按点击面查找存储总线部件，找不到则遍历所有方向 */
    private static IStorageBus findStorageBus(final IPartHost host, final int side) {
        final IPart part = host.getPart(ForgeDirection.getOrientation(side));
        if (part instanceof IStorageBus bus) {
            return bus;
        }

        for (final ForgeDirection dir : ForgeDirection.values()) {
            final IPart p = host.getPart(dir);
            if (p instanceof IStorageBus bus) {
                return bus;
            }
        }

        return null;
    }

    /**
     * 将存储总线配置栏中的标记（物品或流体包）复制到显示元件的配置栏。
     * 已存在的同类标记不会重复占用格位；剩余格不足时提示“空间不足”。
     */
    private static void copyConfiguration(final IStorageBus bus, final ItemStack cellStack, final EntityPlayer player) {
        if (!(cellStack.getItem() instanceof ItemViewCell viewCell)) {
            return;
        }

        final IAEStackInventory cellConfig = viewCell.getConfigAEInventory(cellStack);
        final IAEStackInventory busConfig = bus.getAEInventoryByName(StorageName.CONFIG);
        if (busConfig == null) {
            return;
        }

        // 收集需要新增的标记（跳过已经存在于元件中的同类标记）
        final List<IAEStack<?>> toCopy = new ArrayList<>();
        // 流体存储总线（PartFluidStorageBus，继承 PartStorageBus 并返回 FLUID_STACK_TYPE）的
        // 配置栏中，流体的标记形式是原生 IAEFluidStack（NBT 载入时由流体包转换而来），
        // 因此复制时不能只接受物品栈；同时把可能以“流体包物品”形式存在的条目规范化为原生流体栈，
        // 这样 ItemViewCell.createFilter 生成的过滤列表才能正确匹配终端中的流体条目。
        final boolean fluidBus = bus.getStackType() == FLUID_STACK_TYPE;
        for (int i = 0; i < busConfig.getSizeInventory(); i++) {
            IAEStack<?> entry = busConfig.getAEStackInSlot(i);
            if (entry == null) {
                continue;
            }

            if (fluidBus && entry instanceof IAEItemStack ais) {
                final IAEStack<?> converted = Platform.convertStackPacket(ais.getItemStack());
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

        final int emptySlots = countEmptySlots(cellConfig);
        if (toCopy.size() > emptySlots) {
            player.addChatComponentMessage(new ChatComponentText(EnumChatFormatting.RED + "空间不足"));
            return;
        }

        int slot = 0;
        for (final IAEStack<?> entry : toCopy) {
            while (slot < cellConfig.getSizeInventory() && cellConfig.getAEStackInSlot(slot) != null) {
                slot++;
            }
            if (slot >= cellConfig.getSizeInventory()) {
                break;
            }
            cellConfig.putAEStackInSlot(slot, entry);
            slot++;
        }

        player.addChatComponentMessage(new ChatComponentText(
                EnumChatFormatting.GREEN + "已将 " + toCopy.size() + " 个标记复制到 " + cellStack.getDisplayName()));
    }

    /** 判断元件配置栏中是否已存在同类标记 */
    private static boolean containsType(final IAEStackInventory inventory, final IAEStack<?> entry) {
        for (int i = 0; i < inventory.getSizeInventory(); i++) {
            final IAEStack<?> slot = inventory.getAEStackInSlot(i);
            if (slot != null && slot.isSameType(entry)) {
                return true;
            }
        }
        return false;
    }

    /** 统计配置栏中的空格数量 */
    private static int countEmptySlots(final IAEStackInventory inventory) {
        int empty = 0;
        for (int i = 0; i < inventory.getSizeInventory(); i++) {
            if (inventory.getAEStackInSlot(i) == null) {
                empty++;
            }
        }
        return empty;
    }
}
