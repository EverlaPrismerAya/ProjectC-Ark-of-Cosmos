package net.everla.projectc.arkofcosmos.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.vertex.*;
import net.everla.projectc.arkofcosmos.client.render.NativeSprites;
import net.everla.projectc.arkofcosmos.item.BiomeBladeItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** TrueBiomeBlade.PreDrawInInventory and its shared ChargingEnergyParticleSet. GUI only. */
public final class BiomeItemEffects {
    private BiomeItemEffects(){}
    private static final int[] EDGE={0x757E48,0x892000,0xA5EBEB,0x357004,0x700423,0x3E376E,0x3A38A5,0x1B3B65};
    private static final int[] CENTER={0xC8B888,0xD19A00,0x3A6E8D,0x83AD27,0xC32AC8,0xFF8FFF,0x9978FF,0x1BFFFF};
    private static final List<Energy> LIVE=new ArrayList<>();
    private static final Random RNG=new Random();
    private static Object world;private static int timer,edge=0xFFFFFF,center=0xFFFFFF;
    private static final class Energy {
        double x,y,scale=.1;int age;float[] color;
        final int edgeColor,centerColor;
        Energy(){double angle=RNG.nextDouble()*Math.PI*2;x=Math.cos(angle)*20;y=Math.sin(angle)*20;edgeColor=edge;centerColor=center;}
        boolean update(){
            age++;double length=Math.hypot(x,y),t=clamp((20-length)/14);
            scale=(.05+.075*t*t*(3-2*t))*clamp((age-50)/-10d);
            if(length>4.5){x*=.9;y*=.9;}
            color=mix(edgeColor,centerColor,clamp(age/50d/.67));color[3]=50/255f;
            return age<50;
        }
    }
    public static void tick(){
        var mc=Minecraft.getInstance();if(world!=mc.level){world=mc.level;LIVE.clear();timer=0;edge=center=0xFFFFFF;}
        if(mc.level==null||mc.isPaused())return;
        for(int i=0;i<3;i++){if(timer%2==1)LIVE.add(new Energy());LIVE.removeIf(p->!p.update());timer++;}
    }
    public static void render(ItemStack stack,PoseStack pose,MultiBufferSource buffers){
        int mode=BiomeBladeItem.main(stack);if(mode<0)return;
        edge=EDGE[mode];center=CENTER[mode];
        if(buffers instanceof MultiBufferSource.BufferSource source)source.endBatch();
        var oldShader=RenderSystem.getShader();RenderSystem.enableBlend();RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        try{
            RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE,GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            // The source set is shared by all inventory instances and draws younger particles first.
            for(int i=LIVE.size()-1;i>=0;i--){Energy p=LIVE.get(i);if(p.color!=null)quad(pose,BiomeAssets.texture("light"),p.x/68+(12/68d-.18),-p.y/68-(16/68d-.18),-.05,p.scale*NativeSprites.width(BiomeAssets.texture("light"))/68,p.scale*NativeSprites.height(BiomeAssets.texture("light"))/68,p.color);}
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,GlStateManager.DestFactor.ONE);
            var mc=Minecraft.getInstance();double time=mc.level==null?0:(mc.level.getGameTime()+mc.getFrameTime())/20d;
            double x=Math.cos(time*3)*2*Math.sin(time)/68,y=-Math.sin(time*3)*2*Math.sin(time)/68;
            float[] color=mix(CENTER[mode],CENTER[mode],0);
            quad(pose,BiomeAssets.texture("item"),x,y,-.04,1,1,color);
            quad(pose,BiomeAssets.texture("item"),-x,-y,-.04,1,1,color);
        }finally{RenderSystem.defaultBlendFunc();RenderSystem.enableCull();RenderSystem.setShader(()->oldShader);}
    }
    private static void quad(PoseStack pose,String texture,double x,double y,double z,double w,double h,float[] c){
        RenderSystem.setShaderTexture(0,NativeSprites.texture(texture));
        BufferBuilder b=Tesselator.getInstance().getBuilder();b.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR);
        var m=pose.last().pose();
        b.vertex(m,(float)(x-w/2),(float)(y+h/2),(float)z).uv(0,0).color(c[0],c[1],c[2],c[3]).endVertex();
        b.vertex(m,(float)(x-w/2),(float)(y-h/2),(float)z).uv(0,1).color(c[0],c[1],c[2],c[3]).endVertex();
        b.vertex(m,(float)(x+w/2),(float)(y-h/2),(float)z).uv(1,1).color(c[0],c[1],c[2],c[3]).endVertex();
        b.vertex(m,(float)(x+w/2),(float)(y+h/2),(float)z).uv(1,0).color(c[0],c[1],c[2],c[3]).endVertex();
        BufferUploader.drawWithShader(b.end());
    }
    private static double clamp(double x){return Math.max(0,Math.min(1,x));}
    private static float[] mix(int a,int b,double t){return new float[]{(float)(((a>>16&255)*(1-t)+(b>>16&255)*t)/255), (float)(((a>>8&255)*(1-t)+(b>>8&255)*t)/255),(float)(((a&255)*(1-t)+(b&255)*t)/255),1};}
}
