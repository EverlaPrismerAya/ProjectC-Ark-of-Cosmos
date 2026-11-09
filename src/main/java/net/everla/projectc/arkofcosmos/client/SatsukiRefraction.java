package net.everla.projectc.arkofcosmos.client;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.everla.projectc.arkofcosmos.client.render.ArkFxDraw;
import net.everla.projectc.arkofcosmos.util.AttackFrame;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.GL30;

/** One immutable scene copy per slash pass, never sampling the active draw attachment. */
final class SatsukiRefraction {
    static int texture(){return scene.getColorTextureId();}
    private static TextureTarget scene;
    static void release(){if(scene!=null){scene.destroyBuffers();scene=null;}}
    static void capture(){
        var main=Minecraft.getInstance().getMainRenderTarget();
        if(scene==null)scene=new TextureTarget(main.width,main.height,false,Minecraft.ON_OSX);
        else if(scene.width!=main.width||scene.height!=main.height)scene.resize(main.width,main.height,Minecraft.ON_OSX);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,main.frameBufferId);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,scene.frameBufferId);
        GL30.glBlitFramebuffer(0,0,main.width,main.height,0,0,main.width,main.height,GL30.GL_COLOR_BUFFER_BIT,GL30.GL_NEAREST);
        main.bindWrite(true);
    }
    static void draw(Vec3 at,AttackFrame plane,double length,float strength){
        if(scene==null||strength<=0)return;
        ArkFxDraw.begin("thinendedline",ArkFxDraw.Blend.NONPREMULTIPLIED,false);
        try{
            var shader=ArkFxDraw.satsukiRefractionShader;
            shader.setSampler("SceneSampler",scene.getColorTextureId());
            shader.safeGetUniform("ScreenSize").set((float)scene.width,(float)scene.height);
            RenderSystem.setShader(()->shader);
            var b=Tesselator.getInstance().getBuilder();b.begin(VertexFormat.Mode.QUADS,ArkFxDraw.FORMAT);
            Vec3 x=plane.forward().scale(length*.6),y=plane.right().scale(.8);
            float[] color={1,1,1,strength};
            ArkFxDraw.vertex(b,at.subtract(x).subtract(y),color,0,0,1);
            ArkFxDraw.vertex(b,at.subtract(x).add(y),color,0,1,1);
            ArkFxDraw.vertex(b,at.add(x).add(y),color,1,1,1);
            ArkFxDraw.vertex(b,at.add(x).subtract(y),color,1,0,1);
            BufferUploader.drawWithShader(b.end());
        }finally{ArkFxDraw.end();}
    }
}
