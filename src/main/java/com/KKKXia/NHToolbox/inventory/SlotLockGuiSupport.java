package com.KKKXia.NHToolbox.inventory;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import com.KKKXia.NHToolbox.NHToolbox;
import com.KKKXia.NHToolbox.config.ModConfig;
import com.KKKXia.NHToolbox.handler.KeyBindings;

/**
 * 背包栏位锁定的界面层支持工具。
 *
 * <p>
 * 本类只提供纯静态判定/渲染逻辑；实际的事件注入位于
 * {@code MixinGuiContainerLockInput}（输入）与 {@code MixinGuiContainerLockOverlay}（渲染），
 * 它们都挂在 {@code GuiContainer} 上，因此对背包、箱子、熔炉、AE 终端等所有容器界面一致生效。
 * 注入方负责提供 guiLeft/guiTop 与鼠标坐标（都是 GuiContainer 的 protected 字段，无需反射）。
 *
 * <p>
 * 渲染必须发生在 {@code drawGuiContainerForegroundLayer} 调用之后、手持物品与 tooltip 之前：
 * 原版绘制顺序为槽位物品(GuiContainer.drawScreen:114) -> 前景层(134) -> 手持/拖拽物品 -> tooltip(186)，
 * 画在这个位置才能既盖住物品又不遮挡 tooltip（tooltip 自身还会关闭深度测试）。
 */
public final class SlotLockGuiSupport {

    private static final RenderItem RENDER_ITEM = new RenderItem();

    /** 边框颜色：空锁定 = 蓝色，类型锁定 = 绿色。 */
    private static final int COLOR_EMPTY_LOCK = 0xFF40B0FF;
    private static final int COLOR_TYPE_LOCK = 0xFF40FF80;
    /** 残影遮罩：槽位底色 #8B8B8B、alpha 0x50（80/255 ≈ 31%，数值越小残影越淡）。 */
    private static final int GHOST_OVERLAY_COLOR = 0x508B8B8B;

    /** 边框深度：与原版 Gui.drawRect 一致（z=0）。 */
    private static final double BORDER_Z = 0.0D;
    /**
     * 残影遮罩深度。前景层内深度测试是开启的（由槽位物品绘制打开，GuiContainer.java:288），
     * 必须画得比物品图标更近才不会被剔除：槽位物品 z=100（func_146977_a 设 zLevel=100），
     * 这里取 200；tooltip 绘制时会关闭深度测试，所以不会互相遮挡。
     */
    private static final double GHOST_OVERLAY_Z = 200.0D;

    /**
     * 本帧需要绘制的锁定槽 / 残影槽。静态复用避免逐帧分配，
     * 仅由客户端渲染线程访问（renderLocks 不可重入）。
     */
    private static final List<Slot> LOCKED_SLOTS = new ArrayList<Slot>(SlotLockManager.SLOT_COUNT);
    private static final List<Slot> GHOST_SLOTS = new ArrayList<Slot>(SlotLockManager.SLOT_COUNT);

    private SlotLockGuiSupport() {}

    private static Minecraft mc() {
        return Minecraft.getMinecraft();
    }

    /**
     * 功能是否启用（配置开关）。
     *
     * <p>
     * 输入入口都要检查：mixin 是常驻的，功能关闭时如果还允许"切换锁定"，
     * 玩家中键点一下就会写出锁定数据、破坏"一键开关"的语义。
     */
    private static boolean enabled() {
        return ModConfig.isSlotLockEnabled();
    }

    // =====================================================================
    // 键位判定（沿用"直接比对键码"策略，不依赖 KeyBinding 内部哈希表）
    // =====================================================================
    public static boolean isLockMouseButton(int button) {
        int keyCode = KeyBindings.getLockSlotKeyCode();
        return keyCode < 0 && button == keyCode + 100;
    }

    public static boolean isLockKeyCode(int keyCode) {
        int lockKey = KeyBindings.getLockSlotKeyCode();
        return lockKey >= 0 && keyCode == lockKey;
    }

    // =====================================================================
    // 鼠标坐标（GUI 缩放坐标，与原版 GuiScreen.handleMouseInput:331-332 同一算法）
    // =====================================================================
    public static int mouseX(GuiScreen gui) {
        return Mouse.getX() * gui.width / mc().displayWidth;
    }

    public static int mouseY(GuiScreen gui) {
        return gui.height - Mouse.getY() * gui.height / mc().displayHeight - 1;
    }

    // =====================================================================
    // 槽位解析
    // =====================================================================
    /**
     * 槽位 -> 锁定键；不属于可锁定栏位（护甲、合成格、箱子格、幻影格……）时返回 -1。
     *
     * <p>
     * 直接复用 {@link SlotLockMergeRules#lockKey}：那里同时处理玩家背包栏位（0-35）与
     * 背包模组的背包格（{@code SlotBackpack} -> 1000+），并且刻意不依赖
     * {@code Minecraft.thePlayer}，这样客户端渲染与服务端/客户端容器计算出的键完全一致。
     */
    public static int lockKey(Slot slot) {
        return SlotLockMergeRules.lockKey(slot);
    }

    /** 命中判定：GUI 缩放坐标（与原版 mouseClicked 同一坐标系）。 */
    public static Slot slotAt(GuiContainer gui, int mouseX, int mouseY, int guiLeft, int guiTop) {
        for (Slot slot : slotsOf(gui)) {
            if (lockKey(slot) < 0) {
                continue;
            }
            int x = guiLeft + slot.xDisplayPosition;
            int y = guiTop + slot.yDisplayPosition;
            if (mouseX >= x - 1 && mouseX < x + 17 && mouseY >= y - 1 && mouseY < y + 17) {
                return slot;
            }
        }
        return null;
    }

    // =====================================================================
    // 鼠标按下判定（子类 mouseClicked 在调用 super 之前使用）
    // =====================================================================
    public static boolean handleMouseClick(GuiContainer gui, int mouseX, int mouseY, int mouseButton, int guiLeft,
        int guiTop) {
        if (!enabled()) {
            return false;
        }
        if (isLockMouseButton(mouseButton)) {
            Slot slot = slotAt(gui, mouseX, mouseY, guiLeft, guiTop);
            if (slot != null) {
                toggleAt(slot);
                return true; // 吞掉锁定键，阻止原版的中键行为（clickType 3 / 拖拽）
            }
            return false;
        }
        if (mouseButton != 0 && mouseButton != 1) {
            return false;
        }
        // Shift 点击是"取出/快速转移"，不会往该栏位里放东西，放行
        if (isShiftDown()) {
            return false;
        }
        ItemStack held = heldItem();
        if (held == null) {
            return false;
        }
        // 放置类：手中有物品且目标锁定栏位不兼容时吞掉（阻止直接放入/开始拖拽）
        Slot slot = slotAt(gui, mouseX, mouseY, guiLeft, guiTop);
        return slot != null && !canAccept(slot, held);
    }

    /**
     * 鼠标拖拽经过/松开在锁定槽上，且手上物品不兼容时返回 true。
     * 子类的 mouseClickMove 与 mouseMovedOrUp 共用这一判定。
     */
    public static boolean isHeldItemBlockedAt(GuiContainer gui, int mouseX, int mouseY, int guiLeft, int guiTop) {
        if (!enabled()) {
            return false;
        }
        ItemStack held = heldItem();
        if (held == null) {
            return false;
        }
        Slot slot = slotAt(gui, mouseX, mouseY, guiLeft, guiTop);
        return slot != null && !canAccept(slot, held);
    }

    /**
     * 数字键 1-9 是否应当被吞掉。
     *
     * <p>
     * 原版对悬停栏位按数字键会交换快捷栏对应格（GuiContainer.keyTyped:689 ->
     * checkHotbarKeys:707 -> clickType 2），{@code Container.slotClick:403-441}
     * 在"目标栏位有物品"和"目标栏位为空"两种情况下都可能把快捷栏那一格的物品放进目标栏位。
     * 两个方向都要守：目标栏位拒绝该物品，或悬停栏位的物品会被交换进一个拒绝它的锁定快捷栏。
     */
    public static boolean isHotbarSwapBlocked(GuiContainer gui, int keyCode, int mouseX, int mouseY, int guiLeft,
        int guiTop) {
        if (!enabled()) {
            return false;
        }
        int hotbarIndex = hotbarIndexFor(keyCode);
        if (hotbarIndex < 0) {
            return false;
        }
        Slot slot = slotAt(gui, mouseX, mouseY, guiLeft, guiTop);
        int slotIndex = slot == null ? -1 : lockKey(slot);
        if (slotIndex < 0) {
            return false;
        }
        // 快捷栏 0-8 的背包索引就是 0-8
        ItemStack hotbarStack = mc().thePlayer.inventory.getStackInSlot(hotbarIndex);
        if (hotbarStack != null && !canAccept(slotIndex, hotbarStack)) {
            return true;
        }
        ItemStack hoveredStack = slot.getStack();
        return hoveredStack != null && !canAccept(hotbarIndex, hoveredStack);
    }

    /** 返回该键码对应的快捷栏下标（0-8），不是快捷栏键则返回 -1。 */
    private static int hotbarIndexFor(int keyCode) {
        for (int i = 0; i < 9; i++) {
            if (mc().gameSettings.keyBindsHotbar[i].getKeyCode() == keyCode) {
                return i;
            }
        }
        return -1;
    }

    // =====================================================================
    // 切换
    // =====================================================================
    /**
     * 键盘键路径（子类 keyTyped）：悬浮在玩家槽上时切换锁定。
     *
     * @return 是否真的切换了（只有 true 才应该吞掉这次按键，否则会把输入框字符一起吃掉）
     */
    public static boolean handleKeyboardToggle(GuiContainer gui, int mouseX, int mouseY, int guiLeft, int guiTop) {
        if (!enabled()) {
            return false;
        }
        Slot slot = slotAt(gui, mouseX, mouseY, guiLeft, guiTop);
        if (slot == null) {
            return false;
        }
        toggleAt(slot);
        return true;
    }

    public static void toggleAt(Slot slot) {
        int index = lockKey(slot);
        if (index < 0) {
            return;
        }
        SlotLockManager manager = SlotLockManager.getInstance();
        manager.toggle(index, slot.getStack());
        NHToolbox.LOG.info(
            "[SlotLock] 切换栏位槽 {} -> 锁定状态 {}",
            index,
            manager.getState(index)
                .getType());
    }

    // =====================================================================
    // 渲染
    // =====================================================================
    /**
     * 绘制锁定槽边框与淡化图标。
     *
     * <p>
     * 现在由 {@code MixinGuiContainerLockOverlay} 对**所有**容器界面调用
     * （箱子、熔炉、工作台、AE 终端……），注入点在 GuiContainer.drawScreen 调用前景层之后，
     * 该位置的坐标系已平移过 (guiLeft, guiTop)、光照/深度状态与原前景层入口一致。
     * 因此这里直接使用 slot.xDisplayPosition / yDisplayPosition。
     * 进出时必须保持：LIGHTING=关、LIGHT0/LIGHT1/COLOR_MATERIAL=开、混合函数 (770,771,1,0)。
     */
    public static void renderLocks(GuiContainer gui) {
        if (!SlotLockManager.getInstance()
            .hasAnyLock()) {
            return; // 没有任何锁定（含功能关闭）：O(1) 返回，连槽位表都不遍历
        }
        collectVisibleLockedSlots(gui);
        if (LOCKED_SLOTS.isEmpty()) {
            return; // 当前界面没有锁定栏位：完全不触碰 GL 状态
        }

        collectGhostSlots();

        // 边框全部合并到一次 Tessellator 提交（状态切换只做一遍）
        drawBorders();
        if (!GHOST_SLOTS.isEmpty()) {
            drawGhostIcons();
            drawGhostOverlays();
        }

        // 恢复到本层入口状态
        GL11.glDisable(GL11.GL_LIGHTING);
        OpenGlHelper.glBlendFunc(770, 771, 1, 0);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void collectVisibleLockedSlots(GuiContainer gui) {
        LOCKED_SLOTS.clear();
        SlotLockManager manager = SlotLockManager.getInstance();
        for (Slot slot : slotsOf(gui)) {
            int index = lockKey(slot);
            if (index < 0 || !manager.getState(index)
                .isLocked()) {
                continue;
            }
            // 创造模式其他页签会把槽位移到 (-2000,-2000)（GuiContainerCreative.java:539-540）
            if (slot.xDisplayPosition < -1000 || slot.yDisplayPosition < -1000) {
                continue;
            }
            LOCKED_SLOTS.add(slot);
        }
    }

    private static void collectGhostSlots() {
        GHOST_SLOTS.clear();
        SlotLockManager manager = SlotLockManager.getInstance();
        for (Slot slot : LOCKED_SLOTS) {
            SlotLockState state = manager.getState(lockKey(slot));
            if (state.getType() != SlotLockState.LockType.TYPE || slot.getStack() != null) {
                continue; // 有物品时正常显示物品，只有被取空才显示残影
            }
            if (state.peekTemplate() == null) {
                continue;
            }
            GHOST_SLOTS.add(slot);
        }
    }

    /** 1px 彩色细边框：四条边，全部合并进一次绘制。 */
    private static void drawBorders() {
        SlotLockManager manager = SlotLockManager.getInstance();
        Tessellator tessellator = Tessellator.instance;
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        OpenGlHelper.glBlendFunc(770, 771, 1, 0);
        tessellator.startDrawingQuads();
        for (Slot slot : LOCKED_SLOTS) {
            int index = lockKey(slot);
            if (index < 0) {
                continue;
            }
            int color = manager.getState(index)
                .getType() == SlotLockState.LockType.EMPTY ? COLOR_EMPTY_LOCK : COLOR_TYPE_LOCK;
            tessellator.setColorRGBA_I(color, 255);
            int x = slot.xDisplayPosition;
            int y = slot.yDisplayPosition;
            addQuad(tessellator, x - 1, y - 1, x + 17, y, BORDER_Z);
            addQuad(tessellator, x - 1, y + 16, x + 17, y + 17, BORDER_Z);
            addQuad(tessellator, x - 1, y - 1, x, y + 17, BORDER_Z);
            addQuad(tessellator, x + 16, y - 1, x + 17, y + 17, BORDER_Z);
        }
        tessellator.draw();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** 残影图标：与原版槽位物品同深度（zLevel 50 + renderItemAndEffectIntoGUI 内部 +50 = 100）。 */
    private static void drawGhostIcons() {
        SlotLockManager manager = SlotLockManager.getInstance();
        Minecraft minecraft = mc();
        // 光照只设置一次（与原版在槽位循环前统一 enableGUIStandardItemLighting 的做法一致）
        RenderHelper.enableGUIStandardItemLighting();
        float prevZLevel = RENDER_ITEM.zLevel;
        RENDER_ITEM.zLevel = 50.0F;
        for (Slot slot : GHOST_SLOTS) {
            ItemStack template = manager.getState(lockKey(slot))
                .peekTemplate();
            if (template == null) {
                continue;
            }
            RENDER_ITEM.renderItemAndEffectIntoGUI(
                minecraft.fontRenderer,
                minecraft.getTextureManager(),
                template,
                slot.xDisplayPosition,
                slot.yDisplayPosition);
        }
        RENDER_ITEM.zLevel = prevZLevel;

        // RenderItem 的物品路径末尾会自行 glEnable(GL_LIGHTING)（RenderItem.java:518/526/557/565），
        // 而 disableStandardItemLighting 会连 LIGHT0/LIGHT1/COLOR_MATERIAL 一起关掉，
        // 但本层入口状态是"LIGHTING=关、LIGHT0/LIGHT1/COLOR_MATERIAL=开"。
        // 必须精确恢复，否则后续绘制（手持物品、tip、NEI 等）会处于"有光照但无灯、无颜色材质"的状态，
        // 表现为物品掉色、发灰发淡。
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_LIGHT0);
        GL11.glEnable(GL11.GL_LIGHT1);
        GL11.glEnable(GL11.GL_COLOR_MATERIAL);
    }

    /**
     * 用槽位底色叠加半透明遮罩实现"淡化残影"（不修改 RenderItem.renderWithColor，
     * 那会让带 tint 的物品掉色并影响 NEI 等同样复用 RenderItem 的模块）。
     */
    private static void drawGhostOverlays() {
        Tessellator tessellator = Tessellator.instance;
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        OpenGlHelper.glBlendFunc(770, 771, 1, 0);
        tessellator.startDrawingQuads();
        tessellator.setColorRGBA_I(GHOST_OVERLAY_COLOR, GHOST_OVERLAY_COLOR >> 24 & 255);
        for (Slot slot : GHOST_SLOTS) {
            int x = slot.xDisplayPosition;
            int y = slot.yDisplayPosition;
            addQuad(tessellator, x, y, x + 16, y + 16, GHOST_OVERLAY_Z);
        }
        tessellator.draw();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void addQuad(Tessellator tessellator, int left, int top, int right, int bottom, double z) {
        tessellator.addVertex((double) left, (double) bottom, z);
        tessellator.addVertex((double) right, (double) bottom, z);
        tessellator.addVertex((double) right, (double) top, z);
        tessellator.addVertex((double) left, (double) top, z);
    }

    // =====================================================================
    // 小工具
    // =====================================================================
    @SuppressWarnings("unchecked")
    private static List<Slot> slotsOf(GuiContainer gui) {
        return gui.inventorySlots.inventorySlots;
    }

    /** 该玩家背包索引是否接受此物品（索引非法视为未锁定、一律接受）。 */
    private static boolean canAccept(int playerIndex, ItemStack stack) {
        return SlotLockManager.getInstance()
            .canAccept(playerIndex, stack);
    }

    private static boolean canAccept(Slot slot, ItemStack stack) {
        return canAccept(lockKey(slot), stack);
    }

    private static ItemStack heldItem() {
        Minecraft minecraft = mc();
        return minecraft == null || minecraft.thePlayer == null ? null : minecraft.thePlayer.inventory.getItemStack();
    }

    private static boolean isShiftDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
    }
}
