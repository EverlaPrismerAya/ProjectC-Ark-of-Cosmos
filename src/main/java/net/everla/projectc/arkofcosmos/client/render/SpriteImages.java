package net.everla.projectc.arkofcosmos.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;

/** Resource-pack-aware dimensions and alpha contours shared by all sprite materials. */
public final class SpriteImages {
    private SpriteImages() {}
    public record Image(int[] size, List<int[]> contour) {
        public int width() { return size[0]; }
        public int height() { return size[1]; }
    }
    private static final Map<ResourceLocation, Image> IMAGES = new HashMap<>();
    public static void clear() { IMAGES.clear(); ArkFxDraw.sizes.clear(); }
    public static Image get(ResourceLocation texture) {
        return IMAGES.computeIfAbsent(texture, key -> {
            try (var stream = Minecraft.getInstance().getResourceManager().getResourceOrThrow(key).open();
                 var image = NativeImage.read(stream)) {
                return read(image);
            } catch (IOException error) { throw new UncheckedIOException(error); }
        });
    }
    static Image read(NativeImage image) {
        int w = image.getWidth(), h = image.getHeight();
        List<int[]> edges = new ArrayList<>();
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            if ((image.getPixelRGBA(x, y) >>> 24) == 0) continue;
            if (x == 0 || (image.getPixelRGBA(x - 1, y) >>> 24) == 0) edges.add(new int[]{x,y,0});
            if (x == w - 1 || (image.getPixelRGBA(x + 1, y) >>> 24) == 0) edges.add(new int[]{x,y,1});
            if (y == 0 || (image.getPixelRGBA(x, y - 1) >>> 24) == 0) edges.add(new int[]{x,y,2});
            if (y == h - 1 || (image.getPixelRGBA(x, y + 1) >>> 24) == 0) edges.add(new int[]{x,y,3});
        }
        return new Image(new int[]{w,h}, List.copyOf(edges));
    }
}
