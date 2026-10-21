package fr.penumbra.client.particle;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

/**
 * Particule « cendre » : grise, monte lentement, vie 40–80 ticks, gravité légèrement négative (SPEC §4.9).
 * Classe parente vérifiée en 26.1.2 : {@link SingleQuadParticle} (constructeur avec
 * {@code TextureAtlasSprite}, méthode abstraite {@code getLayer()}).
 */
@Environment(EnvType.CLIENT)
public class AshMoteParticle extends SingleQuadParticle {

    private AshMoteParticle(ClientLevel level, double x, double y, double z,
                            double xa, double ya, double za, SpriteSet sprites) {
        super(level, x, y, z, xa, ya, za, sprites.first());
        this.lifetime = 40 + this.random.nextInt(41);          // 40–80 ticks
        this.gravity = -0.015f;                                // gravité négative : la cendre monte
        this.quadSize = 0.08f + this.random.nextFloat() * 0.05f;
        float gray = 0.68f + this.random.nextFloat() * 0.14f;  // gris pâle, légère variation
        this.rCol = gray;
        this.gCol = gray;
        this.bCol = gray;
        this.alpha = 0.85f;
        this.yd = Math.abs(ya) * 0.2 + 0.008;                  // légère poussée initiale vers le haut
        this.xd = xa * 0.15;
        this.zd = za * 0.15;
    }

    @Override
    public void tick() {
        super.tick();
        float lifeRatio = (float) this.age / (float) this.lifetime;
        this.alpha = 0.85f * (1.0f - lifeRatio);               // fondu de fin de vie
        // Dérive lente, comme portée par un courant d'air chaud.
        this.xd += (this.random.nextDouble() - 0.5) * 0.0008;
        this.zd += (this.random.nextDouble() - 0.5) * 0.0008;
    }

    // SingleQuadParticle#getLayer() (vérifié en 26.1.2 : méthode abstraite protégée).
    @Override
    public SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.TRANSLUCENT;
    }

    /**
     * Fabrique enregistrée via {@code ParticleProviderRegistry} (SPEC §4.2).
     * Mappings officiels : {@code ParticleProvider} (nom Yarn historique : {@code ParticleFactory}) ;
     * {@code SimpleParticleType} (nom Yarn : {@code DefaultParticleType}).
     */
    @Environment(EnvType.CLIENT)
    public static class Factory implements ParticleProvider<SimpleParticleType> {

        private final SpriteSet sprites;

        public Factory(SpriteSet sprites) {
            this.sprites = sprites;
        }

        // Vérifié en 26.1.2 : ParticleProvider.createParticle reçoit un RandomSource final.
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double xSpeed, double ySpeed, double zSpeed,
                                       RandomSource random) {
            return new AshMoteParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, this.sprites);
        }
    }
}
