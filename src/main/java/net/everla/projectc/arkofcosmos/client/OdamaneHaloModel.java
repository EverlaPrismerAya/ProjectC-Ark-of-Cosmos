package net.everla.projectc.arkofcosmos.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.everla.projectc.arkofcosmos.entity.MahouTsukaiEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.util.Mth;

/** Standalone reconstruction of the reference halo's pivots, 21 planes and five-part animation.
 * Source asset/data provenance: reference/mahou/odamane-halo.json. No GR or Curios runtime linkage.
 */
public final class OdamaneHaloModel extends EntityModel<MahouTsukaiEntity> {
    public final ModelPart halo;
    private final ModelPart center, right, left, bottom, top;

    public OdamaneHaloModel(ModelPart root) {
        halo = root.getChild("head").getChild("Halo");
        center = halo.getChild("bone");
        right = halo.getChild("bone2"); left = halo.getChild("bone3");
        bottom = halo.getChild("bone4"); top = halo.getChild("bone5");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition meshdefinition = PlayerModel.createMesh(CubeDeformation.NONE, true);
        PartDefinition partdefinition = meshdefinition.getRoot();
        PartDefinition head = partdefinition.getChild("head");
        PartDefinition Halo = head.addOrReplaceChild("Halo", CubeListBuilder.create(), PartPose.offset(0.0f, -5.0f, 10.0f));
        PartDefinition bone = Halo.addOrReplaceChild("bone", CubeListBuilder.create().texOffs(0, 0).addBox(-9.0f, -9.0f, 0.0f, 18.0f, 18.0f, 0.0f, new CubeDeformation(0.0f)).texOffs(0, 18).addBox(9.0f, -3.0f, 0.0f, 8.0f, 6.0f, 0.0f, new CubeDeformation(0.0f)).texOffs(16, 18).addBox(-17.0f, -3.0f, 0.0f, 8.0f, 6.0f, 0.0f, new CubeDeformation(0.0f)).texOffs(0, 24).addBox(-1.5f, 9.0f, 0.0f, 4.0f, 6.0f, 0.0f, new CubeDeformation(0.0f)).texOffs(8, 24).addBox(-1.5f, -15.0f, 0.0f, 4.0f, 6.0f, 0.0f, new CubeDeformation(0.0f)).texOffs(8, 24).mirror().addBox(-2.5f, -15.0f, 0.0f, 4.0f, 6.0f, 0.0f, new CubeDeformation(0.0f)).mirror(false).texOffs(0, 24).mirror().addBox(-2.5f, 9.0f, 0.0f, 4.0f, 6.0f, 0.0f, new CubeDeformation(0.0f)).mirror(false), PartPose.offset(0.0f, 0.0f, 0.0f));
        PartDefinition bone2 = Halo.addOrReplaceChild("bone2", CubeListBuilder.create().texOffs(16, 31).addBox(20.5f, -1.5f, 0.0f, 7.0f, 3.0f, 0.0f, new CubeDeformation(0.0f)), PartPose.offset(0.0f, 0.0f, 0.0f));
        PartDefinition cube_r1 = bone2.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(3, 35).mirror().addBox(0.2164f, -0.9763f, 0.0f, 2.0f, 1.0f, 0.0f, new CubeDeformation(0.0f)).mirror(false), PartPose.offsetAndRotation(21.0237f, -0.7836f, 0.0f, 0.0f, 0.0f, -1.789f));
        PartDefinition cube_r2 = bone2.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(5, 34).mirror().addBox(-1.0f, 0.0f, 0.0f, 2.0f, 1.0f, 0.0f, new CubeDeformation(0.0f)).mirror(false), PartPose.offsetAndRotation(19.7456f, 1.653f, 0.0f, 0.0f, 0.0f, -1.2654f));
        PartDefinition cube_r3 = bone2.addOrReplaceChild("cube_r3", CubeListBuilder.create().texOffs(1, 34).mirror().addBox(1.0f, 19.0f, 0.0f, 2.0f, 1.0f, 0.0f, new CubeDeformation(5.0E-4f)).mirror(false), PartPose.offsetAndRotation(1.0f, 2.0f, 0.0f, 0.0f, 0.0f, -1.5708f));
        PartDefinition bone3 = Halo.addOrReplaceChild("bone3", CubeListBuilder.create().texOffs(16, 31).mirror().addBox(-27.5f, -1.5f, 0.0f, 7.0f, 3.0f, 0.0f, new CubeDeformation(0.0f)).mirror(false), PartPose.offset(0.0f, 0.0f, 0.0f));
        PartDefinition cube_r4 = bone3.addOrReplaceChild("cube_r4", CubeListBuilder.create().texOffs(7, 35).addBox(-1.0f, 0.0f, 0.0f, 2.0f, 1.0f, 0.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation(-19.7456f, 1.653f, 0.0f, 0.0f, 0.0f, 1.2654f));
        PartDefinition cube_r5 = bone3.addOrReplaceChild("cube_r5", CubeListBuilder.create().texOffs(1, 36).addBox(-3.0f, 19.0f, 0.0f, 2.0f, 1.0f, 0.0f, new CubeDeformation(5.0E-4f)), PartPose.offsetAndRotation(-1.0f, 2.0f, 0.0f, 0.0f, 0.0f, 1.5708f));
        PartDefinition cube_r6 = bone3.addOrReplaceChild("cube_r6", CubeListBuilder.create().texOffs(1, 35).addBox(-2.2164f, -0.9763f, 0.0f, 2.0f, 1.0f, 0.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation(-21.0237f, -0.7836f, 0.0f, 0.0f, 0.0f, 1.789f));
        PartDefinition bone4 = Halo.addOrReplaceChild("bone4", CubeListBuilder.create().texOffs(16, 36).addBox(-0.5f, 17.5f, 0.0f, 1.0f, 1.0f, 0.0f, new CubeDeformation(-0.005f)), PartPose.offset(0.0f, 0.0f, 0.0f));
        PartDefinition cube_r7 = bone4.addOrReplaceChild("cube_r7", CubeListBuilder.create().texOffs(16, 29).mirror().addBox(-1.0f, 0.0f, -1.0f, 2.0f, 1.0f, 0.0f, new CubeDeformation(-5.0E-4f)).mirror(false), PartPose.offsetAndRotation(-0.7218f, 17.0f, 1.0f, 0.0f, 0.0f, 0.2182f));
        PartDefinition cube_r8 = bone4.addOrReplaceChild("cube_r8", CubeListBuilder.create().texOffs(17, 29).mirror().addBox(-1.0f, 0.0f, -1.0f, 2.0f, 1.0f, 0.0f, new CubeDeformation(-5.0E-4f)).mirror(false), PartPose.offsetAndRotation(0.7218f, 17.0f, 1.0f, 0.0f, 0.0f, -0.2182f));
        PartDefinition bone5 = Halo.addOrReplaceChild("bone5", CubeListBuilder.create().texOffs(16, 36).addBox(-0.5f, -18.5f, 0.0f, 1.0f, 1.0f, 0.0f, new CubeDeformation(-9.0E-4f)), PartPose.offset(0.0f, 0.0f, 0.0f));
        PartDefinition cube_r9 = bone5.addOrReplaceChild("cube_r9", CubeListBuilder.create().texOffs(16, 29).addBox(-1.0f, -1.0f, -1.0f, 2.0f, 1.0f, 0.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation(0.7218f, -17.0f, 1.0f, 0.0f, 0.0f, 0.2182f));
        PartDefinition cube_r10 = bone5.addOrReplaceChild("cube_r10", CubeListBuilder.create().texOffs(18, 29).addBox(-1.0f, -1.0f, -1.0f, 2.0f, 1.0f, 0.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation(-0.7218f, -17.0f, 1.0f, 0.0f, 0.0f, -0.2182f));
        return LayerDefinition.create(meshdefinition, 64, 64);
    }

    public void animate(float angle) {
        right.zRot = left.zRot = angle;
        bottom.zRot = top.zRot = -angle;
        center.y = Mth.cos(angle) + 1f;
    }

    public static float rotation(int nameLength, double seconds) {
        return (Mth.cos(nameLength) * 3201123f + (float) seconds * 90f) * Mth.DEG_TO_RAD;
    }

    @Override public void setupAnim(MahouTsukaiEntity boss, float limbSwing, float amount, float age, float yaw, float pitch) {}

    @Override public void renderToBuffer(PoseStack pose, VertexConsumer out, int light, int overlay,
                                        float red, float green, float blue, float alpha) {
        halo.render(pose, out, light, overlay, red, green, blue, alpha);
    }
}
