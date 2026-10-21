package fr.penumbra.photo;

import fr.penumbra.entity.HostEntity;
import fr.penumbra.entity.absent.AbsentEntity;
import fr.penumbra.entity.fauxmob.FauxMobManager;
import fr.penumbra.entity.contrefait.ContrefaitEntity;
import fr.penumbra.entity.listener.ListenerEntity;
import fr.penumbra.entity.originel.OriginelEntity;
import fr.penumbra.entity.projectionist.ProjectionistEntity;
import fr.penumbra.entity.rampant.RampantEntity;
import fr.penumbra.entity.scuttler.ScuttlerEntity;
import fr.penumbra.entity.watcher.WatcherEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/** Détecte au flash les manifestations que le photographe peut réellement voir. */
public final class PhotoEvidenceDetector {

    /** Une apparition touchée par le flash, avec le verdict de cadrage exact. */
    public record AbsentFlashTarget(AbsentEntity absent, boolean photographed) {
    }

    private PhotoEvidenceDetector() {
    }

    public static List<PhotoEvidence> detect(ServerPlayer photographer) {
        return detect(photographer, 1.0f);
    }

    public static List<PhotoEvidence> detect(ServerPlayer photographer, float zoomFactor) {
        if (photographer == null || !(photographer.level() instanceof ServerLevel level)) {
            return List.of();
        }
        Vec3 eye = photographer.getEyePosition();
        Vec3 look = photographer.getViewVector(1.0f).normalize();
        AABB area = photographer.getBoundingBox().inflate(PhotoEvidenceRules.MAX_DISTANCE);
        List<Candidate> candidates = new ArrayList<>();

        for (Monster entity : level.getEntities(EntityTypeTest.forClass(Monster.class), area,
                monster -> evidenceFor(monster).discoverable())) {
            PhotoEvidence evidence = evidenceFor(entity);
            if (!entity.isAlive() || entity.isRemoved()
                    || !entity.broadcastToPlayer(photographer)
                    || entity.isInvisibleTo(photographer)) {
                continue;
            }
            Framing framing = bestFraming(level, photographer, entity, eye, look, zoomFactor);
            if (framing == null) {
                continue;
            }
            candidates.add(new Candidate(evidence,
                    PhotoEvidenceRules.score(evidence, framing.distance(), framing.alignment())));
        }

        // 0.15.0 : les Faux-Semblants sont des animaux vanilla possédés — hors du
        // filtre Monster ci-dessus. Le détecteur passe avant la dissolution du
        // flash : le cliché prouve que ce qui vient de disparaître n'était pas réel.
        for (Mob possessed : FauxMobManager.possessedMobsIn(level)) {
            if (!possessed.isAlive() || possessed.isRemoved()
                    || !possessed.broadcastToPlayer(photographer)
                    || possessed.isInvisibleTo(photographer)) {
                continue;
            }
            Framing framing = bestFraming(level, photographer, possessed, eye, look, zoomFactor);
            if (framing == null) {
                continue;
            }
            candidates.add(new Candidate(PhotoEvidence.FAUX_SEMBLANT,
                    PhotoEvidenceRules.score(PhotoEvidence.FAUX_SEMBLANT,
                            framing.distance(), framing.alignment())));
        }

        candidates.sort(Comparator.comparingDouble(Candidate::score).reversed());
        EnumSet<PhotoEvidence> unique = EnumSet.noneOf(PhotoEvidence.class);
        for (Candidate candidate : candidates) {
            unique.add(candidate.evidence());
            if (unique.size() >= PhotoEvidence.MAX_PER_PHOTO) {
                break;
            }
        }
        return candidates.stream()
                .map(Candidate::evidence)
                .filter(unique::contains)
                .distinct()
                .limit(PhotoEvidence.MAX_PER_PHOTO)
                .toList();
    }

    /**
     * Cibles serveur du flash. Une photographie exige le cône central strict
     * de l'émulsion ; une simple révélation accepte le halo frontal plus large
     * et plus court. Dans les deux cas, plusieurs rayons VISUAL interdisent de
     * traverser les murs tout en tolérant qu'un rebord cache les pieds.
     */
    public static List<AbsentFlashTarget> detectAbsentFlashTargets(
            ServerPlayer photographer) {
        return detectAbsentFlashTargets(photographer, 1.0f);
    }

    public static List<AbsentFlashTarget> detectAbsentFlashTargets(
            ServerPlayer photographer, float zoomFactor) {
        if (photographer == null || !(photographer.level() instanceof ServerLevel level)) {
            return List.of();
        }
        Vec3 eye = photographer.getEyePosition();
        Vec3 look = photographer.getViewVector(1.0f).normalize();
        AABB area = photographer.getBoundingBox().inflate(CameraFlashRules.REVEAL_DISTANCE);
        List<AbsentFlashTarget> targets = new ArrayList<>();
        for (AbsentEntity absent : level.getEntities(
                EntityTypeTest.forClass(AbsentEntity.class), area,
                entity -> entity.isAlive() && !entity.isRemoved())) {
            if (!absent.broadcastToPlayer(photographer)
                    || absent.isInvisibleTo(photographer)) {
                continue;
            }
            boolean exposed = isExposedByFlash(level, photographer, absent, eye, look);
            if (exposed) {
                boolean photographed = bestFraming(
                        level, photographer, absent, eye, look, zoomFactor) != null;
                targets.add(new AbsentFlashTarget(absent, photographed));
            }
        }
        targets.sort(Comparator.comparingDouble(
                target -> target.absent().distanceToSqr(photographer)));
        return List.copyOf(targets);
    }

    static PhotoEvidence evidenceFor(Entity entity) {
        if (entity instanceof OriginelEntity) {
            return PhotoEvidence.ORIGINEL;
        }
        if (entity instanceof AbsentEntity absent) {
            // Le faux compagnon est une perception, pas une preuve physique
            // partageable dans l'album.
            return absent.isFalseCompanion() ? PhotoEvidence.NONE : PhotoEvidence.ABSENT;
        }
        if (entity instanceof ProjectionistEntity) {
            return PhotoEvidence.PROJECTIONIST;
        }
        if (entity instanceof RampantEntity) {
            return PhotoEvidence.RAMPANT;
        }
        if (entity instanceof ContrefaitEntity) {
            return PhotoEvidence.CONTREFAIT;
        }
        if (entity instanceof WatcherEntity) {
            return PhotoEvidence.WATCHER;
        }
        if (entity instanceof HostEntity) {
            return PhotoEvidence.HOST;
        }
        if (entity instanceof ScuttlerEntity) {
            return PhotoEvidence.SCUTTLER;
        }
        if (entity instanceof ListenerEntity) {
            return PhotoEvidence.LISTENER;
        }
        return PhotoEvidence.NONE;
    }

    /**
     * Trois rayons évitent qu'un rebord cachant les pieds invalide un visage
     * parfaitement visible. VISUAL respecte l'occlusion rendue et laisse les
     * prises à travers les blocs réellement transparents se comporter comme l'image.
     */
    private static Framing bestFraming(ServerLevel level, ServerPlayer photographer,
                                       Mob entity, Vec3 eye, Vec3 look,
                                       float zoomFactor) {
        Vec3[] targets = sampleTargets(entity);
        Framing best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Vec3 target : targets) {
            Vec3 delta = target.subtract(eye);
            double distance = delta.length();
            double alignment = distance <= 1.0E-6
                    ? 1.0 : look.dot(delta.scale(1.0 / distance));
            // Un échantillon hors cadre ne peut devenir une preuve, même sans obstacle.
            // Évite le parcours des blocs pour les sujets derrière le photographe.
            if (!PhotoEvidenceRules.isFramed(distance, alignment, true, zoomFactor)) {
                continue;
            }
            boolean visible = level.clip(new ClipContext(eye, target,
                    ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, photographer))
                    .getType() == HitResult.Type.MISS;
            if (!PhotoEvidenceRules.isFramed(distance, alignment, visible, zoomFactor)) {
                continue;
            }
            double framingScore = alignment * 100.0 - distance;
            if (framingScore > bestScore) {
                bestScore = framingScore;
                best = new Framing(distance, alignment);
            }
        }
        return best;
    }

    private static boolean isExposedByFlash(ServerLevel level, ServerPlayer photographer,
                                            Mob entity, Vec3 eye, Vec3 look) {
        for (Vec3 target : sampleTargets(entity)) {
            Vec3 delta = target.subtract(eye);
            double distance = delta.length();
            double alignment = distance <= 1.0E-6
                    ? 1.0 : look.dot(delta.scale(1.0 / distance));
            if (!CameraFlashRules.isExposed(distance, alignment, true)) {
                continue;
            }
            boolean visible = level.clip(new ClipContext(eye, target,
                    ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, photographer))
                    .getType() == HitResult.Type.MISS;
            if (CameraFlashRules.isExposed(distance, alignment, visible)) {
                return true;
            }
        }
        return false;
    }

    private static Vec3[] sampleTargets(Mob entity) {
        AABB box = entity.getBoundingBox();
        Vec3 center = box.getCenter();
        return new Vec3[] {
                entity.getEyePosition(),
                center,
                new Vec3(center.x, box.minY + box.getYsize() * 0.22, center.z)
        };
    }

    private record Candidate(PhotoEvidence evidence, double score) {
    }

    private record Framing(double distance, double alignment) {
    }
}
