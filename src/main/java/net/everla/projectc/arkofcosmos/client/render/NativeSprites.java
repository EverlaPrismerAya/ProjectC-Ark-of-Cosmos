package net.everla.projectc.arkofcosmos.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.*;
import net.everla.projectc.arkofcosmos.util.AttackFrame;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Original sprites through Minecraft's built-in shaders only; no ArkFxDraw shader dependency. */
public final class NativeSprites extends RenderType {
    private NativeSprites(){super("elements_sprites",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,256,false,true,()->{},()->{});}
    private record Image(int width,int height,List<int[]> contour) {}
    private static final Map<String,Image> IMAGES=new HashMap<>();
    private static final Map<ResourceLocation,RenderType> ADDITIVE=new HashMap<>();
    private static RenderType additive(ResourceLocation texture){return ADDITIVE.computeIfAbsent(texture,t->create("elements_additive",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,256,false,true,
            CompositeState.builder().setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER).setTextureState(new TextureStateShard(t,false,false))
                    .setTransparencyState(LIGHTNING_TRANSPARENCY).setCullState(NO_CULL).setLightmapState(LIGHTMAP).setOverlayState(OVERLAY).setWriteMaskState(COLOR_WRITE).createCompositeState(false)));}
    public static ResourceLocation texture(String name){return ResourceLocation.fromNamespaceAndPath("arkofcosmos","textures/"+name+".png");}
    public static void clear(){IMAGES.clear();}
    private static Image image(String name){return IMAGES.computeIfAbsent(name,key->{
        try(var stream=Minecraft.getInstance().getResourceManager().getResourceOrThrow(texture(key)).open();var image=NativeImage.read(stream)){
            List<int[]> edges=new ArrayList<>();int w=image.getWidth(),h=image.getHeight();
            for(int y=0;y<h;y++)for(int x=0;x<w;x++)if((image.getPixelRGBA(x,y)>>>24)!=0){
                if(x==0||(image.getPixelRGBA(x-1,y)>>>24)==0)edges.add(new int[]{x,y,0});
                if(x==w-1||(image.getPixelRGBA(x+1,y)>>>24)==0)edges.add(new int[]{x,y,1});
                if(y==0||(image.getPixelRGBA(x,y-1)>>>24)==0)edges.add(new int[]{x,y,2});
                if(y==h-1||(image.getPixelRGBA(x,y+1)>>>24)==0)edges.add(new int[]{x,y,3});
            }return new Image(w,h,edges);
        }catch(java.io.IOException ex){throw new java.io.UncheckedIOException(ex);}
    });}
    public static int width(String name){return image(name).width;}
    public static int height(String name){return image(name).height;}
    public record Draw(PoseStack pose,MultiBufferSource buffers,Vec3 base,int owner) {
        public void centered(String name,Vec3 at,AttackFrame f,double angle,double sx,double sy,float[] color,boolean additive){
            sprite(name,at,f,angle,sx,sy,width(name)/2d,height(name)/2d,color,additive,false,15728880);
        }
        public void sprite(String name,Vec3 at,AttackFrame f,double angle,double sx,double sy,double ox,double oy,float[] color,boolean additive,boolean thick,int light){
            sprite(name,at,f,angle,sx,sy,ox,oy,color,additive,thick,light,0);
        }
        public void sprite(String name,Vec3 at,AttackFrame f,double angle,double sx,double sy,double ox,double oy,float[] color,boolean additive,boolean thick,int light,double depthBias){
            Image img=image(name);Vec3 x=f.direction(angle).scale(sx/16),y=f.direction(angle+Math.PI/2).scale(sy/16),n=f.normal();
            RenderType type=additive?additive(texture(name)):thick?RenderType.entityCutoutNoCull(texture(name)):RenderType.entityTranslucentEmissive(texture(name));
            VertexConsumer out=buffers.getBuffer(type);double depth=thick?1.5*sx/16+(name.endsWith("_glow")?.002:0)+depthBias:0;
            for(int face=0;face<(thick?2:1);face++){
                Vec3 origin=at.add(n.scale(face==0?depth:-depth));
                // Subdivide only for the owner's near-camera fade.
                boolean fade=ownerView();int cols=fade?Math.max(1,(int)Math.ceil(Math.abs(sx)*img.width/16/.5)):1,rows=fade?Math.max(1,(int)Math.ceil(Math.abs(sy)*img.height/16/.5)):1;
                for(int col=0;col<cols;col++)for(int row=0;row<rows;row++){
                    float u0=col/(float)cols,u1=(col+1f)/cols,v0=row/(float)rows,v1=(row+1f)/rows;
                    vertex(out,origin.add(x.scale(u0*img.width-ox)).add(y.scale(v0*img.height-oy)),u0,v0,n,color,light,additive);
                    vertex(out,origin.add(x.scale(u0*img.width-ox)).add(y.scale(v1*img.height-oy)),u0,v1,n,color,light,additive);
                    vertex(out,origin.add(x.scale(u1*img.width-ox)).add(y.scale(v1*img.height-oy)),u1,v1,n,color,light,additive);
                    vertex(out,origin.add(x.scale(u1*img.width-ox)).add(y.scale(v0*img.height-oy)),u1,v0,n,color,light,additive);
                }
            }
            if(!thick)return;
            for(int[] edge:img.contour){int px=edge[0],py=edge[1],side=edge[2];double ax=px,ay=py,bx=px,by=py;
                if(side<2){ax+=side;bx=ax;by++;}else{ay+=side-2;by=ay;bx++;}
                Vec3 a=at.add(x.scale(ax-ox)).add(y.scale(ay-oy)),b=at.add(x.scale(bx-ox)).add(y.scale(by-oy));
                Vec3 normal=side<2?x.normalize().scale(side==0?-1:1):y.normalize().scale(side==2?-1:1);
                float u=(px+.5f)/img.width,v=(py+.5f)/img.height;
                vertex(out,a.add(n.scale(depth)),u,v,normal,color,light,false);vertex(out,b.add(n.scale(depth)),u,v,normal,color,light,false);
                vertex(out,b.add(n.scale(-depth)),u,v,normal,color,light,false);vertex(out,a.add(n.scale(-depth)),u,v,normal,color,light,false);
            }
        }
        private boolean ownerView(){Minecraft mc=Minecraft.getInstance();return mc.player!=null&&mc.getCameraEntity()==mc.player&&mc.player.getId()==owner&&mc.options.getCameraType().isFirstPerson();}
        private void vertex(VertexConsumer out,Vec3 at,float u,float v,Vec3 normal,float[] color,int light,boolean additive){
            double fade=1;if(ownerView()){double d=at.distanceTo(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition());double t=Math.max(0,Math.min(1,(d-.5)/2.5));fade=.12+.88*t*t*(3-2*t);}
            Vec3 local=at.subtract(base);double rgb=1;
            out.vertex(pose.last().pose(),(float)local.x,(float)local.y,(float)local.z).color(component(color[0]*rgb),component(color[1]*rgb),component(color[2]*rgb),component(color[3]*fade))
                    .uv(u,v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(pose.last().normal(),(float)normal.x,(float)normal.y,(float)normal.z).endVertex();
        }
    }
    private static int component(double value){return (int)(Math.max(0,Math.min(1,value))*255);}
    public static AttackFrame billboard(){var camera=Minecraft.getInstance().gameRenderer.getMainCamera();var look=camera.getLookVector();var up=camera.getUpVector();Vec3 forward=new Vec3(look.x(),look.y(),look.z()),vertical=new Vec3(up.x(),up.y(),up.z());return new AttackFrame(forward.cross(vertical).normalize(),vertical.scale(-1));}
}
