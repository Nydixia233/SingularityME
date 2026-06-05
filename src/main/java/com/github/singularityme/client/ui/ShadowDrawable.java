package com.github.singularityme.client.ui;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.screen.viewport.GuiContext;
import com.cleanroommc.modularui.theme.WidgetTheme;

/** 给 MUI2 背景叠加投影的轻量 drawable。 */
public final class ShadowDrawable implements IDrawable {

    private final IDrawable inner;
    private final int spread;
    private final int shadowColor;

    public ShadowDrawable(final IDrawable inner, final int spread, final int shadowColor) {
        this.inner = inner == null ? IDrawable.NONE : inner;
        this.spread = Math.max(0, spread);
        this.shadowColor = shadowColor;
    }

    @Override
    public void draw(final GuiContext ctx, final int x, final int y, final int w, final int h,
        final WidgetTheme theme) {
        if (spread > 0) {
            GuiDraw.drawDropShadow(x, y, w, h, spread, spread, 0x00000000, shadowColor & 0x00FFFFFF);
        }
        inner.draw(ctx, x, y, w, h, theme);
    }
}
