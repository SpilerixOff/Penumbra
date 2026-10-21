package fr.penumbra.registry;

import fr.penumbra.PenumbraMod;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Registre des particules de Penumbra. Phase 1 : la cendre de l'Hôte (ash_mote).
 */
public final class PenumbraParticles {

    // SPEC §4.4 annonce « DefaultParticleType » : c'est le nom Yarn. En mappings
    // officiels Mojang la classe s'appelle SimpleParticleType — c'est aussi le type
    // attendu par SPEC §4.9 (ParticleFactory<SimpleParticleType>).
    /** Motte de cendre grise qui monte lentement (fabrique côté client). */
    public static final SimpleParticleType ASH_MOTE = Registry.register(
            BuiltInRegistries.PARTICLE_TYPE, PenumbraMod.id("ash_mote"), FabricParticleTypes.simple());

    private PenumbraParticles() {
    }

    /** Force le chargement statique de la classe (aucune autre action requise). */
    public static void register() {
        // Corps vide volontaire : l'enregistrement a lieu à l'initialisation statique.
    }
}
