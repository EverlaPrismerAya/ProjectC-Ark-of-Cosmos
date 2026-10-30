package net.everla.projectc.arkofcosmos.calamity.client;

import net.everla.projectc.arkofcosmos.util.AttackFrame;
import net.everla.projectc.arkofcosmos.client.render.ArkFxDraw;
import net.minecraft.world.phys.Vec3;
import static net.everla.projectc.arkofcosmos.client.render.ArkFxDraw.Blend.*;
import static net.everla.projectc.arkofcosmos.calamity.client.ArkEffects.*;

/** Direct ports of Calamity's particle Update/CustomDraw methods (60 Hz). */
final class ArkFxParticle {
    enum Type { SPARKLE, CRIT, FLARE, SMOKE, SQUISH, RING, BLOOM, LIGHT, LINE, BLOOM_LINE, SMEAR, CONSTELLATION }
    Type type;
    Vec3 position, previous, velocity, line = Vec3.ZERO;
    AttackFrame plane;
    float[] color, bloom;
    double scale, previousScale, rotation, previousRotation, opacity = 1, spin, bloomScale = 1, hueShift;
    double squish = 1, maxSquish = 3, finalScale, originalScale, finalX, finalY, originalX, originalY, expansion;
    int ownerId = -1;
    int time, lifetime, delay, variant;
    boolean glowing, capped, concave, planeSprite;
    String texture;
    private record Frame(Vec3 position,double scale,double rotation,double opacity,double age,float[] color,float[] bloom,Vec3 velocity,AttackFrame plane) {}
    private final Frame[] frames=new Frame[4];
    double born, died=1.01;
    void prepareFrame(){if(frames[0]==null){double birth=born;beginFrame();born=birth;}}
    void capture(int phase){frames[phase]=new Frame(position,scale,rotation,opacity,time,color.clone(),bloom.clone(),velocity,plane);}
    void beginFrame(){born=0;died=1.01;for(int i=0;i<4;i++)capture(i);}


    ArkFxParticle(Type type, Vec3 position, Vec3 velocity, float[] color, double scale, int lifetime, AttackFrame plane) {
        this.type = type; this.position = this.previous = position; this.velocity = velocity; this.color = color;
        this.bloom = color; this.scale = this.previousScale = scale; this.lifetime = lifetime; this.plane = plane;
        this.rotation = this.previousRotation = random() * Math.PI * 2;
        if(type==Type.SPARKLE||type==Type.CRIT||type==Type.FLARE)opacity=0;
        if (type == Type.SMOKE) variant = rng.nextInt(7);
    }
    double completion() { return time / (double)lifetime; }
    void update() {
        previous = position; previousScale = scale; previousRotation = rotation;
        position = position.add(velocity); time++;
        double t = completion(), sign = velocity.dot(plane.forward()) > 0 ? 1 : -1;
        switch (type) {
            case SPARKLE -> {
                opacity = Math.sin(t * Math.PI); velocity = velocity.scale(.95); rotation += spin * sign;
            }
            case CRIT, FLARE -> {
                if (delay > 0) { time--; position = position.subtract(velocity); delay--; return; }
                opacity = Math.sin(Math.PI / 2 + t * Math.PI / 2); velocity = velocity.scale(.8);
                rotation += spin * sign * (t > .5 ? 1 : .5);
                color = shift(color, hueShift); bloom = shift(bloom, hueShift);
            }
            case SMOKE -> {
                scale = t < .2 ? scale + .01 : scale * .975;
                color = shift(color, hueShift); opacity *= .98; rotation += spin * sign; velocity = velocity.scale(.85);
                color = mul(color, clamp((1 - t) / .15));
            }
            case SQUISH -> {
                velocity = velocity.scale(t >= .34 ? .93 : 1.02);
                opacity = t > .5 ? Math.sin(t * Math.PI) * .2 + .8 : Math.sin(t * Math.PI);
                scale *= .95; color = shift(color, hueShift);
            }
            case RING -> {
                scale = lerp(originalScale, finalScale, 1 - Math.pow(1 - t, 4));
                opacity = Math.sin(Math.PI / 2 + t * Math.PI / 2); color = mul(bloom, opacity); velocity = velocity.scale(.95);
            }
            case BLOOM, LIGHT -> {
                opacity = Math.sin(t * Math.PI); color = mul(bloom, opacity); velocity = velocity.scale(.95);
            }
            default -> { }
        }
    }
    void draw(double partial, AttackFrame billboard) {
        if(partial<born||partial>=died)return;
        int n=Math.min(2,(int)(clamp(partial)*3));double fraction=clamp(partial)*3-n;
        Frame a=frames[n],b=frames[n+1];
        if(a==null||b==null){beginFrame();a=frames[n];b=frames[n+1];}
        AttackFrame plane=type==Type.SMEAR || type==Type.CONSTELLATION
                ? AttackFrame.interpolateView(a.plane,b.plane,fraction):this.plane;
        Vec3 p=a.position.lerp(b.position,fraction);
        double s=lerp(a.scale,b.scale,fraction),r=lerp(a.rotation,b.rotation,fraction);
        double opacity=lerp(a.opacity,b.opacity,fraction),completion=lerp(a.age,b.age,fraction)/lifetime;
        float[] color=mix(a.color,b.color,fraction),bloom=mix(a.bloom,b.bloom,fraction);
        Vec3 velocity=a.velocity.lerp(b.velocity,fraction);
        AttackFrame face = planeSprite ? plane : billboard;
        switch (type) {
            case SPARKLE, CRIT -> {
                String star = type == Type.SPARKLE ? "sparkle" : "thinsparkle";
                double ratio = (double)ArkFxDraw.size(star)[1] / ArkFxDraw.size("bloomcircle")[1];
                centered("bloomcircle", p, face, 0, s * bloomScale * ratio, s * bloomScale * ratio, mul(bloom, opacity * .5), ADDITIVE);
                if (type == Type.SPARKLE)
                    centered(star, p, face, r + Math.PI / 4, s * .75, s * .75, mul(color, opacity * .5), ADDITIVE);
                centered(star, p, face, r, s, s, mul(color, opacity), ADDITIVE);
            }
            case FLARE -> {
                if (delay > 0) return;
                double ease = 1 - Math.pow(1 - completion, 4);
                double x = lerp(originalX, finalX, ease), y = lerp(originalY, finalY, ease);
                double ratio = (double)ArkFxDraw.size("thinsparkle")[1] / ArkFxDraw.size("bloomcircle")[1];
                centered("bloomcircle", p, face, 0, x * bloomScale * ratio, y * bloomScale * ratio, mul(bloom, opacity * .5), ADDITIVE);
                centered("thinsparkle", p, face, r, x, y, mul(color, opacity), ADDITIVE);
            }
            case SMOKE -> {
                int row = Math.min(5, (int)Math.floor(completion * 6));
                ArkFxDraw.sprite("heavysmoke", p, face, r, s, s, 40, 40, mul(color, opacity),
                        glowing ? ADDITIVE : NONPREMULTIPLIED, variant * 80, row * 80, 80, 80);
            }
            case SQUISH -> {
                double stretch = Math.max(1, Math.min(maxSquish, velocity.length() * 16 / 10 * squish));
                double angle = face.angle(velocity) + Math.PI / 2, x = s - s * stretch * .3, y = s * stretch;
                double ratio = (double)ArkFxDraw.size("light")[1] / ArkFxDraw.size("bloomcircle")[1];
                centered("bloomcircle", p, face, angle, x * 2 * ratio, y * 2 * ratio, mul(color, opacity * .8), ADDITIVE);
                centered("light", p, face, angle, x * 1.1, y * 1.1, mul(color, opacity * .8), ADDITIVE);
                centered("light", p, face, angle, x, y, mul(WHITE, opacity * .9), ADDITIVE);
            }
            case RING -> centered("hollowcirclehardedge", p, face, r, s, s, mul(color, opacity), ADDITIVE);
            case BLOOM, LIGHT -> centered(type == Type.BLOOM ? "bloomcircle" : "light", p, face, r, s, s, color, ADDITIVE);
            case SMEAR -> centered(texture, p, plane, r, s, s, color, ADDITIVE);
            case CONSTELLATION -> {
                String circle="galaxia/hollowcirclesoftedge";
                // The source's horizontal squeeze is 2D perspective artwork, not a 3D ellipse.
                AttackFrame ringPlane=new AttackFrame(plane.right(),plane.normal());
                int[] dimensions=ArkFxDraw.size(circle);
                centered(circle,p,ringPlane,0,s*dimensions[1]/dimensions[0],s,mul(color,opacity),ADDITIVE);
                double ratio=(double)ArkFxDraw.size("sparkle")[1]/ArkFxDraw.size("bloomcircle")[1];
                for(int i=0;i<variant;i++){
                    double angle=expansion+ArkFxDraw.time()*7+i*Math.PI*2/variant;
                    Vec3 at=p.add(ringPlane.direction(angle).scale(s*dimensions[1]*.45/16));
                    centered("bloomcircle",at,billboard,0,s*ratio,s*ratio,mul(color,opacity*.5),ADDITIVE);
                    centered("sparkle",at,billboard,r+Math.PI/4+Math.PI/4*i,s*.75,s*.75,mul(color,opacity*.5),ADDITIVE);
                    centered("sparkle",at,billboard,r+Math.PI/4*i,s,s,mul(WHITE,opacity),ADDITIVE);
                }
            }
            case LINE, BLOOM_LINE -> {
                Vec3 v = line;
                if (type == Type.LINE) {
                    double extend = Math.sqrt(1 - Math.pow(completion - 1, 2)) * expansion / 16;
                    p = p.subtract(v.normalize().scale(extend / 2)); v = v.add(v.normalize().scale(extend));
                }
                String name = type == Type.BLOOM_LINE ? "bloomline" : concave ? "thickendedline" : "thinendedline";
                int[] size = ArkFxDraw.size(name);
                Vec3 along=v.normalize(); Vec3 side=(planeSprite?plane:billboard).normal().cross(along).normalize();
                if(side.lengthSqr()<1e-8)side=billboard.right();
                AttackFrame ribbon=new AttackFrame(along,side);
                double angle = Math.PI / 2;
                ArkFxDraw.sprite(name, p, ribbon, angle, s, v.length() * 16 / size[1], size[0] / 2d, size[1], color, ADDITIVE);
                if (capped) {
                    int[] cap = ArkFxDraw.size("bloomlinecap");
                    ArkFxDraw.sprite("bloomlinecap", p, ribbon, angle + Math.PI, s, s, cap[0] / 2d, cap[1], color, ADDITIVE);
                    ArkFxDraw.sprite("bloomlinecap", p.add(v), ribbon, angle, s, s, cap[0] / 2d, cap[1], color, ADDITIVE);
                }
            }
        }
    }
    /** Same source particle simulation, rendered without the optional custom shader pipeline. */
    void drawNative(double partial, net.everla.projectc.arkofcosmos.client.render.NativeSprites.Draw draw, AttackFrame billboard) {
        if(partial<born||partial>=died)return;
        int n=Math.min(2,(int)(clamp(partial)*3));double fraction=clamp(partial)*3-n;
        Frame a=frames[n],b=frames[n+1];if(a==null||b==null){beginFrame();a=frames[n];b=frames[n+1];}
        Vec3 p=a.position.lerp(b.position,fraction),v=a.velocity.lerp(b.velocity,fraction);
        double s=lerp(a.scale,b.scale,fraction),r=lerp(a.rotation,b.rotation,fraction),alpha=lerp(a.opacity,b.opacity,fraction),t=lerp(a.age,b.age,fraction)/lifetime;
        float[] c=mix(a.color,b.color,fraction),glow=mix(a.bloom,b.bloom,fraction);AttackFrame face=planeSprite?plane:billboard;
        switch(type){
            case SPARKLE,CRIT,FLARE -> {
                if(type==Type.FLARE&&delay>0)return;
                String texture=type==Type.SPARKLE?"sparkle":"thinsparkle";
                double x=s,y=s;
                if(type==Type.FLARE){double ease=1-Math.pow(1-t,4);x=lerp(originalX,finalX,ease);y=lerp(originalY,finalY,ease);}
                double ratio=(double)net.everla.projectc.arkofcosmos.client.render.NativeSprites.height("fx/"+texture)/net.everla.projectc.arkofcosmos.client.render.NativeSprites.height("fx/bloomcircle");
                draw.centered("fx/bloomcircle",p,face,0,x*bloomScale*ratio,y*bloomScale*ratio,mul(glow,alpha*.5),true);
                if(type==Type.SPARKLE)draw.centered("fx/"+texture,p,face,r+Math.PI/4,x*.75,y*.75,mul(c,alpha*.5),true);
                draw.centered("fx/"+texture,p,face,r,x,y,mul(c,alpha),true);
            }
            case SQUISH -> {
                double stretch=Math.max(1,Math.min(maxSquish,v.length()*16/10*squish)),angle=face.angle(v)+Math.PI/2,x=s-s*stretch*.3,y=s*stretch;
                double ratio=(double)net.everla.projectc.arkofcosmos.client.render.NativeSprites.height("fx/light")/net.everla.projectc.arkofcosmos.client.render.NativeSprites.height("fx/bloomcircle");
                draw.centered("fx/bloomcircle",p,face,angle,x*2*ratio,y*2*ratio,mul(c,alpha*.8),true);
                draw.centered("fx/light",p,face,angle,x*1.1,y*1.1,mul(c,alpha*.8),true);
                draw.centered("fx/light",p,face,angle,x,y,mul(WHITE,alpha*.9),true);
            }
            case RING -> draw.centered("fx/hollowcirclehardedge",p,face,r,s,s,mul(c,alpha),true);
            case BLOOM,LIGHT -> draw.centered("fx/"+(type==Type.BLOOM?"bloomcircle":"light"),p,face,r,s,s,c,true);
            case LINE -> {
                double extend=Math.sqrt(1-Math.pow(t-1,2))*expansion/16;Vec3 start=p.subtract(line.normalize().scale(extend/2)),vLine=line.add(line.normalize().scale(extend));
                Vec3 side=face.normal().cross(vLine.normalize()).normalize();if(side.lengthSqr()<1e-8)side=face.right();AttackFrame ribbon=new AttackFrame(vLine.normalize(),side);
                String name="fx/"+(concave?"thickendedline":"thinendedline");
                draw.sprite(name,start,ribbon,Math.PI/2,s,vLine.length()*16/net.everla.projectc.arkofcosmos.client.render.NativeSprites.height(name),net.everla.projectc.arkofcosmos.client.render.NativeSprites.width(name)/2d,net.everla.projectc.arkofcosmos.client.render.NativeSprites.height(name),c,true,false,15728880);
            }
            default -> { }
        }
    }
    private static void centered(String texture, Vec3 p, AttackFrame face, double angle, double x, double y, float[] color, ArkFxDraw.Blend blend) {
        ArkFxDraw.centered(texture, p, face, angle, x, y, color, blend);
    }
}
