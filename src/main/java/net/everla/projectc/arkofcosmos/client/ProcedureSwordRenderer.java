package net.everla.projectc.arkofcosmos.client;

import com.google.gson.*;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
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
import java.util.*;
import java.util.function.Function;

/** EverlaArtifacts/Re-Avaritia mask geometry and cosmic shader, using Forge's model wrapper. */
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
        event.registerShader(new ShaderInstance(event.getResourceProvider(), id("procedure_cosmic"), DefaultVertexFormat.BLOCK), s -> shader=s);
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
        Model(BakedModel base,List<BakedQuad> mask) { super(base);this.mask=mask; }
        BakedModel base() { return originalModel; }
        @Override public boolean isCustomRenderer() { return true; }
        @Override public BakedModel applyTransform(ItemDisplayContext context,PoseStack pose,boolean left) {
            originalModel.applyTransform(context,pose,left); return this;
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
                        .setLightmapState(LIGHTMAP).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setTextureState(TEXTURE).createCompositeState(true));
    }
    @Override public void renderByItem(ItemStack stack,ItemDisplayContext context,PoseStack pose,MultiBufferSource buffers,int light,int overlay) {
        var mc=Minecraft.getInstance();
        if (!(mc.getItemRenderer().getModel(stack,mc.level,null,0) instanceof Model model)) return;
        for(var pass:model.base().getRenderPasses(stack,true))
            for(var type:pass.getRenderTypes(stack,true))
                mc.getItemRenderer().renderModelLists(pass,stack,light,overlay,pose,buffers.getBuffer(type));
        if(shader==null)return;
        if(buffers instanceof MultiBufferSource.BufferSource batch)batch.endBatch();
        shader.safeGetUniform("time").set(mc.level==null?0f:(float)(mc.level.getGameTime()%Integer.MAX_VALUE));
        shader.safeGetUniform("yaw").set(context==ItemDisplayContext.GUI||mc.player==null?0f:(float)Math.toRadians(mc.player.getYRot()));
        shader.safeGetUniform("pitch").set(context==ItemDisplayContext.GUI||mc.player==null?0f:-(float)Math.toRadians(mc.player.getXRot()));
        shader.safeGetUniform("externalScale").set(context==ItemDisplayContext.GUI?100f:1f);
        shader.safeGetUniform("opacity").set(1f);
        float[] uvs=new float[40];
        for(int i=0;i<10;i++) {
            var sprite=mc.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(id("fx/procedure_mcreator"));
            uvs[i*4]=sprite.getU0();uvs[i*4+1]=sprite.getV0();uvs[i*4+2]=sprite.getU1();uvs[i*4+3]=sprite.getV1();
        }
        shader.safeGetUniform("cosmicuvs").set(uvs);
        mc.getItemRenderer().renderQuadList(pose,buffers.getBuffer(CosmicType.TYPE),model.mask,stack,light,overlay);
        if(buffers instanceof MultiBufferSource.BufferSource batch)batch.endBatch(CosmicType.TYPE);
    }
}
