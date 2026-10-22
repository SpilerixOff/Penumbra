package fr.penumbra.journal;

import fr.penumbra.journal.JournalNetworking.OpenJournalS2CPacket;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JournalPacketTest {
    @Test
    void openingCarriesPersonalProgressAlongsideTheReadablePages() {
        OpenJournalS2CPacket source = new OpenJournalS2CPacket(List.of("host", "method_camera"), 2, 14,
                new StoryGuide.Snapshot(true, 2, false, false, 1, true, 3));
        RegistryFriendlyByteBuf buffer = buffer();
        try {
            OpenJournalS2CPacket.CODEC.encode(buffer, source);
            assertEquals(source, OpenJournalS2CPacket.CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    void consecutiveReadersDoNotInheritVictoryOrPagesFromEachOther() {
        ArrayList<String> readable = new ArrayList<>(List.of("host"));
        OpenJournalS2CPacket veteran = new OpenJournalS2CPacket(readable, 1, 14,
                new StoryGuide.Snapshot(true, 6, true, true, 2, true, 4));
        readable.clear();
        OpenJournalS2CPacket newcomer = new OpenJournalS2CPacket(List.of(), 0, 14,
                new StoryGuide.Snapshot(true, 0, false, true, 0, false, 0));
        RegistryFriendlyByteBuf buffer = buffer();
        try {
            OpenJournalS2CPacket.CODEC.encode(buffer, veteran);
            OpenJournalS2CPacket.CODEC.encode(buffer, newcomer);
            assertEquals(veteran, OpenJournalS2CPacket.CODEC.decode(buffer));
            OpenJournalS2CPacket second = OpenJournalS2CPacket.CODEC.decode(buffer);
            assertEquals(newcomer, second);
            assertFalse(second.story().won());
            assertEquals(List.of("host"), veteran.readableIds());
            assertThrows(UnsupportedOperationException.class, () -> veteran.readableIds().clear());
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    void decoderBoundsImpossibleCountersBeforeTheScreenReceivesThem() {
        RegistryFriendlyByteBuf buffer = buffer();
        try {
            buffer.writeVarInt(0); // readable list
            buffer.writeVarInt(-1); // progress
            buffer.writeVarInt(14); // total
            buffer.writeBoolean(true); // enabled
            buffer.writeVarInt(99); // stage
            buffer.writeBoolean(false); // won
            buffer.writeBoolean(false); // copies
            buffer.writeVarInt(-99); // beds
            buffer.writeBoolean(false); // visitor
            buffer.writeVarInt(Integer.MAX_VALUE); // observations
            OpenJournalS2CPacket decoded = OpenJournalS2CPacket.CODEC.decode(buffer);
            assertEquals(0, decoded.progress());
            assertEquals(new StoryGuide.Snapshot(true, 6, false, false, 0, false, 4), decoded.story());
        } finally {
            buffer.release();
        }
    }

    @Test
    void decoderRejectsOversizedPageListsBeforeAllocatingTheirContents() {
        RegistryFriendlyByteBuf buffer = buffer();
        try {
            buffer.writeVarInt(65);
            assertThrows(DecoderException.class, () -> OpenJournalS2CPacket.CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    }
}
