package com.kkkxia.nhtoolbox.feature.floatingplace;

import net.minecraftforge.client.event.RenderWorldLastEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** 每帧末尾绘制浮空放置预览框。 */
public class FloatingPlaceRenderHandler {

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        FloatingPlaceRenderer.renderPreviewBox(event.partialTicks);
    }
}
