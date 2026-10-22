package fr.penumbra.client.light;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/** Pure planning of terrain updates; identical lights do not require repeated rebuilds. */
final class LightSectionUpdates {
    record Cell(int x, int y, int z, int luminance) { }
    record Section(int x, int y, int z) { }

    private LightSectionUpdates() { }

    static Set<Section> changed(Collection<Cell> previous, Collection<Cell> current) {
        Set<Cell> removed = new HashSet<>(previous);
        Set<Cell> added = new HashSet<>(current);
        removed.removeAll(current);
        added.removeAll(previous);
        removed.addAll(added);
        Set<Section> sections = new HashSet<>();
        for (Cell cell : removed) {
            // Floor division is essential on the negative side of world coordinates.
            int sx = Math.floorDiv(cell.x(), 16);
            int sy = Math.floorDiv(cell.y(), 16);
            int sz = Math.floorDiv(cell.z(), 16);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        sections.add(new Section(sx + dx, sy + dy, sz + dz));
                    }
                }
            }
        }
        return sections;
    }
}
