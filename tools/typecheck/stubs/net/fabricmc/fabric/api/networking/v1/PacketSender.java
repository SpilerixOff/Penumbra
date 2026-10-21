package net.fabricmc.fabric.api.networking.v1;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
public interface PacketSender { void sendPacket(CustomPacketPayload payload); }
