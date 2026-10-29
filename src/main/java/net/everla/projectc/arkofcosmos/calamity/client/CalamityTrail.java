package net.everla.projectc.arkofcosmos.calamity.client;

import net.everla.projectc.arkofcosmos.util.AttackFrame;
import net.everla.projectc.arkofcosmos.client.render.OculusShaderCompat;
import static net.everla.projectc.arkofcosmos.client.render.ArkFxDraw.*;
import com.mojang.blaze3d.vertex.*;
import org.joml.Matrix4f;
import net.minecraft.world.phys.Vec3;

/** Calamity Team PrimitiveRenderer trail adaptation; see THIRD_PARTY_NOTICES.md. */
public final class CalamityTrail {
    private CalamityTrail() {}
    /** PrimitiveRenderer.AssignPoints/AssignVertices/AssignIndicesRectangleTrail, including its end samples. */
    public static void trail(Vec3[] source, AttackFrame plane, PoseStack pose, float scale, boolean needle, Vec3 offset) {
        if(source.length<=4||trailShader==null)return;
        Matrix4f trailMatrix = new Matrix4f(pose.last().pose());
        if (OculusShaderCompat.defer(() -> drawTrail(source, plane, trailMatrix, scale, needle, offset))) return;
        drawTrail(source, plane, trailMatrix, scale, needle, offset);
    }

    private static void drawTrail(Vec3[] source, AttackFrame plane, Matrix4f trailMatrix, float scale, boolean needle, Vec3 offset) {
        Vec3[] points=new Vec3[32]; points[0]=source[0].add(offset);points[31]=source[source.length-1].add(offset);
        for(int i=0;i<30;i++){
            double f=i/30d*(source.length-1);int n=(int)f;double t=f-n;
            Vec3 p1=source[n],p2=source[n+1],p0=n>0?source[n-1]:p1.scale(2).subtract(p2),p3=n<source.length-2?source[n+2]:p2.scale(2).subtract(p1);
            double t2=t*t,t3=t2*t;
            points[i+1]=p0.scale(-.5*t3+t2-.5*t).add(p1.scale(1.5*t3-2.5*t2+1)).add(p2.scale(-1.5*t3+2*t2+.5*t)).add(p3.scale(.5*t3-.5*t2)).add(offset);
        }
        begin("scarletdevilstreak",Blend.ALPHA,true);
        BufferBuilder buffer=Tesselator.getInstance().getBuilder();buffer.begin(VertexFormat.Mode.TRIANGLES,FORMAT);
        Matrix4f old=matrix;Vec3 oldCamera=camera;matrix=trailMatrix;camera=Vec3.ZERO;
        Vec3[][] edges=new Vec3[32][2];float[][] colors=new float[32][];float[] widths=new float[32];
        float now=time();
        for(int i=0;i<32;i++){
            Vec3 tangent=(i==31?points[i].subtract(points[i-1]):points[i+1].subtract(points[i])).normalize();
            Vec3 side=plane.normal().cross(tangent).normalize();
            float completion=(i-1)/31f;
            float width=(float)((needle?14:22)*scale*Math.pow(1-completion,needle?2:3));
            widths[i]=width;edges[i][0]=points[i].subtract(side.scale(width/16));edges[i][1]=points[i].add(side.scale(width/16));
            float opacity=needle?.6f:1f;
            float fade=(float)ArkEffects.clamp((1-completion)/.36)*opacity;
            float pulse=(float)ArkEffects.lerp(.65,1,Math.cos(-now*3)*.5+.5);
            float[] color=ArkEffects.mix(needle?ArkEffects.CYAN:hsl(.55f,1,.8f,1),needle?ArkEffects.rgb(220,20,60):ArkEffects.rgb(175,238,238),Math.sin(completion*Math.PI*1.6-now*4)*.5+.5);
            colors[i]=ArkEffects.mul(ArkEffects.mix(ArkEffects.WHITE,color,pulse),fade);
        }
        // Calamity intentionally emits PositionsIndex - 2 segments.
        for(int i=0;i<30;i++)for(int index:new int[]{i*2,i*2+1,i*2+2,i*2+2,i*2+1,i*2+3}){
            int point=index/2,edge=index%2;float width=widths[point];
            vertex(buffer,edges[point][edge],colors[point],(point-1)/31f,.5f+(edge==0?-1:1)*width*.5f,width);
        }
        BufferUploader.drawWithShader(buffer.end());matrix=old;camera=oldCamera;end();
    }
}
