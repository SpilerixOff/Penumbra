package fr.penumbra.director;

import java.util.Locale;

/** Machine d'état pure des arcs de peur persistants (3 à 7 minutes). */
public final class HorrorArcEngine {

    private static final float ARC_START_TENSION = TensionState.UNEASE.threshold;

    public record Context(boolean underground, boolean nearRefuge, boolean cameraActive) {
    }

    public record Decision(
            DirectorMemory memory,
            boolean beatDue,
            boolean hardManifestationDue,
            boolean falseCalm,
            boolean breakDue,
            boolean phaseChanged) {
    }

    private HorrorArcEngine() {
    }

    /** Avance l'arc jusqu'à l'instant {@code now}, sans jamais produire une rafale de beats. */
    public static Decision tick(DirectorMemory input, long now, float tension,
                                Context context, long seed) {
        DirectorMemory memory = input.rebaseClock(now);
        DirectorMemory.ArcState arc = memory.arc();
        boolean phaseChanged = false;
        boolean breakDue = false;

        // Les conséquences longues sont des descripteurs persistants : au retour
        // d'un joueur hors-ligne, une seule est consommée dans ce cycle.
        for (DirectorMemory.PendingConsequence pending : memory.pending()) {
            if (pending.expiresTick() < now) {
                memory = memory.withPending(memory.pending().stream()
                        .filter(value -> value != pending).toList());
                continue;
            }
            if (pending.dueTick() <= now && "false_calm".equals(pending.type())) {
                memory = memory.withPending(memory.pending().stream()
                        .filter(value -> value != pending).toList());
                memory = forceFalseCalm(memory, now, 900L);
                return decision(memory, now, false, true);
            }
        }

        if (arc.phase() == DirectorMemory.ArcPhase.DORMANT) {
            if (tension >= ARC_START_TENSION
                    && !memory.pacing().falseCalmBreakPending()) {
                arc = beginArc(now, chooseTheme(context, seed), seed);
                memory = memory.withArc(arc);
                phaseChanged = true;
            }
            return decision(memory, now, breakDue, phaseChanged);
        }

        // Une grosse avance d'horloge peut traverser plusieurs phases. La boucle
        // est strictement bornée et ne rend qu'un seul beat à l'appelant.
        for (int transitions = 0; transitions < 6 && now >= arc.phaseEndsTick(); transitions++) {
            long phaseStart = arc.phaseEndsTick();
            switch (arc.phase()) {
                case FORESHADOW -> arc = phase(arc, DirectorMemory.ArcPhase.BUILD,
                        phaseStart, duration(seed, 2, 900, 1800),
                        beatDelay(seed, 2, 260, 500), false);
                case BUILD -> arc = phase(arc, DirectorMemory.ArcPhase.MANIFEST,
                        phaseStart, duration(seed, 3, 600, 1200),
                        beatDelay(seed, 3, 160, 300), false);
                case MANIFEST -> arc = phase(arc, DirectorMemory.ArcPhase.AFTERMATH,
                        phaseStart, duration(seed, 4, 900, 1500),
                        beatDelay(seed, 4, 450, 700), arc.manifestationSeen());
                case AFTERMATH -> {
                    arc = phase(arc, DirectorMemory.ArcPhase.FALSE_CALM,
                            phaseStart, duration(seed, 5, 900, 1500),
                            Long.MAX_VALUE - phaseStart, arc.manifestationSeen());
                    memory = memory.withFalseCalmBreakPending(true, now);
                }
                case FALSE_CALM -> {
                    breakDue = memory.pacing().falseCalmBreakPending();
                    arc = DirectorMemory.ArcState.dormant(phaseStart);
                }
                case DORMANT -> {
                    // Traité avant la boucle.
                }
            }
            memory = memory.withArc(arc);
            phaseChanged = true;
            if (arc.phase() == DirectorMemory.ArcPhase.DORMANT) {
                break;
            }
        }
        return decision(memory.touchClock(now), now, breakDue, phaseChanged);
    }

    public static DirectorMemory afterSuccessfulBeat(
            DirectorMemory memory, long now, boolean manifestation, long seed) {
        DirectorMemory.ArcState arc = memory.arc();
        if (arc.phase() == DirectorMemory.ArcPhase.DORMANT
                || arc.phase() == DirectorMemory.ArcPhase.FALSE_CALM) {
            return memory;
        }
        long delay = switch (arc.phase()) {
            case FORESHADOW -> beatDelay(seed + arc.beatCount(), 11, 400, 700);
            case BUILD -> beatDelay(seed + arc.beatCount(), 12, 260, 500);
            case MANIFEST -> beatDelay(seed + arc.beatCount(), 13, 160, 300);
            case AFTERMATH -> beatDelay(seed + arc.beatCount(), 14, 500, 800);
            case DORMANT, FALSE_CALM -> Long.MAX_VALUE - now;
        };
        DirectorMemory.ArcState value = new DirectorMemory.ArcState(
                arc.theme(), arc.phase(), arc.arcStartedTick(), arc.phaseStartedTick(),
                arc.phaseEndsTick(), safeDeadline(now, delay), arc.hardManifestationTick(),
                arc.beatCount() + 1, arc.manifestationSeen() || manifestation);
        return memory.withArc(value);
    }

    public static DirectorMemory forceStart(DirectorMemory memory, long now,
                                            DirectorMemory.ArcTheme theme, long seed) {
        return memory.rebaseClock(now).withArc(beginArc(now,
                theme == null || theme == DirectorMemory.ArcTheme.NONE
                        ? DirectorMemory.ArcTheme.STALKER : theme, seed));
    }

    public static DirectorMemory clear(DirectorMemory memory, long now) {
        return new DirectorMemory(DirectorMemory.ArcState.dormant(now),
                new DirectorMemory.PacingState(
                        memory.pacing().lastDirectedTick(), memory.pacing().lastPerceptibleTick(),
                        memory.pacing().lastManifestationTick(), 0, now,
                        memory.pacing().eventCooldowns(), memory.pacing().recentFamilies(), false),
                memory.pending());
    }

    public static DirectorMemory forceFalseCalm(
            DirectorMemory memory, long now, long durationTicks) {
        DirectorMemory.ArcState previous = memory.arc();
        DirectorMemory.ArcState arc = new DirectorMemory.ArcState(
                previous.theme(), DirectorMemory.ArcPhase.FALSE_CALM,
                previous.arcStartedTick(), now, safeDeadline(now, Math.max(20L, durationTicks)),
                Long.MAX_VALUE, previous.hardManifestationTick(),
                previous.beatCount(), previous.manifestationSeen());
        return memory.withArc(arc).withFalseCalmBreakPending(true, now);
    }

    public static DirectorMemory consumeBreak(DirectorMemory memory, long now) {
        return memory.withFalseCalmBreakPending(false, now);
    }

    /** Multiplicateur du tempo propre à chaque phase. */
    public static float gapMultiplier(DirectorMemory.ArcPhase phase) {
        return switch (phase) {
            case DORMANT -> 1.0f;
            case FORESHADOW -> 1.10f;
            case BUILD -> 0.85f;
            case MANIFEST -> 0.55f;
            case AFTERMATH -> 1.35f;
            case FALSE_CALM -> Float.POSITIVE_INFINITY;
        };
    }

    /** Poids contextuel d'une famille d'événement dans le thème actif. */
    public static float themeMultiplier(DirectorMemory.ArcTheme theme, String family) {
        if (theme == null || family == null) {
            return 1.0f;
        }
        String normalized = family.toLowerCase(Locale.ROOT);
        return switch (theme) {
            case CAVE -> normalized.equals("cave") || normalized.equals("creature") ? 2.0f : 0.85f;
            case REFUGE -> normalized.equals("refuge") ? 2.2f : 0.9f;
            case STALKER -> normalized.equals("sighting") || normalized.equals("chase") ? 1.8f : 0.9f;
            case MIMIC -> normalized.equals("mimic") ? 2.2f : 0.9f;
            case PHOTOGRAPHIC -> normalized.equals("photo") || normalized.equals("sighting") ? 1.9f : 0.9f;
            case NONE -> 1.0f;
        };
    }

    private static Decision decision(DirectorMemory memory, long now,
                                     boolean breakDue, boolean phaseChanged) {
        DirectorMemory.ArcState arc = memory.arc();
        boolean falseCalm = arc.phase() == DirectorMemory.ArcPhase.FALSE_CALM;
        boolean beatDue = !falseCalm
                && arc.phase() != DirectorMemory.ArcPhase.DORMANT
                && now >= arc.nextBeatTick();
        boolean hardDue = arc.phase() == DirectorMemory.ArcPhase.MANIFEST
                && !arc.manifestationSeen() && now >= arc.hardManifestationTick();
        return new Decision(memory, beatDue, hardDue, falseCalm, breakDue, phaseChanged);
    }

    private static DirectorMemory.ArcState beginArc(
            long now, DirectorMemory.ArcTheme theme, long seed) {
        long duration = duration(seed, 1, 1200, 2400);
        return new DirectorMemory.ArcState(theme, DirectorMemory.ArcPhase.FORESHADOW,
                now, now, safeDeadline(now, duration),
                safeDeadline(now, beatDelay(seed, 1, 400, 700)),
                safeDeadline(now, duration + 900L + 200L), 0, false);
    }

    private static DirectorMemory.ArcState phase(
            DirectorMemory.ArcState previous, DirectorMemory.ArcPhase phase,
            long start, long duration, long nextBeatDelay, boolean manifestationSeen) {
        long hard = phase == DirectorMemory.ArcPhase.MANIFEST
                ? safeDeadline(start, Math.min(duration - 1L,
                beatDelay(previous.arcStartedTick(), 31, 200, 600)))
                : previous.hardManifestationTick();
        return new DirectorMemory.ArcState(previous.theme(), phase,
                previous.arcStartedTick(), start, safeDeadline(start, duration),
                safeDeadline(start, nextBeatDelay), hard, previous.beatCount(), manifestationSeen);
    }

    private static DirectorMemory.ArcTheme chooseTheme(Context context, long seed) {
        if (context != null) {
            if (context.underground()) {
                return DirectorMemory.ArcTheme.CAVE;
            }
            if (context.nearRefuge()) {
                return DirectorMemory.ArcTheme.REFUGE;
            }
            if (context.cameraActive()) {
                return DirectorMemory.ArcTheme.PHOTOGRAPHIC;
            }
        }
        return (mix(seed) & 1L) == 0L
                ? DirectorMemory.ArcTheme.STALKER : DirectorMemory.ArcTheme.MIMIC;
    }

    private static long duration(long seed, int salt, int minimum, int maximum) {
        return minimum + Math.floorMod(mix(seed + salt * 0x9E3779B97F4A7C15L),
                (long) maximum - minimum + 1L);
    }

    private static long beatDelay(long seed, int salt, int minimum, int maximum) {
        return duration(seed, salt, minimum, maximum);
    }

    private static long mix(long value) {
        value ^= value >>> 30;
        value *= 0xbf58476d1ce4e5b9L;
        value ^= value >>> 27;
        value *= 0x94d049bb133111ebL;
        return value ^ value >>> 31;
    }

    private static long safeDeadline(long now, long delay) {
        if (delay >= Long.MAX_VALUE - Math.max(0L, now)) {
            return Long.MAX_VALUE;
        }
        return now + Math.max(0L, delay);
    }
}
