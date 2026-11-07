package net.everla.projectc.arkofcosmos.client;
import net.everla.projectc.arkofcosmos.entity.BrimstoneWitchEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
/** Same slim skin as Calamitas, with native walking and looking, without the boss shield. */
public final class BrimstoneWitchRenderer extends HumanoidMobRenderer<BrimstoneWitchEntity,PlayerModel<BrimstoneWitchEntity>> {
    public BrimstoneWitchRenderer(EntityRendererProvider.Context context){super(context,new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM),true),.4f);}
    @Override public ResourceLocation getTextureLocation(BrimstoneWitchEntity witch){return ResourceLocation.fromNamespaceAndPath("arkofcosmos","textures/entity/supreme_calamitas.png");}
}
