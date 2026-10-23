package fr.penumbra.journal;

import java.util.ArrayList;
import java.util.List;

/** Personal, server-supplied reading notes. This never changes story progression. */
public final class StoryGuide {
    private StoryGuide() { }

    /** Only the reader's current evidence is sent, never another player's history. */
    public record Snapshot(boolean storyEnabled, int stage, boolean won, boolean copiesEnabled,
                           int bedUses, boolean refugeSeen, int hostObservations,
                           boolean investigationEnabled, int volumesMask, boolean cassetteHeard) {
        public Snapshot(boolean storyEnabled, int stage, boolean won, boolean copiesEnabled,
                        int bedUses, boolean refugeSeen, int hostObservations) {
            this(storyEnabled, stage, won, copiesEnabled, bedUses, refugeSeen, hostObservations,
                    false, 0, false);
        }

        public Snapshot {
            stage = Math.clamp(stage, 0, 6);
            bedUses = Math.clamp(bedUses, 0, 2);
            hostObservations = Math.clamp(hostObservations, 0, 4);
            volumesMask = Math.clamp(volumesMask, 0, 15);
        }
    }

    public record Line(String key, List<Integer> arguments) {
        public Line {
            arguments = List.copyOf(arguments);
        }

        private Line(String suffix, Integer... arguments) {
            this("journal.penumbra.guide." + suffix, List.of(arguments));
        }
    }

    public static String subtitleKey(Snapshot state) {
        if (!state.storyEnabled()) return "journal.penumbra.guide.disabled.title";
        if (state.won()) return "journal.penumbra.guide.won.title";
        return "journal.penumbra.guide.stage" + state.stage() + ".title";
    }

    /** Future objectives remain absent until that player reaches their stage. */
    public static List<Line> lines(Snapshot state) {
        List<Line> result = new ArrayList<>();
        if (!state.storyEnabled()) {
            result.add(new Line("disabled.body"));
            result.add(new Line("disabled.notes"));
        } else if (state.won()) {
            result.add(new Line("won.body"));
            result.add(new Line("won.calm"));
            result.add(new Line("won.continue"));
        } else if (state.investigationEnabled() && state.stage() < 6) {
            result.add(new Line("investigation.count", Integer.bitCount(state.volumesMask())));
            int missing = InvestigationRules.firstMissing(state.volumesMask());
            if (missing >= 0) result.add(new Line("investigation.book" + (missing + 1)));
            else result.add(new Line(state.cassetteHeard() ? "investigation.ready" : "investigation.tape"));
            result.add(new Line("investigation.compass"));
            result.add(new Line("investigation.recovery"));
            result.add(new Line("investigation.refuge"));
        } else {
            result.add(new Line("stage" + state.stage() + ".body"));
            switch (state.stage()) {
                case 0 -> {
                    result.add(new Line("stage0.explore"));
                    result.add(new Line("compass"));
                }
                case 1 -> result.add(new Line("stage1.observe"));
                case 2 -> {
                    result.add(new Line("stage2.beds", state.bedUses()));
                    result.add(new Line(state.refugeSeen()
                            ? "stage2.visitor_seen" : "stage2.visitor_missing"));
                }
                case 3 -> {
                    result.add(new Line(state.copiesEnabled()
                            ? "stage3.copy" : "stage3.no_copies"));
                    result.add(new Line("compass"));
                }
                case 4 -> result.add(new Line("stage4.observed", state.hostObservations()));
                case 5 -> {
                    result.add(new Line("stage5.sigil"));
                    result.add(new Line("camera"));
                }
                case 6 -> {
                    result.add(new Line("stage6.focus"));
                    result.add(new Line("stage6.flash"));
                    result.add(new Line("stage6.retry"));
                }
                default -> throw new IllegalStateException("Unbounded story stage");
            }
        }
        if (state.cassetteHeard()) {
            for (int i = 1; i <= 5; i++) result.add(new Line("journal.penumbra.tape.line" + i, List.of()));
        }
        result.add(new Line("personal"));
        return List.copyOf(result);
    }
}
