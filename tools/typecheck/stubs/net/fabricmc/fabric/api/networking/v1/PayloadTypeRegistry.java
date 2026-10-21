package net.fabricmc.fabric.api.networking.v1;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
public interface PayloadTypeRegistry<B> {
    static PayloadTypeRegistry<RegistryFriendlyByteBuf> playC2S() { return null; }
    static PayloadTypeRegistry<RegistryFriendlyByteBuf> playS2C() { return null; }
    static PayloadTypeRegistry<RegistryFriendlyByteBuf> serverboundPlay() { return null; }
    static PayloadTypeRegistry<RegistryFriendlyByteBuf> clientboundPlay() { return null; }
    <T extends CustomPacketPayload> void register(CustomPacketPayload.Type<T> id, StreamCodec<? super B, T> codec);
}
