package fr.penumbra.client.entity;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import fr.penumbra.PenumbraMod;
import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.entity.absent.AbsentEntity;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.phys.Vec3;

/** Renderer de L'Absent : skin volé au loin, vraie peau lors de la rupture. */
@Environment(EnvType.CLIENT)
public final class AbsentRenderer
        extends HumanoidMobRenderer<AbsentEntity, AbsentRenderState, AbsentModel> {

    private static final Identifier MONSTER_TEXTURE =
            PenumbraMod.id("textures/entity/absent.png");
    private static final int MAX_CACHED_SKINS = 48;
    /** Marge empêchant le texte centré de couper le sommet du modèle joueur. */
    private static final double FALSE_COMPANION_NAME_TAG_LIFT = 0.24;

    /**
     * Les suppliers de SkinManager lancent au plus une résolution asynchrone
     * par UUID. L'accès est ensuite une simple lecture du cache de Minecraft.
     */
    private final Map<UUID, SkinLookup> skinLookups = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, SkinLookup> eldest) {
            return this.size() > MAX_CACHED_SKINS;
        }
    };

    public AbsentRenderer(EntityRendererProvider.Context context) {
        super(context, new AbsentModel(context.bakeLayer(AbsentModel.LAYER)), 0.50f);
        AbsentModel parentModel = this.getModel();
        ArmorModelSet<AbsentArmorModel> armorModels = ArmorModelSet.bake(
                ModelLayers.PLAYER_ARMOR, context.getModelSet(),
                root -> new AbsentArmorModel(root, parentModel));
        this.addLayer(new HumanoidArmorLayer<>(this, armorModels,
                context.getEquipmentRenderer()));
    }

    @Override
    public Identifier getTextureLocation(AbsentRenderState state) {
        return state.usingPlayerSkin && state.skinTexture != null
                ? state.skinTexture
                : MONSTER_TEXTURE;
    }

    @Override
    public AbsentRenderState createRenderState() {
        return new AbsentRenderState();
    }

    /** L'armure et les objets volés disparaissent dès que la vraie forme perce. */
    @Override
    protected boolean shouldRenderLayers(AbsentRenderState state) {
        return state.usingPlayerSkin && super.shouldRenderLayers(state);
    }

    @Override
    public void extractRenderState(AbsentEntity entity, AbsentRenderState state,
                                   float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.absentPhase = entity.getAbsentPhase();
        state.phaseTicks = entity.getVisualPhaseTicks(partialTick);
        state.mimicBehavior = entity.getMimicBehavior();
        state.gaitSignature = entity.getGaitSignature();
        state.locomotionSeed = entity.getLocomotionSeed();
        state.revealProgress = entity.getRevealProgress();
        state.imitationError = entity.getImitationError();
        state.modelPartsMask = entity.getModelPartsMask();
        state.lunging = entity.isLunging();
        state.falseCompanion = entity.isFalseCompanion();
        if (state.falseCompanion && state.nameTagAttachment != null) {
            state.nameTagAttachment = state.nameTagAttachment.add(
                    0.0, FALSE_COMPANION_NAME_TAG_LIFT, 0.0);
        }

        PenumbraConfig config = PenumbraConfig.get();
        state.movementImitationEnabled = state.falseCompanion
                || config.absentMovementImitationEnabled;
        boolean disguisePhase = state.absentPhase < AbsentEntity.PHASE_HUNT
                && state.revealProgress < 0.58f;
        state.usingPlayerSkin = (state.falseCompanion || config.absentSkinCopyEnabled)
                && disguisePhase;
        if (state.usingPlayerSkin) {
            PlayerSkin skin = this.resolveSkin(entity);
            state.skinTexture = skin.body().texturePath();
            state.slimArms = skin.model() == PlayerModelType.SLIM;
        } else {
            state.skinTexture = null;
            state.slimArms = false;
        }
    }

    /** La silhouette copiée possède exactement l'échelle du renderer joueur. */
    @Override
    protected void scale(AbsentRenderState state, PoseStack poseStack) {
        if (state.usingPlayerSkin) {
            poseStack.scale(0.9375f, 0.9375f, 0.9375f);
        }
    }

    /** Même correction verticale que le renderer joueur lorsqu'il s'accroupit. */
    @Override
    public Vec3 getRenderOffset(AbsentRenderState state) {
        Vec3 offset = super.getRenderOffset(state);
        return state.falseCompanion && state.isCrouching
                ? offset.add(0.0, state.scale * -2.0 / 16.0, 0.0)
                : offset;
    }

    private PlayerSkin resolveSkin(AbsentEntity entity) {
        UUID uuid = entity.getImitatedUuid();
        if (uuid == null) {
            return DefaultPlayerSkin.getDefaultSkin();
        }

        Minecraft minecraft = Minecraft.getInstance();
        ClientPacketListener connection = minecraft.getConnection();
        PlayerInfo onlinePlayer = connection == null ? null : connection.getPlayerInfo(uuid);
        if (onlinePlayer != null) {
            // Le PlayerInfo partage le cache officiel du client et inclut les
            // propriétés signées envoyées par le serveur.
            return onlinePlayer.getSkin();
        }

        ResolvableProfile profile = entity.getImitatedProfile();
        GameProfile partialProfile = profile == null
                ? new GameProfile(uuid, entity.getImitatedName())
                : profile.partialProfile();
        SkinLookup lookup = this.skinLookups.computeIfAbsent(uuid,
                ignored -> new SkinLookup(
                        minecraft.getSkinManager().createLookup(partialProfile, false),
                        DefaultPlayerSkin.get(uuid)));
        return lookup.current();
    }

    private record SkinLookup(Supplier<PlayerSkin> supplier, PlayerSkin fallback) {
        private PlayerSkin current() {
            PlayerSkin skin = this.supplier.get();
            return skin == null ? this.fallback : skin;
        }
    }
}
