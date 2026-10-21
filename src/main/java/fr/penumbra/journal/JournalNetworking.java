package fr.penumbra.journal;

import fr.penumbra.PenumbraMod;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Ouverture du Relevé (0.18.0) — un seul paquet, dans un seul sens.
 *
 * <p>Le serveur reste la source de vérité : le client ne demande jamais ce
 * qu'il a le droit de lire, c'est le serveur qui le lui envoie au moment du
 * clic droit. Un client modifié ne peut donc pas déverrouiller des pages qu'il
 * n'a pas gagnées — au pire il ouvre un carnet vide.</p>
 *
 * <p>La charge utile est volontairement minuscule : la liste des identifiants
 * de pages entièrement lisibles, plus les deux champs du frontispice. Tous les
 * textes eux-mêmes vivent dans les fichiers de langue, côté client.</p>
 */
public final class JournalNetworking {

    /** Borne défensive : le carnet ne contient de toute façon que quinze pages. */
    private static final int MAX_IDS = 64;

    /**
     * Ouvre le carnet chez le destinataire avec l'état exact de sa connaissance.
     *
     * @param readableIds identifiants des pages entièrement lisibles
     * @param progress    pages révélées, hors frontispice
     * @param total       pages révélables au total
     */
    public record OpenJournalS2CPacket(List<String> readableIds, int progress, int total)
            implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<OpenJournalS2CPacket> TYPE =
                new CustomPacketPayload.Type<>(PenumbraMod.id("open_journal"));

        public static final StreamCodec<? super RegistryFriendlyByteBuf, OpenJournalS2CPacket> CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(MAX_IDS)),
                        OpenJournalS2CPacket::readableIds,
                        ByteBufCodecs.VAR_INT, OpenJournalS2CPacket::progress,
                        ByteBufCodecs.VAR_INT, OpenJournalS2CPacket::total,
                        OpenJournalS2CPacket::new);

        @Override
        public CustomPacketPayload.Type<OpenJournalS2CPacket> type() {
            return TYPE;
        }
    }

    private JournalNetworking() {
    }

    /** Enregistre le payload en phase play, sens serveur → client. */
    public static void registerPayloads() {
        PayloadTypeRegistry.clientboundPlay().register(
                OpenJournalS2CPacket.TYPE, OpenJournalS2CPacket.CODEC);
    }

    /** Envoie l'état courant du carnet et déclenche son ouverture chez le joueur. */
    public static void open(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        JournalKnowledge knowledge = JournalKnowledge.get(server);
        List<String> readable = knowledge.readableIds(player.getUUID());
        if (readable.size() > MAX_IDS) {
            readable = readable.subList(0, MAX_IDS);
        }
        ServerPlayNetworking.send(player, new OpenJournalS2CPacket(
                readable,
                knowledge.progress(player.getUUID()),
                JournalEntry.discoverableCount()));
    }
}
