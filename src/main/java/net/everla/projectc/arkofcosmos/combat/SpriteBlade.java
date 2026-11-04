package net.everla.projectc.arkofcosmos.combat;

import net.everla.projectc.arkofcosmos.util.*;
import net.minecraft.world.phys.*;
import javax.imageio.ImageIO;
import java.io.IOException;
import java.util.*;

/** Server-side silhouette of the unchanged legacy item PNG. Three-pixel extrusion matches NativeSprites. */
public final class SpriteBlade {
    private record Run(int x,int y,int width){}
    private final List<Run> RUNS=new ArrayList<>();
    public final int WIDTH,HEIGHT;
    public SpriteBlade(String texture) {
        try(var input=SpriteBlade.class.getResourceAsStream("/assets/arkofcosmos/textures/"+texture+".png")){
            if(input==null)throw new IOException("Missing legacy Ark texture");
            var image=ImageIO.read(input);WIDTH=image.getWidth();HEIGHT=image.getHeight();
            for(int y=0;y<HEIGHT;y++)for(int x=0;x<WIDTH;){
                if((image.getRGB(x,y)>>>24)==0){x++;continue;}
                int start=x++;while(x<WIDTH&&(image.getRGB(x,y)>>>24)!=0)x++;
                RUNS.add(new Run(start,y,x-start));
            }
        }catch(IOException e){throw new ExceptionInInitializerError(e);}
    }
    public boolean intersects(AABB target,Vec3 hilt,AttackFrame plane,double angle){
        Vec3 x=plane.direction(angle+Math.PI/4),y=plane.direction(angle+Math.PI*3/4),normal=plane.normal();
        for(Run r:RUNS){Vec3 center=hilt.add(x.scale((r.x+r.width*.5)/16)).add(y.scale((r.y+.5-HEIGHT)/16));
            if(ArkGeometry.box(target,center,x,y,normal,r.width/32d,1d/32,1.5/16))return true;
        }return false;
    }
    private record Point(double x,double y){
        Point rotate(double a){return new Point(x*Math.cos(a)-y*Math.sin(a),x*Math.sin(a)+y*Math.cos(a));}
    }
    /** Continuous rotation: contact can only begin at a vertex/edge crossing (or at the initial pose). */
    public boolean sweeps(AABB target,Vec3 hilt,AttackFrame plane,double from,double to){
        if(intersects(target,hilt,plane,from)||intersects(target,hilt,plane,to))return true;
        List<Point> polygon=section(target,hilt,plane);if(polygon.size()<3)return false;
        double delta=to-from;
        for(Run r:RUNS){
            Point[] rectangle={new Point(r.x/16d,(r.y-HEIGHT)/16d),new Point((r.x+r.width)/16d,(r.y-HEIGHT)/16d),new Point((r.x+r.width)/16d,(r.y+1-HEIGHT)/16d),new Point(r.x/16d,(r.y+1-HEIGHT)/16d)};
            for(int i=0;i<4;i++)rectangle[i]=rectangle[i].rotate(from+Math.PI/4);
            for(Point point:rectangle)for(int j=0;j<polygon.size();j++)if(arcCrosses(point,delta,polygon.get(j),polygon.get((j+1)%polygon.size())))return true;
            for(Point point:polygon)for(int j=0;j<4;j++)if(arcCrosses(point,-delta,rectangle[j],rectangle[(j+1)%4]))return true;
        }return false;
    }
    private static boolean arcCrosses(Point start,double delta,Point a,Point b){
        double dx=b.x-a.x,dy=b.y-a.y,aa=dx*dx+dy*dy;if(aa==0)return false;
        double bb=2*(a.x*dx+a.y*dy),cc=a.x*a.x+a.y*a.y-start.x*start.x-start.y*start.y,disc=bb*bb-4*aa*cc;
        if(disc<0)return false;
        for(double t:new double[]{(-bb-Math.sqrt(disc))/(2*aa),(-bb+Math.sqrt(disc))/(2*aa)})if(t>=0&&t<=1){
            double angle=Math.atan2(a.y+t*dy,a.x+t*dx)-Math.atan2(start.y,start.x);
            double directed=(delta>=0?angle:-angle)%(Math.PI*2);if(directed<0)directed+=Math.PI*2;
            if(directed<=Math.abs(delta)+1e-12)return true;
        }return false;
    }
    /** Project the exact intersection of the target AABB with the blade's thickness slab. */
    private static List<Point> section(AABB box,Vec3 hilt,AttackFrame plane){
        Vec3[] corners=new Vec3[8];List<Point> points=new ArrayList<>();Vec3 normal=plane.normal();double depth=1.5/16;
        for(int i=0;i<8;i++){
            Vec3 p=new Vec3((i&1)==0?box.minX:box.maxX,(i&2)==0?box.minY:box.maxY,(i&4)==0?box.minZ:box.maxZ).subtract(hilt);corners[i]=p;
            if(Math.abs(p.dot(normal))<=depth)points.add(new Point(p.dot(plane.forward()),p.dot(plane.right())));
        }
        for(int i=0;i<8;i++)for(int bit:new int[]{1,2,4})if((i&bit)==0){Vec3 a=corners[i],b=corners[i|bit];double da=a.dot(normal),db=b.dot(normal);
            if(da==db)continue;for(double edge:new double[]{-depth,depth}){double t=(edge-da)/(db-da);if(t>=0&&t<=1){Vec3 p=a.lerp(b,t);points.add(new Point(p.dot(plane.forward()),p.dot(plane.right())));}}
        }
        points.sort(Comparator.comparingDouble(Point::x).thenComparingDouble(Point::y));if(points.size()<3)return points;
        List<Point> hull=new ArrayList<>();for(Point p:points){while(hull.size()>=2&&cross(hull.get(hull.size()-2),hull.get(hull.size()-1),p)<=0)hull.remove(hull.size()-1);hull.add(p);}
        int lower=hull.size();for(int i=points.size()-2;i>=0;i--){Point p=points.get(i);while(hull.size()>lower&&cross(hull.get(hull.size()-2),hull.get(hull.size()-1),p)<=0)hull.remove(hull.size()-1);hull.add(p);}hull.remove(hull.size()-1);return hull;
    }
    private static double cross(Point a,Point b,Point c){return (b.x-a.x)*(c.y-a.y)-(b.y-a.y)*(c.x-a.x);}
}
