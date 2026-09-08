package com.KKKXia.NHToolbox.handler;

import java.lang.reflect.Field;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import com.KKKXia.NHToolbox.NHToolbox;
import com.KKKXia.NHToolbox.inventory.SlotLockManager;
import com.KKKXia.NHToolbox.inventory.SlotLockState;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * 背包栏位锁定：客户端输入拦截 + 锁定槽渲染 + 进入世界时加载。
 *
 * <p>
 * 拦截原理（Forge 1.7.10 事件序，已从 Minecraft.runTick 字节码核实）：
 * while (Mouse.next()) { ① ForgeHooksClient.postMouseEvent() —— 可取消，取消则
 * 原版（含 GUI 的 mouseClicked/mouseMovedOrUp）完全不处理该事件；② KeyBinding
 * 状态更新；③ currentScreen.handleMouseInput()；④ FML fireMouseInput()。}
 * 因此"吃掉点击"只能用 ① 的 {@link net.minecraftforge.client.event.MouseEvent}；
 * FML 的 Mouse/KeyInputEvent 都在原版处理之后触发，只能做事后响应。
 *
 * <p>
 * 渲染使用 {@link TickEvent.RenderTickEvent} 的 END 阶段：它在
 * entityRenderer.updateCameraAndRender(...)（内部调用 currentScreen.drawScreen）
 * 之后触发，已覆盖所有 GUI 内容（1.7.10 的 GuiScreenEvent.DrawScreenEvent 是
 * 从未被触发的死事件，不可用）。
 *
 * <p>
 * 键位判定刻意不依赖 KeyBinding.isPressed()/内部哈希表（1.7.10 中该机制会被
 * 后注册的同码键覆盖、且 mod 按键的 options.txt 保存值不会被自动加载），
 * 而是：鼠标键直接比对按钮号（MouseEvent），键盘键直接比对 Keyboard.getEventKey()。
 */
public class SlotLockHandler {

    /** 边框颜色：空锁定 = 蓝色，类型锁定 = 绿色。 */
    private static final int COLOR_EMPTY_LOCK = 0xFF40B0FF;
    private static final int COLOR_TYPE_LOCK = 0xFF40FF80;
    private static final float GHOST_ALPHA = 0.5F;

    private static final Minecraft MC = Minecraft.getMinecraft();
    private static final RenderItem RENDER_ITEM = new RenderItem();

    // GuiContainer 的受保护成员：开发环境是 MCP 名，发布（srg）环境是另一个名字，按顺序尝试
    private static final Field GUI_LEFT = findField("guiLeft", "field_147003_i");
    private static final Field GUI_TOP = findField("guiTop", "field_147009_r");
    private static final Field DRAG_ACTIVE = findField("field_147007_t");
    private static final Field DRAG_TARGETS = findField("field_147008_s");

    private static boolean warnedLayoutFields = false;

    private boolean worldLoaded = false;

    public SlotLockHandler() {
        NHToolbox.LOG.info(
            "[SlotLock] 输入拦截/渲染处理器已注册（锁定键：{}，键码 {}）",
            KeyBindings.LOCK_SLOT.getKeyDescription(),
            KeyBindings.LOCK_SLOT.getKeyCode());
    }

    // =====================================================================
    // 输入拦截：在原版 GUI 处理之前，只有需要"吃掉点击"时才取消
    // =====================================================================
    @SubscribeEvent
    public void onMouse(net.minecraftforge.client.event.MouseEvent event) {
        if (MC.thePlayer == null) {
            return;
        }
        GuiContainer gui = getGuiContainer();
        if (gui == null) {
            return;
        }

        if (event.button != -1) {
            int lockKey = KeyBindings.LOCK_SLOT.getKeyCode();
            NHToolbox.LOG.info(
                "[SlotLock][mouseevt] button={} state={} lockKey={} isLockKey={} screen={}",
                event.button,
                event.buttonstate,
                lockKey,
                isLockKey(event.button),
                gui.getClass()
                    .getSimpleName());
            // ---------- 锁定键（鼠标键绑定，含默认中键） ----------
            if (isLockKey(event.button)) {
                if (event.buttonstate) {
                    Slot slot = findPlayerSlot(gui, event.x, event.y, true);
                    if (slot != null) {
                        SlotLockManager.getInstance()
                            .toggle(slot.getSlotIndex(), slot.getStack());
                        NHToolbox.LOG.info(
                            "[SlotLock] 鼠标键 {} 切换栏位槽 {} -> 锁定状态 {}",
                            event.button,
                            slot.getSlotIndex(),
                            SlotLockManager.getInstance()
                                .getState(slot.getSlotIndex())
                                .getType());
                        // 吞掉锁定键按下，阻止原版 GUI 的中键行为：
                        // 空手时触发 clickType 3（生存为空操作、创造为拾取），手持时开始拖拽
                        event.setCanceled(true);
                    }
                } else if (!isDragActive(gui)) {
                    // 没有拖拽进行中的原版锁定键抬起是 clickType 3（生存空操作），
                    // 统一吞掉以避免与"锁定键"的语义不一致；按下已取消，不吞会错位
                    event.setCanceled(true);
                    // 被取消的抬起事件不会执行 KeyBinding.setKeyBindState(...)，
                    // 手动补一次，避免按键状态停留在"按下"
                    KeyBinding.setKeyBindState(event.button - 100, false);
                }
                return;
            }

            // ---------- 放置类拦截：只处理左键 0 / 右键 1 ----------
            if (event.button != 0 && event.button != 1) {
                return;
            }
            ItemStack held = MC.thePlayer.inventory.getItemStack();
            if (held == null) {
                return; // 空手：取出/双击收集，一律放行
            }

            if (event.buttonstate) {
                // 按下：阻止"直接放入"以及"从此开始一次拖拽"（同种堆叠放行）
                Slot slot = findPlayerSlot(gui, event.x, event.y, false);
                if (slot != null && !SlotLockManager.getInstance()
                    .canAccept(slot.getSlotIndex(), held)) {
                    event.setCanceled(true);
                }
            } else {
                // 松开：拖到锁定槽上释放会被原版直接放入（clickType 0/5 分堆），
                // 吞掉并清理原版拖拽残留状态（否则下次交互的预览/分配会错乱）
                Slot slot = findPlayerSlot(gui, event.x, event.y, false);
                if (slot != null && !SlotLockManager.getInstance()
                    .canAccept(slot.getSlotIndex(), held)) {
                    event.setCanceled(true);
                    clearDragState(gui, event.button);
                }
            }
            return;
        }

        // ---------- 鼠标移动（拖拽中）：禁止把锁定槽加入原版拖拽目标集合 ----------
        if (isDragActive(gui)) {
            ItemStack held = MC.thePlayer.inventory.getItemStack();
            if (held != null) {
                Slot slot = findPlayerSlot(gui, event.x, event.y, false);
                if (slot != null && !SlotLockManager.getInstance()
                    .canAccept(slot.getSlotIndex(), held)) {
                    event.setCanceled(true);
                }
            }
        }
    }

    // =====================================================================
    // 快捷键改绑为键盘键时的入口（鼠标键走 onMouse 直接比对按钮号）。
    // 注意：不比对 KeyBinding.isPressed() —— 它依赖内部哈希表，1.7.10 下
    // 同码后注册者覆盖会使其失效；这里直接比对本次键盘事件的键码。
    // =====================================================================
    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent event) {
        GuiContainer gui = getGuiContainer();
        if (gui == null) {
            return; // 只在容器界面（背包/箱子等）响应；避开游戏内按键的日志噪音
        }
        int key = Keyboard.getEventKey();
        boolean state = Keyboard.getEventKeyState();
        int lockKey = KeyBindings.LOCK_SLOT.getKeyCode();
        NHToolbox.LOG.info(
            "[SlotLock][keyevt] key={} state={} lockKey={} match={} screen={}",
            key,
            state,
            lockKey,
            lockKey >= 0 && state && key == lockKey,
            gui.getClass()
                .getSimpleName());
        if (lockKey < 0) {
            return; // 鼠标键绑定由 onMouse 处理，避免同一次点击触发两次切换
        }
        if (!state) {
            return;
        }
        if (key != lockKey) {
            return;
        }
        if (MC.thePlayer == null) {
            return;
        }
        Slot slot = findPlayerSlot(gui, Mouse.getX(), Mouse.getY(), true);
        if (slot != null) {
            SlotLockManager.getInstance()
                .toggle(slot.getSlotIndex(), slot.getStack());
            NHToolbox.LOG.info(
                "[SlotLock] 键盘键 {} 切换栏位槽 {} -> 锁定状态 {}",
                key,
                slot.getSlotIndex(),
                SlotLockManager.getInstance()
                    .getState(slot.getSlotIndex())
                    .getType());
        }
    }

    // =====================================================================
    // 渲染：锁定槽彩色边框 + 类型锁定被取空后的淡化图标
    // =====================================================================
    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (MC.thePlayer == null) {
            return;
        }
        GuiContainer gui = getGuiContainer();
        if (gui == null) {
            return;
        }
        renderSlotLocks(gui);
    }

    // =====================================================================
    // 生命周期：进入世界后加载锁定状态（每次切换时已即时落盘）
    // =====================================================================
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (MC.theWorld != null && MC.thePlayer != null) {
            if (!worldLoaded) {
                worldLoaded = true;
                SlotLockManager.getInstance()
                    .load();
                NHToolbox.LOG.info("[SlotLock] 已加载背包栏位锁定状态");
            }
        } else {
            worldLoaded = false;
        }
    }

    // =====================================================================
    // 工具方法
    // =====================================================================
    private static GuiContainer getGuiContainer() {
        return MC.currentScreen instanceof GuiContainer ? (GuiContainer) MC.currentScreen : null;
    }

    private static boolean isLockKey(int button) {
        int keyCode = KeyBindings.LOCK_SLOT.getKeyCode();
        return keyCode < 0 && button == keyCode + 100;
    }

    /** 命中判定：换算缩放坐标（与原版 GuiScreen.handleMouseInput 一致，Y 轴反转）后遍历玩家背包槽。 */
    private static Slot findPlayerSlot(GuiContainer gui, int rawX, int rawY, boolean logDiagnostics) {
        Integer guiLeft = getField(gui, GUI_LEFT);
        Integer guiTop = getField(gui, GUI_TOP);
        if (guiLeft == null || guiTop == null) {
            warnOnceLayoutFields();
            return null;
        }
        int mx = rawX * gui.width / MC.displayWidth;
        int my = gui.height - rawY * gui.height / MC.displayHeight - 1;
        Slot found = null;
        for (Slot slot : gui.inventorySlots.inventorySlots) {
            if (slot.inventory != MC.thePlayer.inventory) {
                continue;
            }
            if (slot.getSlotIndex() < 0 || slot.getSlotIndex() >= SlotLockManager.SLOT_COUNT) {
                continue; // 排除护甲(36-39)与其他索引
            }
            int x = guiLeft + slot.xDisplayPosition;
            int y = guiTop + slot.yDisplayPosition;
            if (mx >= x - 1 && mx < x + 17 && my >= y - 1 && my < y + 17) {
                found = slot;
                break;
            }
        }
        if (found == null && logDiagnostics) {
            NHToolbox.LOG.info(
                "[SlotLock][find] 未命中玩家槽：rawX={} rawY={} mx={} my={} guiLeft={} guiTop={} "
                    + "gui={}x{} display={}x{} slots={}",
                rawX,
                rawY,
                mx,
                my,
                guiLeft,
                guiTop,
                gui.width,
                gui.height,
                MC.displayWidth,
                MC.displayHeight,
                gui.inventorySlots.inventorySlots.size());
        }
        return found;
    }

    private static void warnOnceLayoutFields() {
        if (!warnedLayoutFields) {
            warnedLayoutFields = true;
            NHToolbox.LOG.warn("[SlotLock] 无法读取 GuiContainer 的 guiLeft/guiTop 字段（反射失效），" + "栏位命中判定与渲染将失效");
        }
    }

    private static void renderSlotLocks(GuiContainer gui) {
        Integer guiLeft = getField(gui, GUI_LEFT);
        Integer guiTop = getField(gui, GUI_TOP);
        if (guiLeft == null || guiTop == null) {
            warnOnceLayoutFields();
            return;
        }

        // 建立与原版 GUI 相同的 2D 正交投影
        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0.0D, gui.width, gui.height, 0.0D, 1000.0D, 3000.0D);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glTranslatef(0.0F, 0.0F, -2000.0F);

        SlotLockManager manager = SlotLockManager.getInstance();
        for (Slot slot : gui.inventorySlots.inventorySlots) {
            if (slot.inventory != MC.thePlayer.inventory) {
                continue;
            }
            int index = slot.getSlotIndex();
            if (index < 0 || index >= SlotLockManager.SLOT_COUNT) {
                continue;
            }
            SlotLockState state = manager.getState(index);
            if (!state.isLocked()) {
                continue;
            }

            int x = guiLeft + slot.xDisplayPosition;
            int y = guiTop + slot.yDisplayPosition;
            int color = state.getType() == SlotLockState.LockType.EMPTY ? COLOR_EMPTY_LOCK : COLOR_TYPE_LOCK;

            // 彩色小方框：2px 宽的四条边
            Gui.drawRect(x - 1, y - 1, x + 17, y + 1, color);
            Gui.drawRect(x - 1, y + 15, x + 17, y + 17, color);
            Gui.drawRect(x - 1, y - 1, x + 1, y + 17, color);
            Gui.drawRect(x + 15, y - 1, x + 17, y + 17, color);

            // 类型锁定且槽位为空：半透明渲染模板图标
            if (state.getType() == SlotLockState.LockType.TYPE && slot.getStack() == null) {
                ItemStack template = state.getTemplate();
                if (template != null) {
                    GL11.glEnable(GL11.GL_BLEND);
                    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                    GL11.glColor4f(1.0F, 1.0F, 1.0F, GHOST_ALPHA);
                    RenderHelper.enableGUIStandardItemLighting();
                    RENDER_ITEM.renderItemAndEffectIntoGUI(MC.fontRenderer, MC.getTextureManager(), template, x, y);
                    RenderHelper.disableStandardItemLighting();
                    GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
                    GL11.glDisable(GL11.GL_BLEND);
                }
            }
        }

        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }

    private static boolean isDragActive(GuiContainer gui) {
        Boolean active = getField(gui, DRAG_ACTIVE);
        return Boolean.TRUE.equals(active);
    }

    /** 取消一次"松开"事件后，把原版拖拽状态恢复原样（并补上被跳过的按键状态更新）。 */
    private static void clearDragState(GuiContainer gui, int button) {
        setField(gui, DRAG_ACTIVE, false);
        Set<Slot> targets = getField(gui, DRAG_TARGETS);
        if (targets != null) {
            targets.clear();
        }
        KeyBinding.setKeyBindState(button - 100, false);
    }

    @SuppressWarnings("unchecked")
    private static <T> T getField(GuiContainer gui, Field field) {
        if (field == null) {
            return null;
        }
        try {
            return (T) field.get(gui);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void setField(GuiContainer gui, Field field, Object value) {
        if (field == null) {
            return;
        }
        try {
            field.set(gui, value);
        } catch (Exception ignored) {}
    }

    private static Field findField(String... names) {
        for (String name : names) {
            try {
                Field field = GuiContainer.class.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                // 尝试下一个名称
            }
        }
        return null;
    }
}
