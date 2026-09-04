package com.KKKXia.NHToolbox.items;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.parts.IStorageBus;
import appeng.api.parts.SelectedPart;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEStack;
import appeng.core.CreativeTab;
import appeng.core.features.AEFeature;
import appeng.items.storage.ItemViewCell;
import appeng.tile.inventory.IAEStackInventory;
import appeng.util.Platform;
import cpw.mods.fml.common.registry.GameRegistry;

/**
 * 基于 {@link ItemViewCell} 的扩展显示元件。
 * <p>
 * 新增功能：手持本元件右键存储总线（物品存储总线，流体存储总线同样继承 {@link IStorageBus}）时，
 * 将存储总线配置栏中标记的全部物品/流体复制到本元件的配置栏中。
 * 若本元件剩余格不足，则在聊天栏提示“空间不足”。
 */
public class ExtendItemViewCell extends ItemViewCell {

    /** 注册后的单例实例 */
    public static ExtendItemViewCell INSTANCE;

    /** 未注册时调用，负责实例化并注册到物品注册表 */
    public static void init() {
        if (INSTANCE != null) {
            return;
        }
        INSTANCE = new ExtendItemViewCell();
        INSTANCE.setUnlocalizedName("extendItemViewCell");
        // 直接复用 AE2 已验证可正常加载的显示元件贴图，避免自建资源域在本模组环境下加载失败（紫黑块问题）
        INSTANCE.setTextureName("appliedenergistics2:ItemViewCell");
        INSTANCE.setCreativeTab(CreativeTab.instance != null ? CreativeTab.instance : CreativeTabs.tabMisc);
        GameRegistry.registerItem(INSTANCE, "ExtendItemViewCell");
    }

    public ExtendItemViewCell() {
        super();
    }

    /**
     * {@link ItemViewCell} 的构造器会调用 {@code setFeature(...)}，而该方法内部会访问
     * {@code AEConfig.instance.isFeatureEnabled(...)}。若本模组的 preInit 早于 AE2 的 preInit
     * （本模组未声明依赖时的默认排序下容易发生），AEConfig 尚未初始化，直接抛出 NullPointerException。
     * <p>
     * 本物品由 NHToolbox 自行通过 {@link GameRegistry} 注册，并不使用 AE2 的特性系统，
     * 因此这里将 {@code setFeature} 覆写为空实现，避免构造阶段依赖 AE2 的配置加载顺序。
     */
    @Override
    public void setFeature(final EnumSet<AEFeature> features) {
        // 不需要 AE2 的特性注册，见类注释。
    }

    /**
     * Forge 提供的钩子，在方块激活（如存储总线打开 GUI）之前被调用。
     * 在 1.7.10 中其执行时机早于 {@code Block.onBlockActivated}，
     * 因此可以拦截右键事件，避免存储总线 GUI 干扰复制操作。
     */
    @Override
    public boolean onItemUseFirst(final ItemStack stack, final EntityPlayer player, final World world, final int x,
        final int y, final int z, final int side, final float hitX, final float hitY, final float hitZ) {
        if (Platform.isClient()) {
            return false;
        }

        final TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof IPartHost host)) {
            return false;
        }

        final IStorageBus bus = findStorageBus(host, side, hitX, hitY, hitZ);
        if (bus == null) {
            return false;
        }

        copyConfiguration(bus, this.getConfigAEInventory(stack), player, stack);
        // 右键已被本元件处理，阻止存储总线后续打开 GUI
        return true;
    }

    /**
     * 从电缆总线容器中查找被点击的存储总线部件。
     * <p>
     * 优先使用射线命中的精确位置，失败时回退到点击的方块面，最后遍历所有方向。
     */
    private IStorageBus findStorageBus(final IPartHost host, final int side, final float hitX, final float hitY,
        final float hitZ) {
        if (hitX >= 0.0F && hitX <= 1.0F && hitY >= 0.0F && hitY <= 1.0F && hitZ >= 0.0F && hitZ <= 1.0F) {
            final SelectedPart selected = host.selectPart(Vec3.createVectorHelper(hitX, hitY, hitZ));
            if (selected != null && selected.part instanceof IStorageBus bus) {
                return bus;
            }
        }

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
    private void copyConfiguration(final IStorageBus bus, final IAEStackInventory cellConfig, final EntityPlayer player,
        final ItemStack cellStack) {
        final IAEStackInventory busConfig = bus.getAEInventoryByName(StorageName.CONFIG);
        if (busConfig == null) {
            return;
        }

        // 收集需要新增的标记（跳过已经存在于元件中的同类标记）
        final List<IAEStack<?>> toCopy = new ArrayList<>();
        for (int i = 0; i < busConfig.getSizeInventory(); i++) {
            final IAEStack<?> entry = busConfig.getAEStackInSlot(i);
            // 只复制以物品形式存储的标记（流体存储总线中的流体以流体包 ItemFluidPacket 形式存储，同样属于物品栈）
            if (entry != null && entry.isItem() && !containsType(cellConfig, entry)) {
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

        player.addChatComponentMessage(
            new ChatComponentText(
                EnumChatFormatting.GREEN + "已将 " + toCopy.size() + " 个标记复制到 " + cellStack.getDisplayName()));
    }

    /** 判断元件配置栏中是否已存在同类标记 */
    private boolean containsType(final IAEStackInventory inventory, final IAEStack<?> entry) {
        for (int i = 0; i < inventory.getSizeInventory(); i++) {
            final IAEStack<?> slot = inventory.getAEStackInSlot(i);
            if (slot != null && slot.isSameType(entry)) {
                return true;
            }
        }
        return false;
    }

    /** 统计配置栏中的空格数量 */
    private int countEmptySlots(final IAEStackInventory inventory) {
        int empty = 0;
        for (int i = 0; i < inventory.getSizeInventory(); i++) {
            if (inventory.getAEStackInSlot(i) == null) {
                empty++;
            }
        }
        return empty;
    }
}
