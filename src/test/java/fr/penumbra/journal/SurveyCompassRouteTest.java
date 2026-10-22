package fr.penumbra.journal;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static fr.penumbra.journal.SurveyCompassRoute.*;

class SurveyCompassRouteTest {
    private final Site camp = new Site("signal_pyre", 1500, 64, 1500, false);
    private final Site visited = new Site("observation_post", 10, 90, 10, true);
    private final Site unknown = new Site("ash_sanctum", 200, 130, 200, false);

    @Test
    void campRouteDoesNotGetDistractedByNearerRuins() {
        assertEquals(camp, select(Mode.CAMP, List.of(visited, unknown, camp), 0, 0).orElseThrow());
    }

    @Test
    void explorationSkipsAlreadyVisitedLocations() {
        assertEquals(unknown, select(Mode.UNEXPLORED, List.of(visited, unknown, camp), 0, 0).orElseThrow());
    }

    @Test
    void noUnexploredSitesDoesNotPretendVisitedSiteIsNew() {
        assertTrue(select(Mode.UNEXPLORED, List.of(visited), 0, 0).isEmpty());
        assertTrue(select(Mode.UNEXPLORED, List.of(), 0, 0).isEmpty());
    }

    @Test
    void nearestCanGuideBackToKnownShelterRegardlessOfAltitude() {
        Site elevated = new Site("shifted_house", 2, 250, 2, true);
        assertEquals(elevated, select(Mode.NEAREST, List.of(visited, elevated), 0, 0).orElseThrow());
    }

    @Test
    void progressionMovesToNextUndiscoveredSite() {
        Site discoveredRuin = new Site(unknown.id(), unknown.x(), unknown.y(), unknown.z(), true);
        assertEquals(camp, select(Mode.UNEXPLORED, List.of(visited, discoveredRuin, camp), 0, 0).orElseThrow());
    }

    @Test
    void oldAndUnknownModeValuesSafelyReturnToCampAndModesCycle() {
        assertEquals(Mode.CAMP, Mode.fromIndex(0));
        assertEquals(Mode.CAMP, Mode.fromIndex(-1));
        assertEquals(Mode.CAMP, Mode.fromIndex(500));
        assertEquals(Mode.UNEXPLORED, Mode.CAMP.next());
        assertEquals(Mode.NEAREST, Mode.CAMP.next().next());
        assertEquals(Mode.CAMP, Mode.CAMP.next().next().next());
    }
}
