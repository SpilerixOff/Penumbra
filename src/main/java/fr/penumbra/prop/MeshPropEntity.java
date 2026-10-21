package fr.penumbra.prop;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Accessoire de décor porteur d'un maillage 3D (0.19.0).
 *
 * <p>C'est le support minimal du système de maillages : une entité qui ne fait
 * <b>rien</b>. Elle ne bouge pas, ne tombe pas, ne pousse personne, n'a pas de
 * points de vie et n'entre dans aucune logique du Directeur. Elle transporte
 * seulement trois informations vers le client — quel maillage afficher, à
 * quelle échelle, sous quelle orientation — et le renderer fait le reste.</p>
 *
 * <p>Pourquoi une entité plutôt qu'un bloc : le mod n'a aucun bloc, et une
 * entité évite toute la plomberie des états de bloc, des modèles JSON et des
 * objets-blocs. Elle se place aussi n'importe où, y compris à des coordonnées
 * fractionnaires et sous n'importe quel angle — ce qu'un bloc ne sait pas faire.</p>
 *
 * <p>Le nom du maillage est une chaîne libre : le client cherchera
 * {@code assets/penumbra/meshes/<nom>.obj} et affichera un objet invisible si
 * le fichier n'existe pas. Un pack de ressources peut donc ajouter ses propres
 * maillages sans modifier une ligne de code.</p>
 */
public class MeshPropEntity extends Entity {

    private static final EntityDataAccessor<String> DATA_MESH =
            SynchedEntityData.defineId(MeshPropEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> DATA_SCALE =
            SynchedEntityData.defineId(MeshPropEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<String> DATA_TEXTURE =
            SynchedEntityData.defineId(MeshPropEntity.class, EntityDataSerializers.STRING);

    /** Bornes de l'échelle : au-delà, un accessoire deviendrait un mur. */
    private static final float MIN_SCALE = 0.05f;
    private static final float MAX_SCALE = 8.0f;

    public MeshPropEntity(EntityType<? extends MeshPropEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_MESH, "");
        builder.define(DATA_SCALE, 1.0f);
        builder.define(DATA_TEXTURE, "");
    }

    /** Configure l'accessoire côté serveur. Le client suit par synchronisation. */
    public void configure(String mesh, String texture, float scale) {
        this.entityData.set(DATA_MESH, mesh == null ? "" : mesh);
        this.entityData.set(DATA_TEXTURE, texture == null ? "" : texture);
        this.entityData.set(DATA_SCALE,
                Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale)));
    }

    public String meshName() {
        return this.entityData.get(DATA_MESH);
    }

    /**
     * Texture à appliquer, sans préfixe ni extension. Vide = le renderer utilise
     * {@code textures/entity/prop/<maillage>.png}, ce qui couvre le cas courant
     * où maillage et texture portent le même nom.
     */
    public String textureName() {
        return this.entityData.get(DATA_TEXTURE);
    }

    public float scale() {
        return this.entityData.get(DATA_SCALE);
    }

    // ——— Une statue ne fait rien du tout ———

    @Override
    public void tick() {
        // Volontairement vide : pas de gravité, pas de mouvement, pas de coût.
        // Une entité de décor qui ticke pour rien est le premier reproche fait
        // aux mods de décoration.
    }

    @Override
    public boolean isPickable() {
        return true;      // sélectionnable au réticule, pour la commande de retrait
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith(Entity entity) {
        return false;     // on traverse le décor, comme un cadre photo
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        // Retirable par un joueur en créatif uniquement : un accessoire posé par
        // une structure ne doit pas disparaître sous un coup de squelette.
        if (source.getEntity() instanceof net.minecraft.world.entity.player.Player player
                && player.getAbilities().instabuild) {
            this.discard();
        }
        return false;
    }

    /** Sauvegardé avec le monde : une statue posée doit être encore là demain. */
    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.entityData.set(DATA_MESH, input.getStringOr("PenumbraMesh", ""));
        this.entityData.set(DATA_TEXTURE, input.getStringOr("PenumbraTexture", ""));
        this.entityData.set(DATA_SCALE, Math.max(MIN_SCALE, Math.min(MAX_SCALE,
                input.getFloatOr("PenumbraScale", 1.0f))));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putString("PenumbraMesh", meshName());
        output.putString("PenumbraTexture", textureName());
        output.putFloat("PenumbraScale", scale());
    }

    /** Fabrique : pose un accessoire à une position et un angle donnés. */
    public static MeshPropEntity place(ServerLevel level, Vec3 position, float yaw,
                                       String mesh, String texture, float scale) {
        MeshPropEntity prop = new MeshPropEntity(PropEntities.MESH_PROP, level);
        prop.snapTo(position.x, position.y, position.z, yaw, 0.0f);
        prop.configure(mesh, texture, scale);
        return level.addFreshEntity(prop) ? prop : null;
    }
}
