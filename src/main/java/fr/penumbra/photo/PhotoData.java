package fr.penumbra.photo;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

/**
 * Référence légère et persistante vers une vraie capture stockée par le monde.
 *
 * @param projectionist vrai uniquement pour les rares négatifs marqués par le
 *                      Projectionniste. Le champ est optionnel dans le codec
 *                      afin que les photos des anciens mondes restent valides.
 */
public record PhotoData(UUID photoId, long capturedAtTick, boolean projectionist) {

    /** Dix secondes, avec un changement visible continu côté client. */
    public static final int DEVELOPMENT_TICKS = 200;

    public static final Codec<PhotoData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("photo_id").forGetter(PhotoData::photoId),
            Codec.LONG.fieldOf("captured_at").forGetter(PhotoData::capturedAtTick),
            Codec.BOOL.optionalFieldOf("projectionist", false).forGetter(PhotoData::projectionist)
    ).apply(instance, PhotoData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, PhotoData> STREAM_CODEC =
            StreamCodec.composite(
                    UUIDUtil.STREAM_CODEC, PhotoData::photoId,
                    ByteBufCodecs.VAR_LONG, PhotoData::capturedAtTick,
                    ByteBufCodecs.BOOL, PhotoData::projectionist,
                    PhotoData::new);

    /** Compatibilité source avec les captures créées avant le Projectionniste. */
    public PhotoData(UUID photoId, long capturedAtTick) {
        this(photoId, capturedAtTick, false);
    }

    public PhotoData {
        if (photoId == null) {
            throw new IllegalArgumentException("photoId");
        }
    }

    public float progress(long gameTime) {
        return developmentProgress(gameTime, capturedAtTick);
    }

    public int stage(long gameTime) {
        return developmentStage(gameTime, capturedAtTick);
    }

    public static float developmentProgress(long gameTime, long capturedAtTick) {
        long age = Math.max(0L, gameTime - capturedAtTick);
        return Math.min(1.0f, age / (float) DEVELOPMENT_TICKS);
    }

    /** 0 noir, 1 apparition faible, 2 sépia, 3 couleurs complètes. */
    public static int developmentStage(long gameTime, long capturedAtTick) {
        long age = Math.max(0L, gameTime - capturedAtTick);
        if (age < 50L) {
            return 0;
        }
        if (age < 115L) {
            return 1;
        }
        if (age < DEVELOPMENT_TICKS) {
            return 2;
        }
        return 3;
    }
}
