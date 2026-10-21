package fr.penumbra.journal;

import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

/** Route choices use known coordinates only: selecting a route never loads terrain. */
public final class SurveyCompassRoute {
    public enum Mode {
        CAMP("camp"), UNEXPLORED("unexplored"), NEAREST("nearest");

        private final String id;

        Mode(String id) { this.id = id; }

        public String id() { return id; }

        public Mode next() { return values()[(ordinal() + 1) % values().length]; }

        public static Mode fromIndex(int index) {
            return index >= 0 && index < values().length ? values()[index] : CAMP;
        }
    }

    public record Site(String id, int x, int y, int z, boolean discovered) {
        public double horizontalDistanceSquared(double playerX, double playerZ) {
            double dx = x + 0.5 - playerX;
            double dz = z + 0.5 - playerZ;
            return dx * dx + dz * dz;
        }
    }

    private SurveyCompassRoute() { }

    public static Optional<Site> select(Mode mode, Collection<Site> sites,
                                        double playerX, double playerZ) {
        return sites.stream()
                .filter(site -> switch (mode) {
                    case CAMP -> site.id().equals("signal_pyre");
                    case UNEXPLORED -> !site.discovered();
                    case NEAREST -> true;
                })
                .min(Comparator.comparingDouble(site ->
                        site.horizontalDistanceSquared(playerX, playerZ)));
    }
}
