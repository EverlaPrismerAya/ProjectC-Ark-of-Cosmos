package net.everla.projectc.arkofcosmos.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.*;
import net.everla.projectc.arkofcosmos.entity.SatsukiRinEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Phase-two replacement for RF's scarlet rain: a horizontal magic circle of music notes 20 blocks above
 * Satsuki Rin's head. Shape and colour follow her current native attack; changes crossfade over 10 ticks.
 */
public final class SatsukiNoteCircles {
    private SatsukiNoteCircles(){}
    private static final ResourceLocation NOTE=ResourceLocation.fromNamespaceAndPath("minecraft","textures/particle/note.png");
    private static final double HEIGHT=20,RADIUS=6,SPACING=.8;
    private static final float NOTE_SIZE=.7f,FADE=10;
    private record Pattern(float r,float g,float b,List<Vec3> points){}
    private static final Map<String,Pattern> PATTERNS=new HashMap<>();
    private static final class State {String current="NONE",previous;long changed=Long.MIN_VALUE/2;}
    private static final Map<SatsukiRinEntity,State> STATES=new WeakHashMap<>();

    static {
        // NONE: idle double ring.
        PATTERNS.put("NONE",pattern(.95f,.95f,1f,ring(RADIUS),ring(RADIUS*.5)));
        // RANGED (bow): ring, inner ring and a cross of four spokes.
        PATTERNS.put("RANGED",pattern(.35f,1f,.45f,ring(RADIUS),ring(RADIUS*.35),star(4,1,RADIUS,true)));
        // FIRE (fireball / fire rain): pentagram.
        PATTERNS.put("FIRE",pattern(1f,.4f,.15f,ring(RADIUS),star(5,2,RADIUS,false)));
        // TORNADO: three spiral arms.
        PATTERNS.put("TORNADO",pattern(.3f,.9f,1f,ring(RADIUS),spirals(3)));
        // ZOMBIE (summons, monoliths, damned): hexagram.
        PATTERNS.put("ZOMBIE",pattern(.75f,.35f,1f,ring(RADIUS),star(6,2,RADIUS,false)));
        // ROAR: double ring with eight rays.
        PATTERNS.put("ROAR",pattern(1f,.9f,.25f,ring(RADIUS),ring(RADIUS*.6),rays(8,RADIUS*.6,RADIUS)));
        // CLOUD: six satellite circles.
        PATTERNS.put("CLOUD",pattern(.45f,.6f,1f,ring(RADIUS),satellites(6,RADIUS*.6,RADIUS*.3)));
        // SACRIFICE: octagram {8/3}.
        PATTERNS.put("SACRIFICE",pattern(.95f,.15f,.35f,ring(RADIUS),star(8,3,RADIUS,false)));
    }

    public static boolean active(SatsukiRinEntity rin){return rin.isSecondPhase()&&!rin.isDeadOrDying();}
    /** Called from SatsukiRinRenderer with its pose at the entity render origin, so the circle follows the drawn model exactly. */
    public static void render(SatsukiRinEntity rin,float partial,PoseStack pose,net.minecraft.client.renderer.MultiBufferSource buffers){
        if(!active(rin))return;
        long now=rin.level().getGameTime();
        State state=STATES.computeIfAbsent(rin,k->new State());
        String attack=PATTERNS.containsKey(rin.currentAttack())?rin.currentAttack():"NONE";
        if(!attack.equals(state.current)){state.previous=state.current;state.current=attack;state.changed=now;}
        float t=Math.min(1,(now-state.changed+partial)/FADE);
        Vec3 center=new Vec3(0,rin.getBbHeight()+HEIGHT,0);
        double spin=(now+partial)*.02;
        var facing=Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation();
        var consumer=buffers.getBuffer(RenderType.entityTranslucentEmissive(NOTE));
        if(t<1&&state.previous!=null)draw(pose,consumer,facing,center,spin,PATTERNS.get(state.previous),1-t,now);
        draw(pose,consumer,facing,center,spin,PATTERNS.get(state.current),t,now);
    }
    /** Bounds of the circle relative to the entity, for frustum culling. */
    public static net.minecraft.world.phys.AABB bounds(SatsukiRinEntity rin){
        var box=rin.getBoundingBox();double top=box.minY+rin.getBbHeight()+HEIGHT;
        return new net.minecraft.world.phys.AABB(box.getCenter().x-RADIUS-1,box.minY,box.getCenter().z-RADIUS-1,box.getCenter().x+RADIUS+1,top+1,box.getCenter().z+RADIUS+1);
    }

    private static void draw(PoseStack pose,com.mojang.blaze3d.vertex.VertexConsumer consumer,org.joml.Quaternionf facing,Vec3 center,double spin,Pattern pattern,float alpha,long now){
        if(pattern==null||alpha<=.01f)return;
        double cos=Math.cos(spin),sin=Math.sin(spin);
        for(int i=0;i<pattern.points().size();i++){
            Vec3 p=pattern.points().get(i);
            // Slight per-note shimmer so the circle reads as individual notes rather than a solid line.
            float shimmer=.8f+.2f*(float)Math.sin(now*.15+i*1.7);
            pose.pushPose();
            pose.translate(center.x+p.x*cos-p.z*sin,center.y,center.z+p.x*sin+p.z*cos);
            pose.mulPose(facing);pose.scale(NOTE_SIZE,NOTE_SIZE,NOTE_SIZE);
            var m=pose.last().pose();var n=pose.last().normal();
            int r=(int)(pattern.r()*shimmer*255),g=(int)(pattern.g()*shimmer*255),b=(int)(pattern.b()*shimmer*255),a=(int)(alpha*255);
            vertex(consumer,m,n,-.5f,-.5f,0,1,r,g,b,a);
            vertex(consumer,m,n,.5f,-.5f,1,1,r,g,b,a);
            vertex(consumer,m,n,.5f,.5f,1,0,r,g,b,a);
            vertex(consumer,m,n,-.5f,.5f,0,0,r,g,b,a);
            pose.popPose();
        }
    }
    private static void vertex(com.mojang.blaze3d.vertex.VertexConsumer c,org.joml.Matrix4f m,org.joml.Matrix3f n,float x,float y,float u,float v,int r,int g,int b,int a){
        c.vertex(m,x,y,0).color(r,g,b,a).uv(u,v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(n,0,0,1).endVertex();
    }

    // ---- Shapes in the local horizontal plane (x,z), sampled at roughly SPACING blocks per note. ----
    @SafeVarargs private static Pattern pattern(float r,float g,float b,List<Vec3>... parts){
        List<Vec3> all=new ArrayList<>();for(var part:parts)all.addAll(part);return new Pattern(r,g,b,List.copyOf(all));
    }
    private static Vec3 polar(double radius,double angle){return new Vec3(radius*Math.cos(angle),0,radius*Math.sin(angle));}
    private static List<Vec3> ring(double radius){return arc(Vec3.ZERO,radius);}
    private static List<Vec3> arc(Vec3 c,double radius){
        int count=Math.max(6,(int)Math.round(2*Math.PI*radius/SPACING));List<Vec3> out=new ArrayList<>();
        for(int i=0;i<count;i++)out.add(c.add(polar(radius,2*Math.PI*i/count)));return out;
    }
    private static List<Vec3> line(Vec3 a,Vec3 b,boolean includeEnd){
        int count=Math.max(1,(int)Math.round(a.distanceTo(b)/SPACING));List<Vec3> out=new ArrayList<>();
        for(int i=0;i<(includeEnd?count+1:count);i++)out.add(a.lerp(b,(double)i/count));return out;
    }
    /** Star polygon {n/k}; with spokes=true it draws n spokes from the centre instead. */
    private static List<Vec3> star(int n,int k,double radius,boolean spokes){
        List<Vec3> out=new ArrayList<>();
        for(int i=0;i<n;i++){
            double a=2*Math.PI*i/n-Math.PI/2;
            if(spokes)out.addAll(line(Vec3.ZERO,polar(radius,a),false));
            else out.addAll(line(polar(radius,a),polar(radius,2*Math.PI*((i+k)%n)/n-Math.PI/2),false));
        }
        return out;
    }
    private static List<Vec3> rays(int n,double from,double to){
        List<Vec3> out=new ArrayList<>();
        for(int i=0;i<n;i++){double a=2*Math.PI*i/n;out.addAll(line(polar(from,a),polar(to,a),false).subList(1,Math.max(1,(int)Math.round((to-from)/SPACING))));}
        return out;
    }
    private static List<Vec3> spirals(int arms){
        List<Vec3> out=new ArrayList<>();
        for(int k=0;k<arms;k++)for(double s=.08;s<=1;s+=.06)out.add(polar(RADIUS*s,s*1.5*Math.PI+2*Math.PI*k/arms));
        return out;
    }
    private static List<Vec3> satellites(int n,double at,double radius){
        List<Vec3> out=new ArrayList<>();for(int i=0;i<n;i++)out.addAll(arc(polar(at,2*Math.PI*i/n),radius));return out;
    }
}
