package net.everla.projectc.arkofcosmos.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import com.Polarice3.Goety.common.entities.boss.Apostle;
import org.joml.Matrix4f;

/**
 * Shared rainbow fill for RF's legacy Goety hook and modern native boss bar.
 * The fill is clipped by the bar texture's own alpha (the region RF's native fill blit would draw); the caller then
 * lets RF/GR draw their Cosmic overlay over the same region.
 */
public final class SatsukiBossBarFill {
    private SatsukiBossBarFill() {}
    private static final java.util.Map<Apostle,double[]> ark$hurtStates=new java.util.WeakHashMap<>();
    private static float ark$hurtFade;
    private static long ark$noiseFrame;
    private static ShaderInstance mask;

    public static void registerShader(net.minecraftforge.client.event.RegisterShadersEvent event) throws java.io.IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                ResourceLocation.fromNamespaceAndPath("arkofcosmos","satsuki_bar_mask"),DefaultVertexFormat.POSITION_TEX_COLOR),loaded->mask=loaded);
    }

    public static void update(Apostle apostle,float partial){
        double now=apostle.level().getGameTime()+partial;
        float health=apostle.getHealth();
        double[] state=ark$hurtStates.computeIfAbsent(apostle,key->new double[]{health,Double.NEGATIVE_INFINITY});
        if(health<state[0])state[1]=now;
        state[0]=health;
        ark$hurtFade=(float)Math.max(0,1-(now-state[1])/10);
        ark$noiseFrame=(long)(now*3);
    }

    /**
     * Draws {@code health} columns of the bar region (u, v)–(u + health, v + height) of a 256² texture, replacing its
     * colour with the rainbow while keeping its alpha as the mask.
     */
    public static void fill(GuiGraphics graphics,ResourceLocation texture,int x,int y,float u,float v,float health,int height,float alpha){
        if(health<1||alpha<=0)return;
        graphics.flush();
        int width=(int)health;
        float hue=(net.minecraft.Util.getMillis()%12000L)/12000f;
        Matrix4f matrix=graphics.pose().last().pose();
        if(mask==null){
            // Shader unavailable (e.g. failed reload): plain rectangle fill, never the uncoloured texture.
            for(int column=0;column<width;column++)
                graphics.fill(x+column,y,x+column+1,y+height,((int)(255*alpha)<<24)|rainbow(hue,column));
            return;
        }
        RenderSystem.setShaderTexture(0,texture);
        RenderSystem.setShader(()->mask);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        var buffer=Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR);
        for(int column=0;column<width;column++){
            int rgb=rainbow(hue,column);
            quad(buffer,matrix,x+column,y,1,height,u+column,v,1,height,
                    (rgb>>16&255)/255f,(rgb>>8&255)/255f,(rgb&255)/255f,alpha);
        }
        if(ark$hurtFade>0){
            float noiseAlpha=220/255f*ark$hurtFade*alpha;
            for(int column=0;column<width;column++)for(int row=0;row<height;row++){
                int hash=(int)ark$noiseFrame*73428767+column*912931+row*438289;
                hash=(hash^(hash>>>13))*1274126177;
                if((hash&3)!=0)continue;
                float gray=((hash>>>24)&255)/255f;
                quad(buffer,matrix,x+column,y+row,1,1,u+column,v+row,1,1,gray,gray,gray,noiseAlpha);
            }
        }
        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.disableBlend();
    }

    private static int rainbow(float hue,int column){
        return Mth.hsvToRgb((hue+column/208f)%1,0.48f*(1-ark$hurtFade),1);
    }

    private static void quad(BufferBuilder buffer,Matrix4f matrix,float x,float y,float w,float h,
                             float u,float v,float uw,float vh,float r,float g,float b,float a){
        float u0=u/256,u1=(u+uw)/256,v0=v/256,v1=(v+vh)/256;
        buffer.vertex(matrix,x,y,0).uv(u0,v0).color(r,g,b,a).endVertex();
        buffer.vertex(matrix,x,y+h,0).uv(u0,v1).color(r,g,b,a).endVertex();
        buffer.vertex(matrix,x+w,y+h,0).uv(u1,v1).color(r,g,b,a).endVertex();
        buffer.vertex(matrix,x+w,y,0).uv(u1,v0).color(r,g,b,a).endVertex();
    }
}
