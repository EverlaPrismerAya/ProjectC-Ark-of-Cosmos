package net.everla.projectc.arkofcosmos.util;

import net.minecraft.resources.ResourceLocation;

/** Stable name marker; animation is sampled only while drawing. */
public final class MahouNameStyle {
    public static final ResourceLocation FONT = ResourceLocation.fromNamespaceAndPath("arkofcosmos", "mahou_name");
    private static final int[] COLORS = {0x00FFFF, 0x8800FF, 0xFF00FF, 0x8800FF, 0x00FFFF};

    private MahouNameStyle() {}

    public static int colorAt(long millis) {
        float position = Math.floorMod(millis, 6000L) / 6000f * 4;
        int index = (int) position;
        float t = position - index;
        t = t * t * (3 - 2 * t);
        int color = 0;
        for (int shift = 0; shift <= 16; shift += 8) {
            int start = COLORS[index] >> shift & 255;
            int end = COLORS[index + 1] >> shift & 255;
            color |= Math.round(start + (end - start) * t) << shift;
        }
        return color;
    }
}
