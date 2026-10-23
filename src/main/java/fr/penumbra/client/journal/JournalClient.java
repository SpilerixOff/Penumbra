package fr.penumbra.client.journal;

import fr.penumbra.journal.JournalNetworking;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Récepteur client du Relevé : ouvre l'écran à réception du paquet serveur.
 * Aucun état n'est conservé entre deux ouvertures — la connaissance appartient
 * au serveur, le client n'en reçoit qu'une photographie à l'instant du clic.
 */
@Environment(EnvType.CLIENT)
public final class JournalClient {

    private JournalClient() {
    }

    /** À appeler depuis {@code PenumbraClient.onInitializeClient()}. */
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(
                JournalNetworking.OpenJournalS2CPacket.TYPE,
                (packet, context) -> context.client().execute(() -> {
                    Set<String> readable = new LinkedHashSet<>(packet.readableIds());
                    context.client().setScreen(new SurveyJournalScreen(
                            readable, packet.progress(), packet.total(), packet.story(), packet.focusId()));
                }));
    }
}
