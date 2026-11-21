package net.everla.projectc.arkofcosmos.client;

import com.google.gson.*;
import com.mojang.blaze3d.vertex.*;
import com.mojang.blaze3d.systems.RenderSystem;
import net.everla.projectc.arkofcosmos.client.render.OculusShaderCompat;
import net.everla.projectc.arkofcosmos.item.ProcedureSwordItem;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.resources.model.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.geometry.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.*;
import java.util.function.Function;

/** Masked EverlaArtifacts overlay, with a separate material effect for Threadripper. */
@Mod.EventBusSubscriber(modid="arkofcosmos", value=Dist.CLIENT, bus=Mod.EventBusSubscriber.Bus.MOD)
public final class ProcedureSwordRenderer extends BlockEntityWithoutLevelRenderer {
    public static final ProcedureSwordRenderer INSTANCE = new ProcedureSwordRenderer();
    private static ShaderInstance shader;
    private ProcedureSwordRenderer() { super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels()); }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("arkofcosmos", path); }

    /** ArcaneVortex's legacy sword block, scoped to this item's active hand. */
    public static boolean blockTransform(PoseStack pose, net.minecraft.client.player.LocalPlayer player,
            net.minecraft.world.entity.HumanoidArm arm, ItemStack stack, float swing) {
        var usedArm=player.getUsedItemHand()==net.minecraft.world.InteractionHand.MAIN_HAND?player.getMainArm():player.getMainArm().getOpposite();
        if (!player.isUsingItem() || !stack.is(player.getUseItem().getItem()) || arm!=usedArm) return false;
        int sign=arm==net.minecraft.world.entity.HumanoidArm.RIGHT?1:-1;
        float square=net.minecraft.util.Mth.sin(swing*swing*(float)Math.PI);
        float root=net.minecraft.util.Mth.sin(net.minecraft.util.Mth.sqrt(swing)*(float)Math.PI);
        pose.translate(sign*.56f,-.52f,-.71999997f);
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(sign*45));
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(sign*square*-20));
        pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(sign*root*-20));
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(root*-80));
        pose.scale(.4f,.4f,.4f);
        pose.translate(sign*-.5f,.2f,0);
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(sign*30));
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-80));
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(sign*60));
        pose.scale(2.5f,2.5f,2.5f);
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(sign*-45));
        return true;
    }

    @SubscribeEvent public static void shaders(RegisterShadersEvent event) throws java.io.IOException {
        shader=null;
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), id("procedure_cosmic"), DefaultVertexFormat.BLOCK), s -> shader=s);
        } catch (java.io.IOException error) {
            com.mojang.logging.LogUtils.getLogger().warn("Procedure sword overlay unavailable; using the base model", error);
        }
    }
    @SubscribeEvent public static void loaders(ModelEvent.RegisterGeometryLoaders event) {
        event.register("procedure_sword", (IGeometryLoader<Geometry>)(json, context) -> {
            var clean=json.deepCopy(); clean.remove("loader");
            return new Geometry(context.deserialize(clean, BlockModel.class));
        });
    }
    private record Geometry(BlockModel base) implements IUnbakedGeometry<Geometry> {
        @Override public void resolveParents(Function<ResourceLocation, UnbakedModel> getter, IGeometryBakingContext context) { base.resolveParents(getter); }
        @Override public BakedModel bake(IGeometryBakingContext context, ModelBaker baker, Function<Material,TextureAtlasSprite> sprites,
                ModelState state, ItemOverrides overrides, ResourceLocation location) {
            BakedModel model=base.bake(baker,base,sprites,state,location,true);
            var sprite=sprites.apply(new Material(InventoryMenu.BLOCK_ATLAS,id("item/procedure_sword_mask")));
            var quads=new ArrayList<BakedQuad>();
            var bakery=new FaceBakery();
            for(var element:new ItemModelGenerator().processFrames(0,"layer0",sprite.contents()))
                for(var face:element.faces.entrySet())
                    quads.add(bakery.bakeQuad(element.from,element.to,face.getValue(),sprite,face.getKey(),BlockModelRotation.X0_Y0,element.rotation,element.shade,location));
            return new Model(model,quads);
        }
    }
    private static final class Model extends BakedModelWrapper<BakedModel> {
        final List<BakedQuad> mask;
        final BladeFace front, back;
        Model(BakedModel base,List<BakedQuad> mask) {
            super(base);
            this.mask=mask;
            var faces=base.getQuads(null,null,RandomSource.create(42));
            front=BladeFace.find(faces,Direction.SOUTH);
            back=BladeFace.find(faces,Direction.NORTH);
        }
        BakedModel base() { return originalModel; }
        @Override public boolean isCustomRenderer() { return true; }
        @Override public BakedModel applyTransform(ItemDisplayContext context,PoseStack pose,boolean left) {
            originalModel.applyTransform(context,pose,left); return this;
        }
    }
    /** Sprite center and half-axes reconstructed from the actual baked face UVs. */
    record BladeFace(Vec3 center, Vec3 x, Vec3 y) {
        static BladeFace find(List<BakedQuad> quads, Direction side) {
            for (BakedQuad quad : quads) {
                var sprite=quad.getSprite();
                if (quad.getDirection()!=side || !sprite.contents().name().equals(id("item/procedure_sword"))) continue;
                int[] data=quad.getVertices();
                int stride=data.length/4;
                float middleU=(sprite.getU0()+sprite.getU1())*.5f, middleV=(sprite.getV0()+sprite.getV1())*.5f;
                Vec3 center=Vec3.ZERO,x=Vec3.ZERO,y=Vec3.ZERO;
                float minU=Float.POSITIVE_INFINITY,maxU=Float.NEGATIVE_INFINITY;
                float minV=Float.POSITIVE_INFINITY,maxV=Float.NEGATIVE_INFINITY;
                for (int i=0;i<data.length;i+=stride) {
                    Vec3 p=new Vec3(Float.intBitsToFloat(data[i]),Float.intBitsToFloat(data[i+1]),Float.intBitsToFloat(data[i+2]));
                    float u=Float.intBitsToFloat(data[i+4]),v=Float.intBitsToFloat(data[i+5]);
                    center=center.add(p);
                    x=x.add(p.scale(u<middleU?-1:1));
                    y=y.add(p.scale(v<middleV?1:-1));
                    minU=Math.min(minU,u);maxU=Math.max(maxU,u);
                    minV=Math.min(minV,v);maxV=Math.max(maxV,v);
                }
                float spanU=sprite.getU1()-sprite.getU0(),spanV=sprite.getV1()-sprite.getV0();
                // Ignore side strips; undo the atlas inset so the shader can read raw sprite UVs.
                if(maxU-minU>spanU*.8f && maxV-minV>spanV*.8f)
                    return new BladeFace(center.scale(.25),x.scale(.25*spanU/(maxU-minU)),y.scale(.25*spanV/(maxV-minV)));
            }
            return null;
        }
    }
    private static final class CosmicType extends RenderType {
        private CosmicType() { super("procedure_cosmic",DefaultVertexFormat.BLOCK,VertexFormat.Mode.QUADS,256,false,false,()->{},()->{}); }
        // Match the source's isolated atlas binding: lightmap setup must not leave a different texture unit active.
        private static final TextureStateShard TEXTURE = new TextureStateShard(InventoryMenu.BLOCK_ATLAS,false,false) {
            @Override public void setupRenderState() {
                super.setupRenderState();
                com.mojang.blaze3d.systems.RenderSystem.activeTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0);
                com.mojang.blaze3d.systems.RenderSystem.bindTexture(Minecraft.getInstance().getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS).getId());
            }
        };
        static final RenderType TYPE=create("procedure_cosmic",DefaultVertexFormat.BLOCK,VertexFormat.Mode.QUADS,256,false,false,
                CompositeState.builder().setShaderState(new ShaderStateShard(()->shader)).setDepthTestState(EQUAL_DEPTH_TEST)
                        .setWriteMaskState(COLOR_WRITE).setLightmapState(LIGHTMAP).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setTextureState(TEXTURE).createCompositeState(false));
        // Shader packs can displace depth; retain occlusion without requiring exact equality.
        static final RenderType COMPAT=create("procedure_cosmic_compat",DefaultVertexFormat.BLOCK,VertexFormat.Mode.QUADS,256,false,false,
                CompositeState.builder().setShaderState(new ShaderStateShard(()->shader)).setDepthTestState(LEQUAL_DEPTH_TEST)
                        .setWriteMaskState(COLOR_WRITE).setLightmapState(LIGHTMAP).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setTextureState(TEXTURE).createCompositeState(false));
    }
    @Override public void renderByItem(ItemStack stack,ItemDisplayContext context,PoseStack pose,MultiBufferSource buffers,int light,int overlay) {
        var mc=Minecraft.getInstance();
        if (!(mc.getItemRenderer().getModel(stack,mc.level,null,0) instanceof Model model)) return;
        Runnable trail=context.firstPerson()?ThreadripperHandTrail.capture(pose,context):null;
        boolean glare=mc.level!=null && ProcedureSwordItem.unrestricted(mc.player);
        float glareTime=glare?mc.level.getGameTime()%72000+mc.getFrameTime():0;
        float tipFlash=glare?ThreadripperHandTrail.tipFlash(context):0;
        boolean effects=shader!=null || trail!=null || glare;
        boolean deferred=context!=ItemDisplayContext.GUI && OculusShaderCompat.shaderPackActive();
        // EQUAL_DEPTH_TEST needs the blade's depth now. Flush only our blade;
        // the caller owns pending player geometry, including YSM's batches.
        var immediate=effects && !deferred ? MultiBufferSource.immediate(new BufferBuilder(256)) : null;
        try (var state = effects ? new OculusShaderCompat.RasterState() : null) {
            var baseBuffers=immediate==null ? buffers : immediate;
            for(var pass:model.base().getRenderPasses(stack,true))
                for(var type:pass.getRenderTypes(stack,true))
                    mc.getItemRenderer().renderModelLists(pass,stack,light,overlay,pose,baseBuffers.getBuffer(type));
            if(!effects)return;
            if(immediate!=null)immediate.endBatch();
            if(deferred) {
                var captured=new PoseStack();
                captured.last().pose().set(pose.last().pose());
                captured.last().normal().set(pose.last().normal());
                var copy=stack.copy();
                if(OculusShaderCompat.deferItem(()->{
                    var isolated=MultiBufferSource.immediate(new BufferBuilder(256));
                    if(shader!=null)renderCosmic(copy,context,captured,isolated,light,overlay,model,CosmicType.COMPAT);
                    isolated.endBatch();
                    if(glare)ThreadripperHandTrail.glow(captured,context,glareTime,tipFlash,model.front,model.back);
                    if(trail!=null)trail.run();
                },context.firstPerson()))return;
            }
            if(shader!=null)renderCosmic(stack,context,pose,immediate!=null?immediate:MultiBufferSource.immediate(new BufferBuilder(256)),light,overlay,model,
                    deferred?CosmicType.COMPAT:CosmicType.TYPE);
            if(glare)ThreadripperHandTrail.glow(pose,context,glareTime,tipFlash,model.front,model.back);
            if(trail!=null)trail.run();
        }
    }
    private static void renderCosmic(ItemStack stack,ItemDisplayContext context,PoseStack pose,MultiBufferSource buffers,
            int light,int overlay,Model model,RenderType type) {
        var mc=Minecraft.getInstance();
        boolean authorized=ProcedureSwordItem.unrestricted(mc.player);
        var atlas=mc.getTextureAtlas(InventoryMenu.BLOCK_ATLAS);
        shader.safeGetUniform("threadripper").set(authorized?1:0);
        shader.safeGetUniform("yaw").set(context==ItemDisplayContext.GUI||mc.player==null?0f:(float)Math.toRadians(mc.player.getYRot()));
        shader.safeGetUniform("pitch").set(context==ItemDisplayContext.GUI||mc.player==null?0f:-(float)Math.toRadians(mc.player.getXRot()));
        shader.safeGetUniform("externalScale").set(context==ItemDisplayContext.GUI?100f:1f);
        shader.safeGetUniform("opacity").set(1f);
        if(authorized) {
            Vector3f eye=new Vector3f();
            if(context!=ItemDisplayContext.GUI) {
                new Matrix4f(RenderSystem.getModelViewMatrix()).mul(pose.last().pose()).invert().transformPosition(eye);
                eye.sub(.5f,.5f,.5f);
                if(eye.lengthSquared()>1e-6f)eye.normalize();
            }
            shader.safeGetUniform("viewLean").set((eye.x+eye.y)*.5f,(eye.x-eye.y)*.5f);
            var mask=atlas.apply(id("item/procedure_sword_mask"));
            shader.safeGetUniform("maskUv").set(mask.getU0(),mask.getV0(),mask.getU1(),mask.getV1());
            shader.safeGetUniform("time").set(mc.level==null?0f:mc.level.getGameTime()%72000+mc.getFrameTime());
        } else {
            shader.safeGetUniform("time").set(mc.level==null?0f:(float)(mc.level.getGameTime()%Integer.MAX_VALUE));
        }
        float[] uvs=new float[40];
        for(int i=0;i<10;i++) {
            var sprite=atlas.apply(id(authorized?"fx/cosmic_"+i:"fx/procedure_mcreator"));
            uvs[i*4]=sprite.getU0();uvs[i*4+1]=sprite.getV0();uvs[i*4+2]=sprite.getU1();uvs[i*4+3]=sprite.getV1();
        }
        shader.safeGetUniform("cosmicuvs").set(uvs);
        mc.getItemRenderer().renderQuadList(pose,buffers.getBuffer(type),model.mask,stack,light,overlay);
        if(buffers instanceof MultiBufferSource.BufferSource batch)batch.endBatch(type);
    }
}
