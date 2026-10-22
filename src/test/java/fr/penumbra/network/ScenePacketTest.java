package fr.penumbra.network;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ScenePacketTest {
    @Test
    void successiveScreenStatesRoundTripWithoutLeavingUnreadBytes() {
        RegistryFriendlyByteBuf buffer = buffer();
        try {
            for (int kind : new int[]{SceneScreenState.OCCUPIED, SceneScreenState.OTHER, SceneScreenState.NONE})
                PenumbraC2S.SceneScreenC2S.CODEC.encode(buffer, new PenumbraC2S.SceneScreenC2S(kind));
            assertEquals(SceneScreenState.OCCUPIED, PenumbraC2S.SceneScreenC2S.CODEC.decode(buffer).kind());
            assertEquals(SceneScreenState.OTHER, PenumbraC2S.SceneScreenC2S.CODEC.decode(buffer).kind());
            assertEquals(SceneScreenState.NONE, PenumbraC2S.SceneScreenC2S.CODEC.decode(buffer).kind());
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }

    @Test
    void invalidScreenStatesCannotForgeAnEligibleClose() {
        RegistryFriendlyByteBuf buffer = buffer();
        try {
            buffer.writeVarInt(-1);
            buffer.writeVarInt(Integer.MAX_VALUE);
            assertEquals(SceneScreenState.OTHER, PenumbraC2S.SceneScreenC2S.CODEC.decode(buffer).kind());
            assertEquals(SceneScreenState.OTHER, PenumbraC2S.SceneScreenC2S.CODEC.decode(buffer).kind());
        } finally { buffer.release(); }
    }

    @Test
    void hushIsBoundedEvenWhenTheWireValueWasNotBuiltByOurConstructor() {
        RegistryFriendlyByteBuf buffer = buffer();
        try {
            buffer.writeVarInt(-20);
            buffer.writeVarInt(Integer.MAX_VALUE);
            PenumbraNetworking.SceneHushS2CPacket.CODEC.encode(buffer,
                    new PenumbraNetworking.SceneHushS2CPacket(24));
            assertEquals(0, PenumbraNetworking.SceneHushS2CPacket.CODEC.decode(buffer).ticks());
            assertEquals(60, PenumbraNetworking.SceneHushS2CPacket.CODEC.decode(buffer).ticks());
            assertEquals(24, PenumbraNetworking.SceneHushS2CPacket.CODEC.decode(buffer).ticks());
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    }
}
