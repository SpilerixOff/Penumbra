package fr.penumbra.director;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Mémoire persistante du directeur 0.12. Les sous-structures sont immuables :
 * une sauvegarde ne peut jamais observer un état à moitié muté.
 */
public final class DirectorMemory {

    public static final int MAX_EVENT_COOLDOWNS = 96;
    public static final int MAX_RECENT_FAMILIES = 6;
    public static final int MAX_PENDING_CONSEQUENCES = 8;

    public enum ArcTheme {
        NONE, CAVE, REFUGE, STALKER, MIMIC, PHOTOGRAPHIC
    }

    public enum ArcPhase {
        DORMANT, FORESHADOW, BUILD, MANIFEST, AFTERMATH, FALSE_CALM
    }

    private static final Codec<ArcTheme> THEME_CODEC = enumCodec(ArcTheme.class, ArcTheme.NONE);
    private static final Codec<ArcPhase> PHASE_CODEC = enumCodec(ArcPhase.class, ArcPhase.DORMANT);

    public record ArcState(
            ArcTheme theme,
            ArcPhase phase,
            long arcStartedTick,
            long phaseStartedTick,
            long phaseEndsTick,
            long nextBeatTick,
            long hardManifestationTick,
            int beatCount,
            boolean manifestationSeen) {

        public static final Codec<ArcState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                THEME_CODEC.optionalFieldOf("theme", ArcTheme.NONE).forGetter(ArcState::theme),
                PHASE_CODEC.optionalFieldOf("phase", ArcPhase.DORMANT).forGetter(ArcState::phase),
                Codec.LONG.optionalFieldOf("arcStarted", 0L).forGetter(ArcState::arcStartedTick),
                Codec.LONG.optionalFieldOf("phaseStarted", 0L).forGetter(ArcState::phaseStartedTick),
                Codec.LONG.optionalFieldOf("phaseEnds", 0L).forGetter(ArcState::phaseEndsTick),
                Codec.LONG.optionalFieldOf("nextBeat", 0L).forGetter(ArcState::nextBeatTick),
                Codec.LONG.optionalFieldOf("hardManifestation", 0L)
                        .forGetter(ArcState::hardManifestationTick),
                Codec.INT.optionalFieldOf("beats", 0).forGetter(ArcState::beatCount),
                Codec.BOOL.optionalFieldOf("manifestationSeen", false)
                        .forGetter(ArcState::manifestationSeen)
        ).apply(instance, ArcState::new));

        public ArcState {
            theme = theme == null ? ArcTheme.NONE : theme;
            phase = phase == null ? ArcPhase.DORMANT : phase;
            beatCount = Math.max(0, Math.min(10_000, beatCount));
        }

        public static ArcState dormant(long now) {
            return new ArcState(ArcTheme.NONE, ArcPhase.DORMANT,
                    now, now, now, now, now, 0, false);
        }

        ArcState shifted(long delta) {
            return new ArcState(theme, phase,
                    safeAdd(arcStartedTick, delta), safeAdd(phaseStartedTick, delta),
                    safeAdd(phaseEndsTick, delta), safeAdd(nextBeatTick, delta),
                    safeAdd(hardManifestationTick, delta), beatCount, manifestationSeen);
        }
    }

    public record PacingState(
            long lastDirectedTick,
            long lastPerceptibleTick,
            long lastManifestationTick,
            int audioStreak,
            long lastClockTick,
            Map<String, Long> eventCooldowns,
            List<String> recentFamilies,
            boolean falseCalmBreakPending) {

        public static final Codec<PacingState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.optionalFieldOf("lastDirected", 0L).forGetter(PacingState::lastDirectedTick),
                Codec.LONG.optionalFieldOf("lastPerceptible", 0L).forGetter(PacingState::lastPerceptibleTick),
                Codec.LONG.optionalFieldOf("lastManifestation", -1L)
                        .forGetter(PacingState::lastManifestationTick),
                Codec.INT.optionalFieldOf("audioStreak", 0).forGetter(PacingState::audioStreak),
                Codec.LONG.optionalFieldOf("lastClock", 0L).forGetter(PacingState::lastClockTick),
                Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("eventCooldowns", Map.of())
                        .forGetter(PacingState::eventCooldowns),
                Codec.STRING.listOf().optionalFieldOf("recentFamilies", List.of())
                        .forGetter(PacingState::recentFamilies),
                Codec.BOOL.optionalFieldOf("falseCalmBreakPending", false)
                        .forGetter(PacingState::falseCalmBreakPending)
        ).apply(instance, PacingState::new));

        public PacingState {
            audioStreak = Math.max(0, Math.min(16, audioStreak));
            eventCooldowns = sanitizeCooldowns(eventCooldowns);
            recentFamilies = sanitizeIds(recentFamilies, MAX_RECENT_FAMILIES);
        }

        public static PacingState fresh(long now, long lastDirected) {
            long directed = lastDirected > 0L ? Math.min(lastDirected, now) : now;
            return new PacingState(directed, directed, now, 0, now,
                    Map.of(), List.of(), false);
        }

        PacingState shifted(long delta) {
            Map<String, Long> shiftedCooldowns = new LinkedHashMap<>();
            for (Map.Entry<String, Long> entry : eventCooldowns.entrySet()) {
                shiftedCooldowns.put(entry.getKey(), safeAdd(entry.getValue(), delta));
            }
            return new PacingState(
                    safeAdd(lastDirectedTick, delta), safeAdd(lastPerceptibleTick, delta),
                    lastManifestationTick < 0L ? -1L : safeAdd(lastManifestationTick, delta),
                    audioStreak, safeAdd(lastClockTick, delta), shiftedCooldowns,
                    recentFamilies, falseCalmBreakPending);
        }
    }

    public record PendingConsequence(
            String type, long dueTick, long expiresTick, String source, int attempts) {

        public static final Codec<PendingConsequence> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.optionalFieldOf("type", "").forGetter(PendingConsequence::type),
                Codec.LONG.optionalFieldOf("due", 0L).forGetter(PendingConsequence::dueTick),
                Codec.LONG.optionalFieldOf("expires", 0L).forGetter(PendingConsequence::expiresTick),
                Codec.STRING.optionalFieldOf("source", "").forGetter(PendingConsequence::source),
                Codec.INT.optionalFieldOf("attempts", 0).forGetter(PendingConsequence::attempts)
        ).apply(instance, PendingConsequence::new));

        public PendingConsequence {
            type = sanitizeId(type);
            source = sanitizeId(source);
            attempts = Math.max(0, Math.min(8, attempts));
        }

        PendingConsequence shifted(long delta) {
            return new PendingConsequence(type, safeAdd(dueTick, delta),
                    safeAdd(expiresTick, delta), source, attempts);
        }

        boolean valid() {
            return !type.isEmpty() && expiresTick >= dueTick;
        }
    }

    public static final Codec<DirectorMemory> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ArcState.CODEC.optionalFieldOf("arc", ArcState.dormant(0L)).forGetter(DirectorMemory::arc),
            PacingState.CODEC.optionalFieldOf("pacing", PacingState.fresh(0L, 0L))
                    .forGetter(DirectorMemory::pacing),
            PendingConsequence.CODEC.listOf().optionalFieldOf("pending", List.of())
                    .forGetter(DirectorMemory::pending)
    ).apply(instance, DirectorMemory::new));

    private final ArcState arc;
    private final PacingState pacing;
    private final List<PendingConsequence> pending;

    public DirectorMemory(ArcState arc, PacingState pacing, List<PendingConsequence> pending) {
        this.arc = arc == null ? ArcState.dormant(0L) : arc;
        this.pacing = pacing == null ? PacingState.fresh(0L, 0L) : pacing;
        List<PendingConsequence> safe = new ArrayList<>();
        if (pending != null) {
            for (PendingConsequence consequence : pending) {
                if (safe.size() >= MAX_PENDING_CONSEQUENCES) {
                    break;
                }
                if (consequence != null && consequence.valid()) {
                    safe.add(consequence);
                }
            }
        }
        this.pending = List.copyOf(safe);
    }

    public static DirectorMemory fresh(long now, long lastDirected) {
        return new DirectorMemory(ArcState.dormant(now),
                PacingState.fresh(now, lastDirected), List.of());
    }

    public ArcState arc() {
        return arc;
    }

    public PacingState pacing() {
        return pacing;
    }

    public List<PendingConsequence> pending() {
        return pending;
    }

    public DirectorMemory withArc(ArcState value) {
        return new DirectorMemory(value, pacing, pending);
    }

    public DirectorMemory withPacing(PacingState value) {
        return new DirectorMemory(arc, value, pending);
    }

    public DirectorMemory withPending(List<PendingConsequence> value) {
        return new DirectorMemory(arc, pacing, value);
    }

    /** Ré-ancre tous les timestamps lorsque /time ou une restauration recule l'horloge. */
    public DirectorMemory rebaseClock(long now) {
        if (pacing.lastClockTick() <= 0L || now >= pacing.lastClockTick()) {
            return touchClock(now);
        }
        long delta = now - pacing.lastClockTick();
        List<PendingConsequence> shifted = pending.stream()
                .map(value -> value.shifted(delta)).toList();
        DirectorMemory rebased = new DirectorMemory(arc.shifted(delta), pacing.shifted(delta), shifted);
        return rebased.touchClock(now);
    }

    public DirectorMemory touchClock(long now) {
        PacingState value = new PacingState(
                pacing.lastDirectedTick(), pacing.lastPerceptibleTick(),
                pacing.lastManifestationTick(), pacing.audioStreak(), now,
                pacing.eventCooldowns(), pacing.recentFamilies(),
                pacing.falseCalmBreakPending());
        return withPacing(value);
    }

    public DirectorMemory recordDirected(long now, String eventId, String family,
                                         boolean audio, boolean manifestation) {
        Map<String, Long> cooldowns = new HashMap<>(pacing.eventCooldowns());
        String safeEvent = sanitizeId(eventId);
        if (!safeEvent.isEmpty()) {
            if (!cooldowns.containsKey(safeEvent) && cooldowns.size() >= MAX_EVENT_COOLDOWNS) {
                cooldowns.entrySet().stream().min(Comparator.comparingLong(Map.Entry::getValue))
                        .map(Map.Entry::getKey).ifPresent(cooldowns::remove);
            }
            cooldowns.put(safeEvent, now);
        }
        List<String> families = new ArrayList<>(pacing.recentFamilies());
        String safeFamily = sanitizeId(family);
        if (!safeFamily.isEmpty()) {
            families.add(safeFamily);
            while (families.size() > MAX_RECENT_FAMILIES) {
                families.removeFirst();
            }
        }
        PacingState value = new PacingState(now, now,
                manifestation ? now : pacing.lastManifestationTick(),
                audio ? Math.min(16, pacing.audioStreak() + 1) : 0,
                now, cooldowns, families, pacing.falseCalmBreakPending());
        ArcState arcValue = manifestation && !arc.manifestationSeen()
                ? new ArcState(arc.theme(), arc.phase(), arc.arcStartedTick(),
                arc.phaseStartedTick(), arc.phaseEndsTick(), arc.nextBeatTick(),
                arc.hardManifestationTick(), arc.beatCount(), true)
                : arc;
        return new DirectorMemory(arcValue, value, pending);
    }

    public DirectorMemory recordIncidental(long now) {
        return withPacing(new PacingState(
                pacing.lastDirectedTick(), now, pacing.lastManifestationTick(),
                pacing.audioStreak(), now, pacing.eventCooldowns(),
                pacing.recentFamilies(), pacing.falseCalmBreakPending()));
    }

    public DirectorMemory recordManifestation(long now) {
        PacingState value = new PacingState(
                pacing.lastDirectedTick(), now, now, 0, now,
                pacing.eventCooldowns(), pacing.recentFamilies(),
                pacing.falseCalmBreakPending());
        ArcState arcValue = new ArcState(arc.theme(), arc.phase(), arc.arcStartedTick(),
                arc.phaseStartedTick(), arc.phaseEndsTick(), arc.nextBeatTick(),
                arc.hardManifestationTick(), arc.beatCount(), true);
        return new DirectorMemory(arcValue, value, pending);
    }

    public DirectorMemory withFalseCalmBreakPending(boolean value, long now) {
        return withPacing(new PacingState(
                pacing.lastDirectedTick(), pacing.lastPerceptibleTick(),
                pacing.lastManifestationTick(), pacing.audioStreak(), now,
                pacing.eventCooldowns(), pacing.recentFamilies(), value));
    }

    /** Fixe l'ancrage de dette sans prétendre qu'une apparition vient de réussir. */
    public DirectorMemory withLastManifestation(long value, long now) {
        return withPacing(new PacingState(
                pacing.lastDirectedTick(), pacing.lastPerceptibleTick(), value,
                pacing.audioStreak(), now, pacing.eventCooldowns(),
                pacing.recentFamilies(), pacing.falseCalmBreakPending()));
    }

    /** Ajoute ou remplace une conséquence longue par type, dans une liste bornée. */
    public DirectorMemory scheduleConsequence(PendingConsequence value) {
        if (value == null || !value.valid()) {
            return this;
        }
        List<PendingConsequence> values = new ArrayList<>();
        for (PendingConsequence current : pending) {
            if (!current.type().equals(value.type())) {
                values.add(current);
            }
        }
        values.add(value);
        while (values.size() > MAX_PENDING_CONSEQUENCES) {
            values.removeFirst();
        }
        return withPending(values);
    }

    private static Map<String, Long> sanitizeCooldowns(Map<String, Long> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return source.entrySet().stream()
                .filter(entry -> !sanitizeId(entry.getKey()).isEmpty() && entry.getValue() != null)
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(MAX_EVENT_COOLDOWNS)
                .collect(LinkedHashMap::new,
                        (map, entry) -> map.put(sanitizeId(entry.getKey()), entry.getValue()),
                        LinkedHashMap::putAll);
    }

    private static List<String> sanitizeIds(List<String> source, int limit) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        int first = Math.max(0, source.size() - limit);
        for (int index = first; index < source.size(); index++) {
            String id = sanitizeId(source.get(index));
            if (!id.isEmpty()) {
                result.add(id);
            }
        }
        return List.copyOf(result);
    }

    private static String sanitizeId(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.strip();
        return value.length() <= 64 ? value : value.substring(0, 64);
    }

    private static long safeAdd(long value, long delta) {
        try {
            return Math.addExact(value, delta);
        } catch (ArithmeticException ignored) {
            return delta >= 0L ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }

    private static <E extends Enum<E>> Codec<E> enumCodec(Class<E> type, E fallback) {
        return Codec.STRING.xmap(raw -> {
            if (raw == null) {
                return fallback;
            }
            try {
                return Enum.valueOf(type, raw.strip().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return fallback;
            }
        }, value -> value.name().toLowerCase(Locale.ROOT));
    }
}
