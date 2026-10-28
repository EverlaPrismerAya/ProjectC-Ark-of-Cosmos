package net.everla.projectc.arkofcosmos;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public final class ArkAttackRenderer extends EntityRenderer<ArkAttackEntity> {
    private static ArkAttackRenderer INSTANCE;
    private record Sprite(String name, int width, int height, boolean item) {
        private Sprite(String name, int width, int height) { this(name, width, height, false); }
        ResourceLocation texture() { return ResourceLocation.fromNamespaceAndPath(ArkOfCosmos.MOD_ID, "textures/" + (item ? "item/" : "entity/") + name + ".png"); }
    }
    private static final Sprite LEFT = new Sprite("sundering_scissors_left", 120, 132);
    private static final Sprite RIGHT = new Sprite("sundering_scissors_right", 134, 132);
    private static final Sprite LEFT_GLOW = new Sprite("sundering_scissors_left_glow", 120, 132);
    private static final Sprite RIGHT_GLOW = new Sprite("sundering_scissors_right_glow", 134, 132);
    private static final Sprite NEEDLE = new Sprite("rending_needle", 14, 88);
    private static final Sprite BOLT = new Sprite("galaxia_bolt", 22, 34);
    private static final Sprite ITEM = new Sprite("ark_of_cosmos", 126, 132, true);

    private final java.util.Map<Sprite, java.util.List<int[]>> contours = new java.util.HashMap<>();
    private java.util.List<int[]> contour(Sprite sprite) {
        return contours.computeIfAbsent(sprite, key -> {
            java.util.List<int[]> edges = new java.util.ArrayList<>();
            try (var stream = net.minecraft.client.Minecraft.getInstance().getResourceManager().getResourceOrThrow(key.texture()).open();
                 var image = com.mojang.blaze3d.platform.NativeImage.read(stream)) {
                for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
                    if ((image.getPixelRGBA(x, y) >>> 24) == 0) continue;
                    if (x == 0 || (image.getPixelRGBA(x - 1, y) >>> 24) == 0) edges.add(new int[]{x, y, 0});
                    if (x == image.getWidth() - 1 || (image.getPixelRGBA(x + 1, y) >>> 24) == 0) edges.add(new int[]{x, y, 1});
                    if (y == 0 || (image.getPixelRGBA(x, y - 1) >>> 24) == 0) edges.add(new int[]{x, y, 2});
                    if (y == image.getHeight() - 1 || (image.getPixelRGBA(x, y + 1) >>> 24) == 0) edges.add(new int[]{x, y, 3});
                }
            } catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
            return edges;
        });
    }
    public ArkAttackRenderer(EntityRendererProvider.Context context) { super(context); shadowRadius = 0; INSTANCE = this; }
    @Override public ResourceLocation getTextureLocation(ArkAttackEntity entity) { return RIGHT.texture(); }
    @Override public boolean shouldRender(ArkAttackEntity entity, Frustum frustum, double x, double y, double z) {
        // Blade sprites and constellation reach far outside the small entity tracking box.
        return entity.shouldRenderAtSqrDistance(entity.distanceToSqr(x, y, z));
    }

    @Override public void render(ArkAttackEntity entity, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        CompoundTag state = entity.renderState(partial);
        if (state.isEmpty()) return;
        AttackFrame frame = entity.attackFrame();
        Vec3 position = entity.renderPosition(partial);
        // EntityRenderDispatcher already translated to the vanilla entity interpolation point.
        Vec3 base = new Vec3(net.minecraft.util.Mth.lerp(partial, entity.xOld, entity.getX()),
                net.minecraft.util.Mth.lerp(partial, entity.yOld, entity.getY()),
                net.minecraft.util.Mth.lerp(partial, entity.zOld, entity.getZ()));
        Vec3 local = position.subtract(base);
        float rotation = entity.renderRotation(partial), scale = entity.renderScale(partial);
        Entity owner = entity.level().getEntity(entity.ownerId());
        Vec3 center = owner == null ? position : owner.getBoundingBox().getCenter().add(
                net.minecraft.util.Mth.lerp(partial, owner.xOld, owner.getX()) - owner.getX(),
                net.minecraft.util.Mth.lerp(partial, owner.yOld, owner.getY()) - owner.getY(),
                net.minecraft.util.Mth.lerp(partial, owner.zOld, owner.getZ()) - owner.getZ());
        int mode = state.getInt("Mode"), left = state.getInt("Left");
        boolean charged = state.getInt("Charge") > 0;
        ArkFxDraw.matrix=pose.last().pose(); ArkFxDraw.camera=Vec3.ZERO;
        ArkFxDraw.ownerView(entity.ownerId(),base);
        try {
        switch (entity.kind()) {
            case BLADE -> {
                bladeEffects(entity,pose,frame,center.subtract(base),local,state,partial,rotation,scale);
                if (mode < 2) {
                    Vec3 origin = center.add(frame.direction(rotation).scale(10 * ArkCombat.PIXEL)).subtract(base);
                    if (charged) {
                        Vec3 offset = frame.direction(rotation).scale(56 * scale * ArkCombat.PIXEL)
                                .add(frame.direction(rotation - Math.PI / 2).scale(11 * scale * ArkCombat.PIXEL));
                        blade(pose, buffers, frame, origin.add(offset), rotation + Math.PI / 4, scale, false, 44, 86, light);
                        blade(pose, buffers, frame, origin, rotation + Math.PI / 4, scale, true, 0, 132, light);
                    } else blade(pose, buffers, frame, origin, rotation + Math.PI / 4, scale, mode == 1, 0, 132, light);
                } else {
                    double t = mode == 3 ? (35 - left) / 35d : (140 - left) / 140d;
                    if (charged) {
                        double opening = mode == 3 ? lerp(Math.PI * 133 / 200, Math.PI / 4, clamp(t + .5))
                                : lerp(Math.PI / 4, Math.PI * 133 / 200, clamp(t * 2));
                        blade(pose, buffers, frame, local, rotation + opening, scale, false, 44, 86, light);
                    } else if (mode == 3) {
                        double s = clamp(t + .25); s = s * s * (3 - 2 * s);
                        Vec3 other = center.lerp(position, s).subtract(base);
                        blade(pose, buffers, frame, other, frame.angle(entity.renderDirection(partial)) + Math.PI / 4, scale, false, 44, 86, light);
                    }
                    blade(pose, buffers, frame, local, rotation + Math.PI / 4, scale, true, 32, 86, light);
                }
            }
            case NEEDLE, BOLT -> {
                Vec3 velocity = entity.renderVelocity(partial);
                Vec3 forward = velocity.normalize();
                Vec3 side = frame.normal().cross(forward).normalize();
                if (side.lengthSqr() < .5) side = frame.right();
                AttackFrame flight = new AttackFrame(forward, side);
                Vec3[] worldTrail = ArkEffects.trail(entity,partial), localTrail = new Vec3[worldTrail.length];
                for (int i = 0; i < worldTrail.length; i++) localTrail[i] = worldTrail[i].subtract(base);
                Vec3 trailOffset = entity.kind() == ArkAttackEntity.Kind.NEEDLE ? forward.scale(-30.5 * ArkCombat.PIXEL) : Vec3.ZERO;
                ArkFxDraw.trail(localTrail, frame, pose, scale, entity.kind() == ArkAttackEntity.Kind.NEEDLE, trailOffset);
                if(entity.kind()==ArkAttackEntity.Kind.NEEDLE){
                    Vec3 at=local.add(trailOffset);float time=ArkFxDraw.time();
                    float[] color=ArkEffects.hsl(time*.6,.85);
                    double ratio=(double)ArkFxDraw.size("sparkle")[1]/ArkFxDraw.size("bloomcircle")[1];
                    ArkFxDraw.centered("bloomcircle",at,flight,0,4*ratio,4*ratio,ArkEffects.mul(color,.5),ArkFxDraw.Blend.ADDITIVE);
                    ArkFxDraw.centered("sparkle",at,flight,time*8+Math.PI/4,1.5,1.5,ArkEffects.mul(color,.5),ArkFxDraw.Blend.ADDITIVE);
                    ArkFxDraw.centered("sparkle",at,flight,time*8,2,2,ArkEffects.WHITE,ArkFxDraw.Blend.ADDITIVE);
                }
                Sprite sprite = entity.kind() == ArkAttackEntity.Kind.NEEDLE ? NEEDLE : BOLT;
                sprite(pose, buffers, flight, local, sprite, Math.PI / 2, scale, scale, sprite.width / 2d, sprite.height / 2d, 1, 15728880);
            }
            case CHAIN -> { /* Original particle list is simulated at 60 Hz and drawn in AFTER_PARTICLES. */ }
            case PARRY -> {
                float progress = (16 - entity.renderLeft(partial)) / 15f;
                float close = ArkCurves.parryClose(progress);
                Vec3 origin = entity.renderAim(partial).subtract(base);
                blade(pose, buffers, frame, origin, lerp(Math.PI / 2, Math.PI / 4, close), scale, false, 44, 86, light);
                blade(pose, buffers, frame, origin, lerp(0, Math.PI / 4, close), scale, true, 33, 86, light);
            }
            case BLAST -> blast(entity, pose, buffers, frame, base, local, state, partial, light);
        }
        } finally { ArkFxDraw.fadeEye = null; }
    }

    private void bladeEffects(ArkAttackEntity entity,PoseStack pose,AttackFrame frame,Vec3 center,Vec3 local,CompoundTag state,float partial,float rotation,float scale){
        int mode=state.getInt("Mode");boolean charged=state.getInt("Charge")>0;
        double left=entity.renderLeft(partial),life=state.getInt("Life"),completion=(life-left)/life;
        if(mode<2){
            if((charged||mode==0)&&life-left>10){
                float[] angles=ArkEffects.rotations(entity,partial);
                for(int i=1;i<angles.length;i++){
                    float[] color=ArkEffects.mul(ArkEffects.hsl(i/10d*.1,charged?.9:.6),charged?.15:.05);
                    String name=charged?"sunderingscissorsglow":"sunderingscissorsrightglow";
                    ArkFxDraw.sprite(name,center.add(frame.direction(rotation).scale(10d/16)),frame,angles[i]+Math.PI/4,scale-.2*i/10,scale-.2*i/10,0,132,color,ArkFxDraw.Blend.ALPHA);
                }
            }
            if(mode==0&&completion>.5){
                double offset=-Math.PI/8+Math.PI/8*completion;
                float[] color=ArkEffects.mul(ArkEffects.hsl((completion-.5)*.3,.6),.5*Math.sin(completion*Math.PI));
                ArkFxDraw.centered("trientcircularsmear",center,frame,Math.PI+offset,scale*2.3,scale*2.3,color,ArkFxDraw.Blend.ADDITIVE);
            }
        }
        if(mode==1){
            double ratio=ArkCurves.swirl((float)completion),intensity=ArkEffects.clamp(Math.sin((ratio-.2)*Math.PI))*.8;
            // The source creates one smear, then resets Time and updates its transform every AI step.
            double sourceScale=1.6+Math.sin(ratio*Math.PI)+state.getInt("Charge")/10d*.05;
            double smearScale=ArkEffects.lerp(2.6,3.5,sourceScale-1.6);
            ArkFxDraw.centered("circularsmearsmokey",center,frame,rotation+Math.PI/4,smearScale,smearScale,ArkEffects.mul(ArkEffects.CHOCOLATE,intensity),ArkFxDraw.Blend.ADDITIVE);
        }else if(mode>=2){
            double amount=(mode==3?Math.cos((35-left)/35*Math.PI/2):Math.sin((140-left)/140*Math.PI))*.5;
            float[] color=ArkEffects.mul(ArkEffects.hsl((35-left-17.5)/17.5*.15,.8),amount);
            ArkFxDraw.centered(charged?"circularsmearsmokey":"trientcircularsmear",local,frame,rotation-Math.PI*7/8,scale*1.65,scale*1.65,color,ArkFxDraw.Blend.ADDITIVE);
        }
    }
    private void blast(ArkAttackEntity entity, PoseStack pose, MultiBufferSource buffers, AttackFrame frame,
                       Vec3 base, Vec3 local, CompoundTag state, float partial, int light) {
        double timer=70-entity.renderLeft(partial),snap=clamp(timer/25),hold=clamp((timer-25)/15),stitch=clamp((timer-32.5)/37.5);
        double length=ArkCurves.blastThrust((float)snap)*242;
        ArkFxDraw.sprite("bloomline",local,frame,Math.PI/2,.2*(1-snap),length,32,1,ArkEffects.mix(ArkEffects.ORANGE_RED,ArkEffects.WHITE,snap),ArkFxDraw.Blend.ADDITIVE);
        if(hold<=.4){
            double close=ArkCurves.blastClose((float)snap),fade=(.4-hold)/.4;
            Vec3 origin=entity.renderAim(partial).subtract(base);
            ArkFxDraw.sprite("sunderingscissorsright",origin,frame,lerp(Math.PI/2,Math.PI/4,close),1.4,1.4,44,86,ArkEffects.mul(ArkEffects.rgb(0,191,255),fade*.9),ArkFxDraw.Blend.ADDITIVE);
            ArkFxDraw.sprite("sunderingscissorsleft",origin,frame,lerp(0,Math.PI/4,close),1.4,1.4,33,86,ArkEffects.mul(ArkEffects.rgb(255,99,71),fade*fade*.9),ArkFxDraw.Blend.ADDITIVE);
        }
        if(hold<=0)return;
        double fade=stitch<.75?1:1-(stitch-.75)*4;
        // The source jitters this plane by at most half a pixel during its first three hold frames.
        Vec3 jitter=hold>.2?Vec3.ZERO:frame.direction(ArkEffects.random()*Math.PI*2+Math.PI/4).scale(Math.sqrt(2)*(1-hold*5)*.5/16);
        float[] color=ArkEffects.mul(ArkEffects.mix(ArkEffects.WHITE,ArkEffects.mul(ArkEffects.ORANGE_RED,.7),Math.sin(hold*Math.PI/2)),fade);
        ArkFxDraw.sprite("thinendedline",local.add(jitter),frame,Math.PI/2,.2*fade,length/84,32,84,color,ArkFxDraw.Blend.ADDITIVE);
        int count=(int)Math.ceil((1-Math.sqrt(1-Math.pow(clamp(stitch*3),2)))*8);
        for(int i=0;i<count;i++){
            double angle=ArkEffects.stitchAngle(entity,i);
            // Actual first integer AI step containing this stitch (HandleParticles increments its age immediately).
            double born=Math.floor(32.5+12.5*Math.sqrt(1-Math.pow(1-i/8d,2)))+1;
            double age=Math.max(1,timer-born+1),t=clamp(age/7);
            double size=(1+Math.sin(t*Math.PI)*.3)*.85;
            if(count==8)size*=1-(timer-32.5-11.25)/37.5*.7*.8;
            Vec3 at=local.add(frame.forward().scale(length*(i+.5)/8/16)).add(jitter);
            float[] c=ArkEffects.mix(ArkEffects.WHITE,ArkEffects.mul(ArkEffects.CORNFLOWER,.7),Math.sin(t*Math.PI/2));
            ArkFxDraw.sprite("thinendedline",at,frame,Math.PI/2+angle,.2*size,(Math.sin(i/7d*Math.PI)*.5+.5)*size,32,42,c,ArkFxDraw.Blend.ADDITIVE);
        }
    }

    private void blade(PoseStack pose, MultiBufferSource buffers, AttackFrame frame, Vec3 origin, double angle,
                       float scale, boolean left, double pivotX, double pivotY, int light) {
        sprite(pose, buffers, frame, origin, left ? LEFT : RIGHT, angle, scale, scale, pivotX, pivotY, 1, light);
        sprite(pose, buffers, frame, origin.add(frame.normal().scale(.001)), left ? LEFT_GLOW : RIGHT_GLOW,
                angle, scale, scale, pivotX, pivotY, 1, 15728880);
    }

    private void sprite(PoseStack pose, MultiBufferSource buffers, AttackFrame frame, Vec3 origin, Sprite sprite, double angle,
                        double scaleX, double scaleY, double pivotX, double pivotY, double alpha, int light) {
        if (alpha <= 0) return;
        Vec3 x = frame.direction(angle).scale(scaleX * ArkCombat.PIXEL);
        Vec3 y = frame.direction(angle + Math.PI / 2).scale(scaleY * ArkCombat.PIXEL);
        Vec3 normal = frame.normal();
        RenderType type = sprite == ITEM ? RenderType.entityCutoutNoCull(sprite.texture())
                : (sprite == LEFT || sprite == RIGHT ? RenderType.entityTranslucent(sprite.texture()) : RenderType.entityTranslucentEmissive(sprite.texture()));
        VertexConsumer out = buffers.getBuffer(type);
        Matrix4f matrix = pose.last().pose();
        boolean blade = sprite == LEFT || sprite == RIGHT || sprite == LEFT_GLOW || sprite == RIGHT_GLOW || sprite == ITEM;
        // Three Terraria pixels total thickness; the previous width-derived value made blades several blocks thick.
        double depth = blade ? 1.5d * scaleX * ArkCombat.PIXEL : 0;
        if (sprite == LEFT_GLOW || sprite == RIGHT_GLOW) depth += .002;
        for (int face = 0; face < (blade ? 2 : 1); face++) {
            Vec3 faceOrigin = origin.add(normal.scale(face == 0 ? depth : -depth));
            Vec3 faceNormal = face == 0 ? normal : normal.scale(-1);
            int columns = ArkFxDraw.fadeEye == null ? 1 : Math.max(1, (int)Math.ceil(x.length() * sprite.width / .5));
            int rows = ArkFxDraw.fadeEye == null ? 1 : Math.max(1, (int)Math.ceil(y.length() * sprite.height / .5));
            for (int ix = 0; ix < columns; ix++) for (int iy = 0; iy < rows; iy++) {
                float u0 = ix / (float)columns, u1 = (ix + 1f) / columns;
                float v0 = iy / (float)rows, v1 = (iy + 1f) / rows;
                vertex(out, matrix, pose, faceOrigin.add(x.scale(u0 * sprite.width - pivotX)).add(y.scale(v0 * sprite.height - pivotY)), u0, v0, faceNormal, alpha, light);
                vertex(out, matrix, pose, faceOrigin.add(x.scale(u0 * sprite.width - pivotX)).add(y.scale(v1 * sprite.height - pivotY)), u0, v1, faceNormal, alpha, light);
                vertex(out, matrix, pose, faceOrigin.add(x.scale(u1 * sprite.width - pivotX)).add(y.scale(v1 * sprite.height - pivotY)), u1, v1, faceNormal, alpha, light);
                vertex(out, matrix, pose, faceOrigin.add(x.scale(u1 * sprite.width - pivotX)).add(y.scale(v0 * sprite.height - pivotY)), u1, v0, faceNormal, alpha, light);
            }
        }
        if (sprite != LEFT && sprite != RIGHT && sprite != ITEM) return;
        for (int[] edge : contour(sprite)) {
            int px = edge[0], py = edge[1], side = edge[2];
            double ax = px, ay = py, bx = px, by = py;
            if (side < 2) { ax += side; bx = ax; by++; }
            else { ay += side - 2; by = ay; bx++; }
            Vec3 a = origin.add(x.scale(ax - pivotX)).add(y.scale(ay - pivotY));
            Vec3 b = origin.add(x.scale(bx - pivotX)).add(y.scale(by - pivotY));
            Vec3 n = side < 2 ? x.normalize().scale(side == 0 ? -1 : 1) : y.normalize().scale(side == 2 ? -1 : 1);
            float u = (px + .5f) / sprite.width, v = (py + .5f) / sprite.height;
            vertex(out, matrix, pose, a.add(normal.scale(depth)), u, v, n, alpha, light);
            vertex(out, matrix, pose, b.add(normal.scale(depth)), u, v, n, alpha, light);
            vertex(out, matrix, pose, b.add(normal.scale(-depth)), u, v, n, alpha, light);
            vertex(out, matrix, pose, a.add(normal.scale(-depth)), u, v, n, alpha, light);
        }
    }

    private void vertex(VertexConsumer out, Matrix4f matrix, PoseStack pose, Vec3 v, float u, float texV, Vec3 normal, double alpha, int light) {
        out.vertex(matrix, (float)v.x, (float)v.y, (float)v.z).color(255, 255, 255, (int)(255 * clamp(alpha * ArkFxDraw.opacityAt(v))))
                .uv(u, texV).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(pose.last().normal(), (float)normal.x, (float)normal.y, (float)normal.z).endVertex();
    }
    public static void renderHoverSword(PoseStack pose, MultiBufferSource buffers, Vec3 origin, Vec3 playerRight, int light) {
        ArkAttackRenderer renderer = INSTANCE;
        if (renderer == null) return;
        AttackFrame vertical = new AttackFrame(new Vec3(0, 1, 0), playerRight);
        renderer.sprite(pose, buffers, vertical, origin, ITEM, Math.PI / 2, .22f, .22f, ITEM.width / 2d, ITEM.height / 2d, 1, light);
    }
    private static double clamp(double value) { return Math.max(0, Math.min(1, value)); }
    private static double lerp(double from, double to, double value) { return from + (to - from) * value; }
}
