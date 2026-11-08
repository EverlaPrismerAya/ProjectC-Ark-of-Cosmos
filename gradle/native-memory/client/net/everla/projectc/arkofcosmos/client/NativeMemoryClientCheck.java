package net.everla.projectc.arkofcosmos.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;

/** Real GL context: stable native storage and idempotent GL resource retirement. */
@Mod.EventBusSubscriber(modid="arkofcosmos",value=Dist.CLIENT)
public final class NativeMemoryClientCheck {
    private static boolean done;
    static Field field(Class<?> type,String name)throws Exception{var f=type.getDeclaredField(name);f.setAccessible(true);return f;}
    static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static void buffers(Class<?> owner,String name)throws Exception{
        var source=(MultiBufferSource.BufferSource)field(owner,name).get(null);
        var builder=(BufferBuilder)field(MultiBufferSource.BufferSource.class,"builder").get(source);
        var bytes=field(BufferBuilder.class,"buffer");
        long address=0;int capacity=0;
        for(int frame=0;frame<501;frame++){
            require(field(owner,name).get(null)==source,"buffer source replaced");
            var out=source.getBuffer(RenderType.entityTranslucent(ResourceLocation.fromNamespaceAndPath("minecraft","textures/entity/illager/ravager.png")));
            for(int i=0;i<4096;i++)out.vertex(0,0,0).color(255,255,255,255).uv(0,0).overlayCoords(0).uv2(15728880).normal(0,1,0).endVertex();
            source.endBatch();
            var buffer=(ByteBuffer)bytes.get(builder);
            if(frame==0){address=MemoryUtil.memAddress0(buffer);capacity=buffer.capacity();}
            else require(address==MemoryUtil.memAddress0(buffer)&&capacity==buffer.capacity(),"native storage changed after warmup");
        }
        System.out.println("NATIVE_MEMORY_BUFFER_PASS "+owner.getSimpleName()+" draws=501 capacity="+capacity);
    }
    static void targets(Class<?> owner)throws Exception{
        var mask=EffectRenderTarget.create(32,32,false);var visible=EffectRenderTarget.create(32,32,true);
        int color=mask.getColorTextureId(),depthColor=visible.getColorTextureId();
        int fbo=mask.frameBufferId,depthFbo=visible.frameBufferId;
        field(owner,"mask").set(null,mask);field(owner,"visible").set(null,visible);
        var release=owner.getDeclaredMethod("releaseTargets");release.setAccessible(true);
        release.invoke(null);release.invoke(null);
        require(field(owner,"mask").get(null)==null&&field(owner,"visible").get(null)==null,"retired target retained");
        require(!GL11.glIsTexture(color)&&!GL11.glIsTexture(depthColor)&&!GL30.glIsFramebuffer(fbo)&&!GL30.glIsFramebuffer(depthFbo),"GL allocation survived retirement");
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        var mc=Minecraft.getInstance();
        if(done||event.phase!=TickEvent.Phase.END||!(mc.screen instanceof TitleScreen)||mc.getOverlay()!=null)return;
        done=true;
        try {
            buffers(OldOneRenderer.class,"DEFERRED_BUFFERS");buffers(OldOneParticles.class,"BUFFERS");
            targets(CalamitasArenaRenderer.class);targets(NecromanticEffects.class);
            try{EffectRenderTarget.create(GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE)+1,32,false);throw new AssertionError("invalid target accepted");}
            catch(IllegalArgumentException expected){}
            field(DomainEffects.class,"inside").setBoolean(null,true);DomainEffects.grayscale(0);
            require(field(DomainEffects.class,"chain").get(null)!=null,"domain chain not created");
            OdamaneHaloEffects.targetType();
            require(field(OdamaneHaloEffects.class,"chain").get(null)!=null,"halo chain not created");
            DomainEffects.logout(null);DomainEffects.logout(null);
            require(field(DomainEffects.class,"chain").get(null)==null&&field(OdamaneHaloEffects.class,"chain").get(null)==null,"logout retained chain");
            System.out.println("NATIVE_MEMORY_CLIENT_PASS buffers/targets/invalid-size/logout/repeated-cleanup");
        }catch(Throwable failure){failure.printStackTrace();System.out.println("NATIVE_MEMORY_CLIENT_FAIL "+failure);}
        finally{mc.stop();}
    }
}
