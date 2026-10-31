package net.everla.projectc.arkofcosmos.mixin.client;

import net.everla.projectc.arkofcosmos.util.MahouNameStyle;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Run before performance/rendering mods that replace Font's internal batching path.
@Mixin(value = Font.class, priority = 2000)
public abstract class MahouNameFontMixin {
    @Unique
    private static boolean arkofcosmos$isAnimated(Style style) {
        return MahouNameStyle.marked(style);
    }

    @Invoker("renderText")
    protected abstract float arkofcosmos$renderText(FormattedCharSequence text, float x, float y,
            int color, boolean shadow, Matrix4f matrix, MultiBufferSource buffers,
            Font.DisplayMode mode, int background, int light);

    @Inject(method = "drawInternal(Lnet/minecraft/util/FormattedCharSequence;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)I",
        at = @At("HEAD"), cancellable = true)
    private void arkofcosmos$renderSuperearth(FormattedCharSequence text, float x, float y,
            int color, boolean shadow, Matrix4f matrix, MultiBufferSource buffers,
            Font.DisplayMode mode, int background, int light, CallbackInfoReturnable<Integer> callback) {
        boolean[] marked = {false};
        text.accept((index, style, codePoint) -> {
            marked[0] = arkofcosmos$isAnimated(style);
            return !marked[0];
        });
        if (!marked[0]) return;

        Font font = (Font)(Object)this;
        double phase = (System.currentTimeMillis() % 3000L) * Math.PI * 2 / 3000.0;
        int alpha = (color & 0xFC000000) == 0 ? 255 : color >>> 24;
        int outlineAlpha = Math.round(alpha * 0.28f);
        float[] cursor = {x};
        // Draw every outline first, then every foreground: neighboring outlines cannot cover letters.
        for (int pass = 0; pass < 2; pass++) {
            boolean outlinePass = pass == 0;
            cursor[0] = x;
            int[] character = {0};
            text.accept((index, style, codePoint) -> {
                boolean animated = arkofcosmos$isAnimated(style);
                Style glyphStyle = animated ? style.withFont(Style.DEFAULT_FONT) : style;
                if (MahouNameStyle.marked(style)) {
                    glyphStyle = glyphStyle.withColor(MahouNameStyle.colorAt(System.currentTimeMillis() + character[0] * 120L));
                }
                FormattedCharSequence glyph = FormattedCharSequence.codepoint(codePoint, glyphStyle);
                float floatingY = animated ? y + (float)Math.sin(phase + character[0] * 0.35) * 0.8f : y;
                if (outlinePass && animated && outlineAlpha > 0) {
                    int rgb = glyphStyle.getColor() == null ? color : glyphStyle.getColor().getValue();
                    // Match the glyph's grayscale brightness instead of washing dark colors out with white.
                    int brightness = Math.round(0.2126f * (rgb >> 16 & 255)
                        + 0.7152f * (rgb >> 8 & 255) + 0.0722f * (rgb & 255));
                    int edgeColor = brightness << 16 | (brightness * 253 / 255) << 8
                        | brightness * 242 / 255;
                    FormattedCharSequence edge = FormattedCharSequence.codepoint(codePoint,
                        glyphStyle.withColor(edgeColor));
                    for (int direction = 0; direction < 4; direction++) {
                        float dx = direction == 0 ? -1f : direction == 1 ? 1f : 0;
                        float dy = direction == 2 ? -1f : direction == 3 ? 1f : 0;
                        // Bypass adjustColor: it turns alpha values below 4 back into full opacity.
                        arkofcosmos$renderText(edge, cursor[0] + dx, floatingY + dy,
                            outlineAlpha << 24 | 0xFFFFFF, false, new Matrix4f(matrix),
                            buffers, mode, 0, light);
                    }
                } else if (!outlinePass) {
                    font.drawInBatch(glyph, cursor[0], floatingY, color, shadow && !animated,
                        new Matrix4f(matrix).translate(0, 0, 0.03f), buffers, mode, background, light);
                }
                cursor[0] += font.getSplitter().stringWidth(glyph);
                character[0]++;
                return true;
            });
        }
        callback.setReturnValue((int)cursor[0] + (shadow ? 1 : 0));
    }
}
