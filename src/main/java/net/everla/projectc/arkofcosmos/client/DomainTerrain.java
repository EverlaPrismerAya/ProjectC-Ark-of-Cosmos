package net.everla.projectc.arkofcosmos.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.everla.projectc.arkofcosmos.client.render.ArkFxDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/** Cached collision-shape surface grid and terrain/domain intersection. */
final class DomainTerrain {
    private record Segment(Vec3 a,Vec3 b) {}
    private static final List<Segment> grid=new ArrayList<>(),seam=new ArrayList<>();
    private static Vec3 cachedCenter;
    private static long updated=Long.MIN_VALUE;
    private static Object cachedLevel;

    static void render(Vec3 center){
        var level=Minecraft.getInstance().level;
        if(level==null)return;
        if(level!=cachedLevel||!center.equals(cachedCenter)||level.getGameTime()-updated>=20){
            grid.clear();seam.clear();cachedCenter=center;cachedLevel=level;updated=level.getGameTime();
            BlockPos min=BlockPos.containing(center.add(-33,-33,-33)),max=BlockPos.containing(center.add(33,33,33));
            for(BlockPos pos:BlockPos.betweenClosed(min,max)){
                if(pos.getY()<level.getMinBuildHeight()||pos.getY()>=level.getMaxBuildHeight()
                        ||Vec3.atCenterOf(pos).distanceToSqr(center)>34*34||!level.hasChunkAt(pos))continue;
                var state=level.getBlockState(pos);
                if(state.isAir())continue;
                for(AABB box:state.getCollisionShape(level,pos).toAabbs()){
                    AABB b=box.move(pos);
                    for(Direction direction:Direction.values()){
                        Vec3[] face=face(b,direction);
                        double plane=switch(direction.getAxis()){case X->face[0].x-pos.getX();case Y->face[0].y-pos.getY();case Z->face[0].z-pos.getZ();};
                        boolean boundary=Math.abs(plane-(direction.getAxisDirection()==Direction.AxisDirection.POSITIVE?1:0))<1e-6;
                        if(boundary&&level.getBlockState(pos.relative(direction)).isSolidRender(level,pos.relative(direction)))continue;
                        for(int i=0;i<4;i++)clipGrid(face[i],face[(i+1)%4],center);
                        clipGrid(face[0],face[2],center);clipGrid(face[1],face[3],center);
                        intersect(face[0],face[1],face[2],center);
                        intersect(face[0],face[2],face[3],center);
                    }
                }
            }
        }
        boolean blend=org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_BLEND);
        boolean cull=org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_CULL_FACE);
        boolean write=org.lwjgl.opengl.GL11.glGetBoolean(org.lwjgl.opengl.GL11.GL_DEPTH_WRITEMASK);
        var shader=RenderSystem.getShader();float[] color=RenderSystem.getShaderColor().clone();
        RenderSystem.enableDepthTest();RenderSystem.depthMask(false);RenderSystem.disableCull();
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.setShaderColor(1,1,1,1);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        try{
            var buffer=Tesselator.getInstance().getBuilder();
            buffer.begin(VertexFormat.Mode.DEBUG_LINES,DefaultVertexFormat.POSITION_COLOR);
            for(Segment line:grid){vertex(buffer,line.a,105);vertex(buffer,line.b,105);}
            for(Segment line:seam){vertex(buffer,line.a,255);vertex(buffer,line.b,255);}
            BufferUploader.drawWithShader(buffer.end());
            buffer.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);
            for(Segment line:seam){
                vertex(buffer,line.a,150);vertex(buffer,line.b,150);
                vertex(buffer,rise(line.b,center),0);vertex(buffer,rise(line.a,center),0);
            }
            BufferUploader.drawWithShader(buffer.end());
        }finally{
            RenderSystem.depthMask(write);if(cull)RenderSystem.enableCull();
            if(!blend)RenderSystem.disableBlend();
            RenderSystem.setShaderColor(color[0],color[1],color[2],color[3]);RenderSystem.setShader(()->shader);
        }
    }
    private static void vertex(BufferBuilder buffer,Vec3 p,int alpha){
        // Small camera-facing offset avoids coplanar fighting without showing hidden faces.
        Vec3 v=p.subtract(ArkFxDraw.camera);v=v.scale(Math.max(0,1-.008/Math.max(.008,v.length())));
        buffer.vertex(ArkFxDraw.matrix,(float)v.x,(float)v.y,(float)v.z).color(255,255,255,alpha).endVertex();
    }
    private static Vec3 rise(Vec3 p,Vec3 center){
        double y=Math.min(center.y+31.99,p.y+1.8),r=Math.sqrt(Math.max(0,32*32-(y-center.y)*(y-center.y)));
        Vec3 radial=new Vec3(p.x-center.x,0,p.z-center.z).normalize().scale(r);
        return new Vec3(center.x+radial.x,y,center.z+radial.z);
    }
    private static void clipGrid(Vec3 a,Vec3 b,Vec3 center){
        Vec3 d=b.subtract(a),o=a.subtract(center);double aa=d.lengthSqr();
        if(aa<1e-12)return;
        double dot=o.dot(d),disc=dot*dot-aa*(o.lengthSqr()-32*32);
        if(disc<0)return;
        double lo=Math.max(0,(-dot-Math.sqrt(disc))/aa),hi=Math.min(1,(-dot+Math.sqrt(disc))/aa);
        if(lo<hi)grid.add(new Segment(a.add(d.scale(lo)),a.add(d.scale(hi))));
    }
    private static void intersect(Vec3 a,Vec3 b,Vec3 c,Vec3 center){
        Vec3[] v={a,b,c};Vec3 first=null;
        for(int i=0;i<3;i++){
            Vec3 p=v[i],q=v[(i+1)%3];double fp=p.distanceToSqr(center)-1024,fq=q.distanceToSqr(center)-1024;
            if((fp<0)==(fq<0))continue;
            Vec3 hit=p.lerp(q,fp/(fp-fq));
            if(first==null)first=hit;else{seam.add(new Segment(first,hit));return;}
        }
    }
    private static Vec3[] face(AABB b,Direction d){
        double x=b.minX,X=b.maxX,y=b.minY,Y=b.maxY,z=b.minZ,Z=b.maxZ;
        return switch(d){
            case DOWN,UP->{double h=d==Direction.UP?Y:y;yield new Vec3[]{new Vec3(x,h,z),new Vec3(X,h,z),new Vec3(X,h,Z),new Vec3(x,h,Z)};}
            case NORTH,SOUTH->{double h=d==Direction.SOUTH?Z:z;yield new Vec3[]{new Vec3(x,y,h),new Vec3(X,y,h),new Vec3(X,Y,h),new Vec3(x,Y,h)};}
            case WEST,EAST->{double h=d==Direction.EAST?X:x;yield new Vec3[]{new Vec3(h,y,z),new Vec3(h,Y,z),new Vec3(h,Y,Z),new Vec3(h,y,Z)};}
        };
    }
}
