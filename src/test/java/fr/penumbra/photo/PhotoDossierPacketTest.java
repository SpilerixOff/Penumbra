package fr.penumbra.photo;

import fr.penumbra.network.PhotoNetworking.AlbumPageS2C;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PhotoDossierPacketTest {

    @Test
    void networkRoundTripKeepsAllTenPersonalEvidenceIds() {
        List<String> all = Arrays.stream(PhotoEvidence.values())
                .filter(PhotoEvidence::discoverable).map(PhotoEvidence::id).toList();
        AlbumPageS2C packet = new AlbumPageS2C((byte) 0, 0, 0, 0, 0, all, List.of());
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            AlbumPageS2C.CODEC.encode(buffer, packet);
            AlbumPageS2C decoded = AlbumPageS2C.CODEC.decode(buffer);
            assertEquals(all, decoded.dossierEvidenceIds());
            assertEquals(10, decoded.dossierDiscovered());
            assertEquals(4, decoded.dossierStage());
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    void unknownAndDuplicateIdsCannotInflateClientProgress() {
        AlbumPageS2C packet = new AlbumPageS2C((byte) 1, 0, 0, 4, 999,
                Arrays.asList("HOST", null, "host", "future_creature", "none"), List.of());
        assertEquals(List.of("host"), packet.dossierEvidenceIds());
        assertEquals(1, packet.dossierDiscovered());
        assertEquals(1, packet.dossierStage());
    }

    @Test
    void decoderRejectsAnOversizedDossierBeforeReadingItsEntries() {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            buffer.writeByte(0);
            buffer.writeVarInt(0); // page
            buffer.writeVarInt(0); // photos
            buffer.writeVarInt(0); // stage
            buffer.writeVarInt(0); // count
            buffer.writeVarInt(PhotoEvidence.discoverableCount() + 1);
            assertThrows(DecoderException.class, () -> AlbumPageS2C.CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }
}
