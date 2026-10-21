package net.fabricmc.fabric.api.client.particle.v1;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
public interface ParticleProviderRegistry {
    static ParticleProviderRegistry getInstance() { return null; }
    <T extends ParticleOptions> void register(ParticleType<T> type, ParticleProvider<T> provider);
    <T extends ParticleOptions> void register(ParticleType<T> type, PendingParticleFactory<T> factory);
    interface PendingParticleFactory<T extends ParticleOptions> {
        ParticleProvider<T> create(SpriteSet spriteSet);
    }
}
