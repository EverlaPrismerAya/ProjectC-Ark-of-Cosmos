package net.everla.projectc.arkofcosmos.client;

import net.everla.projectc.arkofcosmos.client.render.ArkFxDraw;
import net.everla.projectc.arkofcosmos.client.render.NativeSprites;
import net.everla.projectc.arkofcosmos.util.AttackFrame;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;

/** Shared world/depth FX pipeline: emissive sprites, per-fragment owner fade and Oculus deferral. */
public record TrueBiomeDraw(NativeSprites.Draw nativeDraw) {
    private void fx(Runnable action){
        if(nativeDraw.buffers() instanceof MultiBufferSource.BufferSource source)source.endBatch();
        var oldMatrix=ArkFxDraw.matrix;var oldCamera=ArkFxDraw.camera;var oldEye=ArkFxDraw.fadeEye;
        ArkFxDraw.matrix=new org.joml.Matrix4f(nativeDraw.pose().last().pose());ArkFxDraw.camera=nativeDraw.base();
        ArkFxDraw.ownerView(nativeDraw.owner(),Vec3.ZERO);
        try{action.run();}finally{ArkFxDraw.matrix=oldMatrix;ArkFxDraw.camera=oldCamera;ArkFxDraw.fadeEye=oldEye;}
    }
    public void centered(String name,Vec3 at,AttackFrame f,double angle,double sx,double sy,float[] color,boolean additive){
        sprite(name,at,f,angle,sx,sy,NativeSprites.width(name)/2d,NativeSprites.height(name)/2d,color,additive,false,15728880);
    }
    public void sprite(String name,Vec3 at,AttackFrame f,double angle,double sx,double sy,double ox,double oy,float[] color,boolean additive,boolean thick,int light){
        if(thick&&!additive){nativeDraw.sprite(name,at,f,angle,sx,sy,ox,oy,color,false,true,light);return;}
        if(thick){tinted(name,at,f,angle,sx,ox,oy,color,null);return;}
        fx(()->ArkFxDraw.sprite(name,at,f,angle,sx,sy,ox,oy,color,additive?ArkFxDraw.Blend.ADDITIVE:ArkFxDraw.Blend.ALPHA));
    }
    public void tinted(String name,Vec3 at,AttackFrame f,double angle,double scale,double ox,double oy,float[] color,ArkFxDraw.Tint tint){
        fx(()->ArkFxDraw.additiveBlade(name,at,f,angle,scale,ox,oy,color,tint));
    }
    public void region(String name,Vec3 at,AttackFrame f,double angle,double scale,double ox,double oy,float[] color,int left,int top,int width,int height,boolean additive,int light){
        region(name,at,f,angle,scale,scale,ox,oy,color,left,top,width,height,additive,light);
    }
    public void region(String name,Vec3 at,AttackFrame f,double angle,double sx,double sy,double ox,double oy,float[] color,int left,int top,int width,int height,boolean additive,int light){
        if(!additive&&light!=15728880){nativeDraw.region(name,at,f,angle,sx,sy,ox,oy,color,left,top,width,height,false,light);return;}
        fx(()->ArkFxDraw.sprite(name,at,f,angle,sx,sy,ox,oy,color,additive?ArkFxDraw.Blend.ADDITIVE:ArkFxDraw.Blend.ALPHA,left,top,width,height));
    }
    public void thickRegion(String name,Vec3 at,AttackFrame f,double angle,double scale,double ox,double oy,float[] color,int left,int top,int width,int height,boolean additive,int light){
        thickRegion(name,at,f,angle,scale,scale,ox,oy,color,left,top,width,height,additive,light);
    }
    public void thickRegion(String name,Vec3 at,AttackFrame f,double angle,double sx,double sy,double ox,double oy,float[] color,int left,int top,int width,int height,boolean additive,int light){
        if(!additive){nativeDraw.thickRegion(name,at,f,angle,sx,sy,ox,oy,color,left,top,width,height,false,light);return;}
        Vec3 depth=f.normal().scale(1.5*sx/16+.002);
        region(name,at.add(depth),f,angle,sx,sy,ox,oy,color,left,top,width,height,true,light);
        region(name,at.subtract(depth),f,angle,sx,sy,ox,oy,color,left,top,width,height,true,light);
    }
}
