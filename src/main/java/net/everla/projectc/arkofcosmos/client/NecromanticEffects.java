package net.everla.projectc.arkofcosmos.client;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.*;
import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.CalamitasContent;
import net.everla.projectc.arkofcosmos.client.render.*;
import net.everla.projectc.arkofcosmos.entity.NecromanticAttackEntity;
import net.everla.projectc.arkofcosmos.util.AttackFrame;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import static net.everla.projectc.arkofcosmos.entity.NecromanticAttackEntity.*;

/** Original 60 Hz gas/lava particles, sharing the arena's retained-depth metaball material. */
@Mod.EventBusSubscriber(modid=ArkOfCosmos.MOD_ID,value=Dist.CLIENT)
public final class NecromanticEffects {
    private static final List<Particle> particles=new ArrayList<>();
    private static final Map<UUID,Integer> burned=new HashMap<>();
    private static final Map<UUID,Integer> ages=new HashMap<>();
    private static final Map<UUID,Loop> loops=new HashMap<>();
    private static final Set<UUID> grown=new HashSet<>();
    private static net.minecraft.client.multiplayer.ClientLevel level;
    private static TextureTarget mask,visible;
    private static final Random random=new Random();
    private static final class Particle {
        Vec3 at,velocity;double size,rotation;int owner=-1;int age,lifetime,mode,variant=random.nextInt(3);float[] color;
        Particle(int mode,Vec3 at,Vec3 velocity,double size,int lifetime,float[] color){this.mode=mode;this.at=at;this.velocity=velocity;this.size=size;this.lifetime=lifetime;this.color=color;this.rotation=random.nextDouble()*Math.PI*2;}
        void step(){age++;at=at.add(velocity);switch(mode){case 0->{size*=.94;velocity=velocity.scale(.96);}case 1->{size=Math.min(200,Math.max(0,size-.24))*.9956;if(size<15)size=size*.95-.9;velocity=Vec3.ZERO;}case 2->{rotation+=velocity.x*16*.004;velocity=velocity.scale(.985);}case 3->velocity=velocity.scale(.95);case 6->{velocity=velocity.multiply(.93*.97,.93*.99,.93*.97);size-=.1;}case 4->{velocity=velocity.scale(.98*.92);rotation+=velocity.x<0?-1:1;size-=.24;}default->{velocity=velocity.scale(.92);size-=.4;}}}
        boolean dead(){return age>=lifetime||size<=(mode==0?2.5:mode==1?3:.1);}
    }
    private static final class Loop extends AbstractTickableSoundInstance {
        private final NecromanticAttackEntity entity;
        Loop(NecromanticAttackEntity e){super(CalamitasContent.necromanticSound("rancor_loop"),SoundSource.PLAYERS,RandomSource.create());entity=e;looping=true;delay=0;volume=1;}
        @Override public void tick(){if(entity.isRemoved()||entity.level()!=Minecraft.getInstance().level||entity.age()<180){stop();return;}x=entity.getX();y=entity.getY();z=entity.getZ();}
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();
        if(level!=mc.level){for(var loop:loops.values())mc.getSoundManager().stop(loop);loops.clear();NecromanticAshRenderer.clear();particles.clear();burned.clear();burnAges.clear();ages.clear();grown.clear();level=mc.level;if(mask!=null){mask.destroyBuffers();visible.destroyBuffers();mask=visible=null;}}
        if(level==null||mc.isPaused())return;
        NecromanticAshRenderer.tick();
        List<NecromanticAttackEntity> entities=new ArrayList<>();Set<UUID> alive=new HashSet<>();
        for(var candidate:level.entitiesForRendering())if(candidate instanceof NecromanticAttackEntity e){entities.add(e);alive.add(e.getUUID());if(!ages.containsKey(e.getUUID())&&e.power()>.62)grown.add(e.getUUID());}
        for(int step=0;step<3;step++){
            burn();
            for(var particle:particles)particle.step();particles.removeIf(Particle::dead);
            for(var entity:entities){var samples=entity.view().getList("Samples",10);if(samples.size()<=step)continue;var sample=samples.getCompound(step);int age=sample.getInt("Age");if(age<=ages.getOrDefault(entity.getUUID(),-1))continue;ages.put(entity.getUUID(),age);emit(entity,sample);}
        }
        ages.keySet().retainAll(alive);grown.retainAll(alive);
        for(var e:entities)if(e.kind()==Kind.RANCOR&&e.age()>=180&&!loops.containsKey(e.getUUID())){var loop=new Loop(e);loops.put(e.getUUID(),loop);mc.getSoundManager().play(loop);}
        loops.entrySet().removeIf(entry->{if(alive.contains(entry.getKey()))return false;mc.getSoundManager().stop(entry.getValue());return true;});
    }
    public static void visual(int kind,int id,UUID uuid,Vec3 at){
        var mc=Minecraft.getInstance();if(mc.level==null)return;
        if(kind==2){NecromanticAshRenderer.request(id,uuid);return;}
        if(kind==0){var target=mc.level.getEntity(id);if(target!=null&&target.getUUID().equals(uuid)){burned.put(uuid,id);burnAges.put(uuid,0);}return;}
        if(kind==1)for(int i=0;i<6;i++)add(6,at.add(ball(12/16d)),new Vec3((random.nextInt(41)-20)*.1/16,(random.nextInt(41)-20)*.1/16,(random.nextInt(41)-20)*.1/16),1.2*8,100,null);
    }
    private static final Map<UUID,Integer> burnAges=new HashMap<>();
    private static void burn(){
        burned.entrySet().removeIf(entry->{
            var target=level.getEntity(entry.getValue());int age=burnAges.getOrDefault(entry.getKey(),0)+1;burnAges.put(entry.getKey(),age);
            if(target==null||!target.getUUID().equals(entry.getKey())||age>10){burnAges.remove(entry.getKey());return true;}
            Vec3 center=target.getBoundingBox().getCenter();
            if(random.nextInt(4)==0)add(2,center,ball(1).multiply(2.75/16,6.5/16,2.75/16),ArkFxDraw.size("rancor_fog")[0]*(.45+random.nextDouble()*.3),180,new float[]{236/255f,0,68/255f,1});
            if(random.nextInt(6)==0){var box=target.getBoundingBox().inflate(10/16d);add(1,new Vec3(box.minX+random.nextDouble()*box.getXsize(),box.minY+random.nextDouble()*box.getYsize(),box.minZ+random.nextDouble()*box.getZsize()),Vec3.ZERO,30+random.nextDouble()*7,600,null);}return false;
        });
    }
    private static void add(int mode,Vec3 at,Vec3 velocity,double size,int lifetime,float[] color){particles.add(new Particle(mode,at,velocity,size,lifetime,color));}
    private static Vec3 unit(){double y=random.nextDouble()*2-1,a=random.nextDouble()*Math.PI*2,r=Math.sqrt(1-y*y);return new Vec3(r*Math.cos(a),y,r*Math.sin(a));}
    private static Vec3 ball(double radius){return unit().scale(Math.sqrt(random.nextDouble())*radius);}
    private static Vec3 plane(Vec3 direction,double x,double y,boolean edge){Vec3 normal=direction.lengthSqr()<.001?new Vec3(0,0,1):direction.normalize();Vec3 right=normal.cross(new Vec3(0,1,0));if(right.lengthSqr()<.001)right=new Vec3(1,0,0);right=right.normalize();Vec3 up=normal.cross(right).normalize();double a=random.nextDouble()*Math.PI*2,r=edge?1:Math.sqrt(random.nextDouble());return right.scale(Math.cos(a)*r*x).add(up.scale(Math.sin(a)*r*y));}
    private static void emit(NecromanticAttackEntity e,CompoundTag sample){
        Vec3 at=vector(sample,"Position"),velocity=vector(sample,"Velocity");int age=sample.getInt("Age"),death=sample.getInt("Death");double power=Math.pow(lerpValue(15,840,age),4);
        switch(e.kind()){
            case CONGREGATION->{
                var owner=level.getEntity(e.view().getInt("Owner"));
                if(owner!=null&&death==0){
                    Vec3 right=new Vec3(-Math.cos(Math.toRadians(owner.getYRot())),0,-Math.sin(Math.toRadians(owner.getYRot())));
                    Vec3 book=owner.position().add(0,owner.getBbHeight()*.65+.3,0).add(right.scale(.35+7/36d*.6)).add(owner.getLookAngle().scale(.35));
                    double mix=random.nextDouble()*.7,speed=(.8+random.nextDouble()*1.2)*(random.nextInt(8)==0?1.7:1)/16;
                    add(4,book.add(ball(5/16d)),new Vec3(Math.sin(.44),Math.cos(.44),0).scale(speed),8,100,new float[]{(float)((139/255d+(1-139/255d)*mix)*.5),0,(float)(mix*.5),0});particles.get(particles.size()-1).owner=owner.getId();
                }
                double size=smooth(54,185,power);if(age>2)size+=velocity.length()*16*4.2;size=Math.min(210,size)*(1-.5*lerpValue(0,35,death));
                int count=random.nextInt(8)==0?3:1;
                for(int i=0;i<count;i++){
                    Vec3 spawn=at.add(ball(size/26/16));add(0,spawn,ball(4.4/16),size,100,null);
                    spawn=spawn.add(rotate(velocity,1.38).scale(size/105));add(0,spawn,ball((i*1.5+7)/16),size*.3,100,null);size*=.9;
                }
                if(power>.62&&grown.add(e.getUUID()))for(int j=0;j<16;j++)for(int k=0;k<40;k++){
                    float darkness=random.nextFloat()*.6f;float[] color={.58f*(1-darkness),0,.827f*(1-darkness),0};
                    add(4,at.add(ball(10/16d)),unit().scale((14+j*1.8)/16),(1.74+random.nextDouble()*.76)*8,100,color);
                }
            }
            case SPIRIT_DUST->{double radius=smooth(80,48,1-(60-age)/90d);add(0,at.add(ball(5/16d).scale(radius/130)),ball(3/16d),radius,100,null);}
            case SMALL_SPIRIT->{double radius=smooth(67,32,Math.sqrt(Math.min(1,age/360d)))+(sample.getBoolean("Returning")?36:0);add(0,at.add(ball(5/16d).scale(radius/130)),ball(6/16d),radius,100,null);}
            case LARGE_CINDER->add(1,at.add(ball(6/16d)),Vec3.ZERO,sample.getFloat("Scale")*36,600,null);
            case RANCOR->{
                Vec3 direction=vector(sample,"Direction").normalize();double charge=lerpValue(0,180,age);int chance=(int)smooth(20,2,charge);
                for(int i=0;i<2;i++)if(random.nextInt(Math.max(1,chance))==0){Vec3 offset=plane(direction,.5,1,true).scale((114*.375+random.nextDouble()*114*.11)/16);Vec3 tangent=direction.cross(offset).normalize().scale((random.nextBoolean()?1:-1)*(2+random.nextDouble()*4)/16);add(5,at.add(offset),tangent,8*(1+random.nextDouble()*.4),100,new float[]{random.nextFloat(),0,random.nextFloat(),0});}
                if(age>30&&age%5==0)add(3,at,plane(direction,1,1,true).scale((5+random.nextDouble()*5)/16),(.075+(.35-.075)*charge)*ArkFxDraw.size("light")[0],30,new float[]{1,(float)(1-charge),(float)(1-charge),1});
                if(age<180&&random.nextInt(3)==0){Vec3 offset=plane(direction,.5,1,true).scale((114*.375+random.nextDouble()*114*.11)/16);double speed=(3.5+4.5*charge)*(.65+random.nextDouble()*.35)/16;float mix=random.nextFloat();add(5,at.add(offset),direction.scale(speed),8*(1+random.nextDouble()*(.05+charge*.55)),100,new float[]{1-mix,0,mix,0});}
                if(age>=179){
                    Vec3 end=at.add(direction.scale(sample.getDouble("Length")));
                    add(1,end.add(ball(10/16d)).add(direction.scale(40/16d)),Vec3.ZERO,135,600,null);
                    add(2,end.subtract(direction.scale((12+random.nextDouble()*60)/16)),plane(direction,4/16d,8/16d,false),ArkFxDraw.size("rancor_fog")[0]*(1+random.nextDouble()*.7),180,new float[]{236/255f,0,68/255f,1});
                }
            }
            default->{}
        }
    }
    private static Vec3 rotate(Vec3 velocity,double maxAngle){Vec3 axis=unit().cross(velocity).normalize();double angle=(random.nextDouble()*2-1)*maxAngle;return velocity.scale(Math.cos(angle)).add(axis.cross(velocity).scale(Math.sin(angle)));}
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES||level==null)return;
        ArkFxDraw.matrix=event.getPoseStack().last().pose();ArkFxDraw.camera=event.getCamera().getPosition();ArkFxDraw.fadeEye=null;
        if(!OculusShaderCompat.defer(()->draw(event.getPartialTick()),true))draw(event.getPartialTick());
    }
    private static void draw(float partial){
        if(particles.isEmpty()&&NecromanticAshRenderer.empty())return;var mc=Minecraft.getInstance();var main=mc.getMainRenderTarget();if(main.width<=0||main.height<=0)return;
        if(mask==null||mask.width!=main.width||mask.height!=main.height){if(mask!=null){mask.destroyBuffers();visible.destroyBuffers();}mask=new TextureTarget(main.width,main.height,false,Minecraft.ON_OSX);visible=new TextureTarget(main.width,main.height,true,Minecraft.ON_OSX);mask.setClearColor(0,0,0,0);visible.setClearColor(0,0,0,0);}
        var shader=RenderSystem.getShader();int texture=RenderSystem.getShaderTexture(0);boolean depth=org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_DEPTH_TEST),write=org.lwjgl.opengl.GL11.glGetBoolean(org.lwjgl.opengl.GL11.GL_DEPTH_WRITEMASK);
        boolean cull=org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_CULL_FACE),blend=org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_BLEND);
        int src=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_SRC_RGB),dst=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_DST_RGB),srcAlpha=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_SRC_ALPHA),dstAlpha=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_DST_ALPHA);
        float[] shaderColor=RenderSystem.getShaderColor().clone();
        try{
            mask.clear(Minecraft.ON_OSX);mask.bindWrite(true);project(false,partial);
            visible.clear(Minecraft.ON_OSX);visible.copyDepthFrom(main);visible.bindWrite(true);project(true,partial);
            main.bindWrite(true);CalamitasArenaMaterial.compositeGhost(mask,visible,ArkFxDraw.time(),ArkFxDraw.camera.x*16,-ArkFxDraw.camera.y*16);
            for(var e:level.entitiesForRendering())if(e instanceof NecromanticAttackEntity entity&&entity.kind()==Kind.CONGREGATION)head(entity,partial,false);
            NecromanticAshRenderer.draw(partial);
            for(int mode=1;mode<=6;mode++)for(int variant=0;variant<(mode>=4?3:1);variant++)decorate(mode,variant,partial);
        }finally{main.bindWrite(true);RenderSystem.setShader(()->shader);RenderSystem.setShaderTexture(0,texture);RenderSystem.setShaderColor(shaderColor[0],shaderColor[1],shaderColor[2],shaderColor[3]);RenderSystem.blendFuncSeparate(src,dst,srcAlpha,dstAlpha);if(cull)RenderSystem.enableCull();else RenderSystem.disableCull();if(blend)RenderSystem.enableBlend();else RenderSystem.disableBlend();RenderSystem.depthMask(write);if(depth)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();}
    }
    private static void project(boolean depth,float partial){
        ArkFxDraw.begin("necro_circle",ArkFxDraw.Blend.ALPHA,false);if(!depth)RenderSystem.disableDepthTest();
        var out=com.mojang.blaze3d.vertex.Tesselator.getInstance().getBuilder();out.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,ArkFxDraw.FORMAT);
        for(var p:particles)if(p.mode==0)quad(out,p.at.add(p.velocity.scale(partial)),p.size/16,0,new float[]{1,1,1,1});
        com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(out.end());ArkFxDraw.end();
        for(var e:level.entitiesForRendering())if(e instanceof NecromanticAttackEntity entity&&entity.kind()==Kind.CONGREGATION){if(!depth)RenderSystem.disableDepthTest();head(entity,partial,true);}
    }
    private static void quad(com.mojang.blaze3d.vertex.BufferBuilder out,Vec3 at,double diameter,double angle,float[] color){
        var plane=NativeSprites.billboard();Vec3 x=plane.direction(angle).scale(diameter/2),y=plane.direction(angle+Math.PI/2).scale(diameter/2);
        ArkFxDraw.vertex(out,at.subtract(x).subtract(y),color,0,0,1);ArkFxDraw.vertex(out,at.subtract(x).add(y),color,0,1,1);ArkFxDraw.vertex(out,at.add(x).add(y),color,1,1,1);ArkFxDraw.vertex(out,at.add(x).subtract(y),color,1,0,1);
    }
    private static void head(NecromanticAttackEntity e,float partial,boolean back){
        boolean big=e.power()>.62;String texture=back?(big?"spirit_back_big":"spirit_back"):(big?"spirit_head_big":"spirit_head");int[] size=ArkFxDraw.size(texture);int frames=big?9:6,h=size[1]/frames;
        double scale=e.view().getFloat("Scale")*(back?1.04:1);Vec3 velocity=vector(e.view(),"Velocity"),direction=velocity.normalize();double offset=scale*((e.diameter()*16-54)/90+1.5)*size[0]/90d*15/16;
        Vec3 at=e.getPosition(partial).add(direction.scale(offset));var face=NativeSprites.billboard();double angle=Math.atan2(velocity.dot(face.right()),velocity.dot(face.forward()));
        ArkFxDraw.sprite(texture,at,face,angle,scale,scale,size[0]/2d,h/2d,new float[]{1,1,1,1},ArkFxDraw.Blend.ALPHA,0,(e.age()/5%frames)*h,size[0],h);
    }
    private static void decorate(int mode,int variant,float partial){
        String texture=mode==1||mode==3?"light":mode==2?"rancor_fog":mode==4?"necro_dust_267":mode==5?"necro_dust_234":"calamitas_dust_261_"+variant;
        if(mode>=4&&mode<=5&&variant>0)texture+="_"+variant;
        ArkFxDraw.begin(texture,mode<=3?ArkFxDraw.Blend.ADDITIVE:ArkFxDraw.Blend.ALPHA,false);
        var out=com.mojang.blaze3d.vertex.Tesselator.getInstance().getBuilder();out.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,ArkFxDraw.FORMAT);
        for(var p:particles){if(p.mode!=mode||(mode>=4&&p.variant!=variant))continue;
        Vec3 at=p.at.add(p.velocity.scale(partial));double t=p.age+partial;float[] color;
        double scale=p.size/16;
        if(p.mode==1){double bright=lerpValue(10,2,t)*.67;float r=(float)(1+(245/255d-1)*bright),g=(float)(56/255d+(222/255d-56/255d)*bright),b=(float)(3/255d+(179/255d-3/255d)*bright);color=new float[]{r*1.2f,g*1.2f,b*1.2f,1};}
        else if(p.mode==2){float opacity=(float)(lerpValue(0,15,t)*lerpValue(p.lifetime,p.lifetime-90,t)*.5);color=new float[]{p.color[0]*opacity,p.color[1]*opacity,p.color[2]*opacity,opacity};}
        else if(p.mode==3){float opacity=(float)Math.pow(Math.sin(t/p.lifetime*Math.PI),2);color=new float[]{p.color[0]*opacity,p.color[1]*opacity,p.color[2]*opacity,opacity};}
        else color=p.mode==5?new float[]{1,1,1,0}:p.mode==6?new float[]{230/255f,230/255f,230/255f,115/255f}:p.color;
        var mc=Minecraft.getInstance();
        if(mc.player!=null&&ArkFxDraw.ownerFirstPerson(p.owner,mc.player.getId(),mc.options.getCameraType().isFirstPerson(),mc.getCameraEntity()==mc.player)){
            float fade=(float)ArkFxDraw.nearOpacity(at.distanceTo(mc.gameRenderer.getMainCamera().getPosition()));color=new float[]{color[0]*fade,color[1]*fade,color[2]*fade,color[3]*fade};
        }
        quad(out,at,scale,p.rotation,color);
        }com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(out.end());ArkFxDraw.end();
    }
}
