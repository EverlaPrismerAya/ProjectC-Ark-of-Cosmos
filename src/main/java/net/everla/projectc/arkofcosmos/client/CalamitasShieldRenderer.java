package net.everla.projectc.arkofcosmos.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.everla.projectc.arkofcosmos.client.render.ArkFxDraw;
import net.everla.projectc.arkofcosmos.client.render.NativeSprites;
import net.everla.projectc.arkofcosmos.client.render.OculusShaderCompat;
import net.everla.projectc.arkofcosmos.entity.SupremeCalamitasEntity;
import net.everla.projectc.arkofcosmos.entity.CalamitasBrotherEntity;
import net.everla.projectc.arkofcosmos.util.AttackFrame;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

/** Pinned forcefield/skull draw parameters, independent of the mob's native damage policy. */
final class CalamitasShieldRenderer {
    private CalamitasShieldRenderer() {}
    static void renderBrother(CalamitasBrotherEntity entity,float partial,PoseStack pose) {
        ArkFxDraw.matrix=pose.last().pose();ArkFxDraw.camera=entity.getPosition(partial);ArkFxDraw.fadeEye=null;
        Vec3 center=entity.getPosition(partial).add(0,entity.getBbHeight()/2,0);
        double scale=entity.getBbHeight()*32/ArkFxDraw.size("calamitas_forcefield")[0];
        brotherField(center,scale,((entity.level().getGameTime()%72000)+partial)/20d);
    }
    private static void brotherField(Vec3 center,double scale,double time) {
        if(OculusShaderCompat.defer(()->brotherField(center,scale,time),true))return;
        field("calamitas_forcefield",center,NativeSprites.billboard(),0,scale,
                palette(148,0,211,.75f),palette(255,0,0,.75f),time);
    }
    static void render(SupremeCalamitasEntity entity,float partial,PoseStack pose) {
        var view=entity.view();var state=view.getCompound("Shield");double pixels=view.getDouble("Pixels");
        if(state.isEmpty() || pixels<=0)return;
        ArkFxDraw.matrix=pose.last().pose();ArkFxDraw.camera=entity.getPosition(partial);ArkFxDraw.fadeEye=null;
        Vec3 center=entity.getPosition(partial).add(0,entity.getBbHeight()/2,0);
        draw(center,state,view.getBoolean("Invulnerable"),entity.getHealth()/entity.getMaxHealth(),pixels,
                ((entity.level().getGameTime()%72000)+partial)/20d);
    }
    private static Vec3 vector(CompoundTag state,String name){return new Vec3(state.getDouble(name+"X"),state.getDouble(name+"Y"),state.getDouble(name+"Z"));}
    private static void draw(Vec3 at,CompoundTag state,boolean immune,float health,double pixels,double time) {
        if(OculusShaderCompat.defer(()->draw(at,state,immune,health,pixels,time),true))return;
        AttackFrame face=NativeSprites.billboard();float opacity=state.getFloat("Opacity"),scale=state.getFloat("Scale"),pure=state.getFloat("VisualScale");
        boolean post=state.getBoolean("PostMusic"),preparing=state.getBoolean("Preparing"),charging=state.getBoolean("Charging");
        // DrawForcefield changes these display values without touching the NPC's native health.
        if(health<.05f)opacity=.75f;if(health<=.01f)opacity=.6f;
        float flicker=(health<.6f?.1f:0)+(health<.3f?.25f:0)+(post?.61f:0)+(health<.05f?java.util.concurrent.ThreadLocalRandom.current().nextFloat(.7f,1):0)+(health<=.01f?.08f:0);
        opacity*=1+(Math.max(1-flicker,.56f)-1)*(float)Math.pow(Math.cos(time*(3+2*flicker)),24);
        int music=state.getInt("Music");opacity*=music<=0 && music> -30?Math.max(0,Math.min(1,(120-music)/120f)):.75f;
        boolean chargePalette=!immune && state.getBoolean("WillCharge") && !charging;
        float[] main=palette(chargePalette?255:148,0,chargePalette?255:211,opacity);
        // XNA Red*1.4 clamps to red; the pre-charge nested Color.Lerp truncates blue to53.
        float[] secondary=palette(255,0,chargePalette?53:0,opacity);
        if(post)field("calamitas_central_gold",at,face,face.angle(vector(state,"Away")),scale*.088*pure*16/pixels,main,secondary,time);
        field(immune?"calamitas_immune_smear":"calamitas_forcefield",at,face,post || immune?face.angle(vector(state,"Toward")):0,
                scale*(immune?1.35:3)*pure*16/pixels,main,secondary,time);
        float shieldAlpha=state.getFloat("ShieldOpacity");if(shieldAlpha<=0)return;
        Vec3 direction=vector(state,"Direction");
        Vec3 down=new Vec3(0,-1,0).subtract(direction.scale(-direction.y));
        if(down.lengthSqr()<1e-12)down=face.forward().subtract(direction.scale(face.forward().dot(direction)));
        if(down.lengthSqr()<1e-12)down=direction.cross(new Vec3(1,0,0));
        down=down.normalize();
        // Local X follows the locked 3D charge; local Y is the source perpendicular adapted to world up.
        AttackFrame skull=new AttackFrame(direction,down);Vec3 skullAt=at.add(direction.scale(24/pixels));
        double jaw=charging?.71:preparing?-(.04+(-.82-.04)*(Math.sin(time*17.2)*.5+.5)):0;
        float alpha=(int)(255*shieldAlpha)/255f;float[] color={alpha,alpha,alpha,alpha};
        ArkFxDraw.centered("calamitas_shield_bottom",skullAt.add(down.scale(42/pixels)),skull,jaw,16/pixels,16/pixels,color,ArkFxDraw.Blend.ALPHA);
        ArkFxDraw.centered("calamitas_shield_top",skullAt,skull,0,16/pixels,16/pixels,color,ArkFxDraw.Blend.ALPHA);
    }
    private static float[] palette(int r,int g,int b,float opacity){return new float[]{(int)(r*opacity)/255f,(int)(g*opacity)/255f,(int)(b*opacity)/255f};}
    private static void field(String texture,Vec3 at,AttackFrame face,double angle,double scale,float[] main,float[] secondary,double time) {
        if(scale<=1e-10 || ArkFxDraw.forcefieldShader==null)return;
        ArkFxDraw.begin(texture,ArkFxDraw.Blend.ALPHA,false);
        try {
            var shader=ArkFxDraw.forcefieldShader;shader.safeGetUniform("uSupremeSource").set(1f);shader.safeGetUniform("uFixedBoundary").set(0f);
            shader.safeGetUniform("uTime").set((float)time);shader.safeGetUniform("uColor").set(main[0],main[1],main[2]);shader.safeGetUniform("uSecondaryColor").set(secondary[0],secondary[1],secondary[2]);
            RenderSystem.setShaderTexture(1,ArkFxDraw.texture("terraria_perlin"));
            var noise=net.minecraft.client.Minecraft.getInstance().getTextureManager().getTexture(ArkFxDraw.texture("terraria_perlin"));
            noise.setFilter(true,false);RenderSystem.activeTexture(org.lwjgl.opengl.GL13.GL_TEXTURE1);noise.bind();
            RenderSystem.texParameter(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S,org.lwjgl.opengl.GL11.GL_REPEAT);
            RenderSystem.texParameter(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T,org.lwjgl.opengl.GL11.GL_REPEAT);
            RenderSystem.activeTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0);RenderSystem.setShader(()->shader);
            int[] size=ArkFxDraw.size(texture);Vec3 x=face.direction(angle).scale(size[0]*scale/32),y=face.direction(angle+Math.PI/2).scale(size[1]*scale/32);
            var out=Tesselator.getInstance().getBuilder();out.begin(VertexFormat.Mode.QUADS,ArkFxDraw.FORMAT);float[] white={1,1,1,1};
            ArkFxDraw.vertex(out,at.subtract(x).subtract(y),white,0,0,1);ArkFxDraw.vertex(out,at.subtract(x).add(y),white,0,1,1);
            ArkFxDraw.vertex(out,at.add(x).add(y),white,1,1,1);ArkFxDraw.vertex(out,at.add(x).subtract(y),white,1,0,1);BufferUploader.drawWithShader(out.end());
        } finally {ArkFxDraw.end();}
    }
}
