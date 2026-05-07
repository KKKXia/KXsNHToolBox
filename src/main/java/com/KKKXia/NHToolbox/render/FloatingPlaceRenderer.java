package com.KKKXia.NHToolbox.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.player.EntityPlayer;

import org.lwjgl.opengl.GL11;

import com.KKKXia.NHToolbox.config.ModConfig;
import com.KKKXia.NHToolbox.helper.RayTraceHelper;
import com.KKKXia.NHToolbox.manager.FloatingPlaceManager;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class FloatingPlaceRenderer {

    private static final FloatingPlaceManager placeManager = FloatingPlaceManager.getInstance();

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (!placeManager.isFloatingPlaceMode() || !ModConfig.isPreviewBoxEnabled()) {
            placeManager.clearPreviewPosition();
            return;
        }

        int[] pos = RayTraceHelper.getPlacementPreviewPosition();
        if (pos != null) {
            placeManager.setPreviewPosition(pos[0], pos[1], pos[2]);
        } else {
            placeManager.clearPreviewPosition();
        }
    }

    public static void renderPreviewBox(float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.thePlayer;

        if (player == null || mc.theWorld == null) {
            return;
        }

        if (!placeManager.isFloatingPlaceMode() || !ModConfig.isPreviewBoxEnabled()) {
            return;
        }

        int[] pos = RayTraceHelper.getPlacementPreviewPosition();
        if (pos == null) {
            return;
        }

        double px = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks;
        double py = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks;
        double pz = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks;

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glLineWidth(2.0F);

        double x = pos[0] - px;
        double y = pos[1] - py;
        double z = pos[2] - pz;

        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawing(3);
        tessellator.setColorRGBA_F(0.0F, 1.0F, 0.5F, 0.6F);

        tessellator.addVertex(x, y, z);
        tessellator.addVertex(x + 1, y, z);
        tessellator.addVertex(x + 1, y, z + 1);
        tessellator.addVertex(x, y, z + 1);
        tessellator.addVertex(x, y, z);

        tessellator.addVertex(x, y + 1, z);
        tessellator.addVertex(x + 1, y + 1, z);
        tessellator.addVertex(x + 1, y + 1, z + 1);
        tessellator.addVertex(x, y + 1, z + 1);
        tessellator.addVertex(x, y + 1, z);

        tessellator.addVertex(x, y + 1, z + 1);
        tessellator.addVertex(x, y, z + 1);
        tessellator.addVertex(x + 1, y, z + 1);
        tessellator.addVertex(x + 1, y + 1, z + 1);

        tessellator.addVertex(x + 1, y + 1, z);
        tessellator.addVertex(x + 1, y, z);
        tessellator.addVertex(x, y, z);
        tessellator.addVertex(x, y + 1, z);

        tessellator.draw();

        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }
}
