package net.everla.projectc.arkofcosmos.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.Path;

/** Opt-in real-client post-chain check; excluded from the release JAR. */
@Mod.EventBusSubscriber(modid = ArkOfCosmos.MOD_ID, value = Dist.CLIENT)
public final class OdamaneHaloClientCheck {
    private static int frames;
    @SubscribeEvent public static void verifyHaloPostChain(ScreenEvent.Render.Post event) throws Exception {
        if (!Boolean.getBoolean("arkofcosmos.haloClientCheck") || !(event.getScreen() instanceof TitleScreen) || ++frames != 20) return;
        var mc=Minecraft.getInstance();
        event.getGuiGraphics().flush();
        var main=mc.getMainRenderTarget();
        main.setClearColor(.125f,.25f,.375f,1);main.clear(Minecraft.ON_OSX);main.bindWrite(true);
        int background;
        try(var image=Screenshot.takeScreenshot(main)) { background=image.getPixelRGBA(4,4); }
        var model=new OdamaneHaloModel(OdamaneHaloModel.createLayer().bakeRoot());model.animate(.75f);
        var pose=event.getGuiGraphics().pose();pose.pushPose();
        pose.translate(event.getGuiGraphics().guiWidth()/2f,event.getGuiGraphics().guiHeight()/2f+20,0);
        pose.scale(48,48,1);model.halo.translateAndRotate(pose);pose.translate(0,0,-.5);
        var type=OdamaneHaloEffects.targetType();
        if(!type.toString().contains("ark_odamane_halo"))throw new AssertionError("Post chain fell back instead of loading");
        var buffers=mc.renderBuffers().bufferSource();RenderSystem.setShaderColor(1,1,1,1);
        model.renderToBuffer(pose,buffers.getBuffer(type),0xF000F0,OverlayTexture.NO_OVERLAY,1,1,1,1);
        buffers.endBatch(type);pose.popPose();OdamaneHaloEffects.composite(event.getPartialTick());
        try(var image=Screenshot.takeScreenshot(main)) {
            int colored=0;
            for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)
                if(image.getPixelRGBA(x,y)!=background)colored++;
            for(int[] corner:new int[][]{{4,4},{image.getWidth()-5,4},{4,image.getHeight()-5},{image.getWidth()-5,image.getHeight()-5}})
                if(image.getPixelRGBA(corner[0],corner[1])!=background)
                    throw new AssertionError("Halo composite overwrote the world background at " + corner[0] + "," + corner[1]);
            if(colored<500)throw new AssertionError("Halo produced no visible client pixels: "+colored);
            Path output=Path.of(System.getProperty("arkofcosmos.haloClientOutput"));image.writeToFile(output);
            System.out.println("ODAMANE_CLIENT_CHECK_PASS: "+colored+" visible pixels; "+output);
        }
        mc.stop();
    }
}
