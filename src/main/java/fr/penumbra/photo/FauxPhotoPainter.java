package fr.penumbra.photo;

import java.util.Random;

/**
 * Peint des photographies que personne n'a prises (0.16.0).
 *
 * <p>Deux clichés générés en 128×72 RVB, le format exact de l'album
 * ({@link PhotoAlbumState#RGB_BYTES}) :</p>
 * <ul>
 *   <li>{@link #bedroomAtNight(long)} — une chambre dans le noir, un lit,
 *       et une silhouette pâle d'un pixel de large dans l'embrasure : la
 *       photo « prise pendant que vous dormiez » ;</li>
 *   <li>{@link #baseFromOutside(long)} — une bâtisse sombre vue de dehors,
 *       de nuit, une seule fenêtre allumée : l'angle d'où personne ne s'est
 *       jamais tenu.</li>
 * </ul>
 *
 * <p>Aucune dépendance Minecraft : {@code java.util.Random} seedé, sortie
 * déterministe, testable en JUnit. L'obscurité et le grain font le travail —
 * ces images se découvrent en petit, dans l'album, à moitié illisibles :
 * c'est leur illisibilité qui inquiète.</p>
 */
public final class FauxPhotoPainter {

    public static final int WIDTH = PhotoAlbumState.WIDTH;
    public static final int HEIGHT = PhotoAlbumState.HEIGHT;

    private FauxPhotoPainter() {
    }

    /** Chambre de nuit : lit sombre, embrasure, silhouette d'un pixel. */
    public static byte[] bedroomAtNight(long seed) {
        Random random = new Random(seed);
        byte[] img = new byte[PhotoAlbumState.RGB_BYTES];

        // Pièce : gradient vertical très sombre, sol à peine plus clair.
        for (int y = 0; y < HEIGHT; y++) {
            float depth = y / (float) HEIGHT;
            int base = 6 + Math.round(depth * 10);
            for (int x = 0; x < WIDTH; x++) {
                put(img, x, y, base, base - 1, base + 3);
            }
        }
        // Sol (tiers inférieur) : lattes suggérées par des lignes sombres.
        for (int y = HEIGHT * 2 / 3; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                add(img, x, y, 6, 5, 3);
                if ((x + y * 2) % 16 == 0) {
                    add(img, x, y, -4, -4, -4);
                }
            }
        }
        // Le lit, aux deux tiers droits : couverture mate, oreiller pâle.
        int bedLeft = 62 + random.nextInt(8);
        int bedTop = 34 + random.nextInt(6);
        int bedRight = Math.min(WIDTH - 4, bedLeft + 48);
        int bedBottom = Math.min(HEIGHT - 6, bedTop + 26);
        for (int y = bedTop; y < bedBottom; y++) {
            for (int x = bedLeft; x < bedRight; x++) {
                int wrinkle = random.nextInt(7);
                put(img, x, y, 44 + wrinkle, 16 + wrinkle / 2, 18 + wrinkle / 2);
            }
        }
        for (int y = bedTop + 2; y < bedTop + 9; y++) {          // oreiller
            for (int x = bedRight - 16; x < bedRight - 3; x++) {
                int soft = random.nextInt(6);
                put(img, x, y, 74 + soft, 72 + soft, 66 + soft);
            }
        }
        for (int x = bedLeft - 1; x < bedRight + 1; x++) {       // cadre en bois
            put(img, x, bedBottom, 26, 18, 10);
        }

        // L'embrasure de porte, bord gauche : un rectangle plus noir que la nuit.
        int doorLeft = 6 + random.nextInt(6);
        int doorWidth = 14;
        for (int y = 12; y < HEIGHT - 4; y++) {
            for (int x = doorLeft; x < doorLeft + doorWidth; x++) {
                put(img, x, y, 3, 3, 4);
            }
        }
        // La silhouette : une verticale pâle d'un pixel, presque rien.
        int figureX = doorLeft + 4 + random.nextInt(doorWidth - 8);
        int figureTop = 18 + random.nextInt(4);
        for (int y = figureTop; y < HEIGHT - 8; y++) {
            put(img, figureX, y, 96, 92, 84);
            if (random.nextFloat() < 0.3f) {
                put(img, figureX + 1, y, 44, 42, 38);
            }
        }
        // La tête : deux pixels plus larges, et un point d'œil.
        put(img, figureX - 1, figureTop, 96, 92, 84);
        put(img, figureX + 1, figureTop, 96, 92, 84);
        put(img, figureX - 1, figureTop + 1, 96, 92, 84);
        put(img, figureX + 1, figureTop + 1, 96, 92, 84);
        put(img, figureX, figureTop + 2, 150, 148, 138);

        grainAndVignette(img, random, 9);
        return img;
    }

    /** La base vue de dehors, de nuit : silhouette bâtie, une fenêtre allumée. */
    public static byte[] baseFromOutside(long seed) {
        Random random = new Random(seed);
        byte[] img = new byte[PhotoAlbumState.RGB_BYTES];

        // Ciel de nuit + étoiles éparses + lune gibbeuse.
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                put(img, x, y, 4, 5, 11);
            }
        }
        for (int i = 0; i < 40; i++) {
            int x = random.nextInt(WIDTH);
            int y = random.nextInt(HEIGHT / 2);
            int glow = 60 + random.nextInt(80);
            put(img, x, y, glow, glow, glow - 6);
        }
        int moonX = 12 + random.nextInt(WIDTH - 40);
        int moonY = 6 + random.nextInt(10);
        for (int dy = -3; dy <= 3; dy++) {
            for (int dx = -3; dx <= 3; dx++) {
                if (dx * dx + dy * dy <= 9 && dx > -3) {
                    put(img, moonX + dx, moonY + dy, 168, 168, 156);
                }
            }
        }

        // Sol : bande sombre d'herbe morte.
        int groundY = HEIGHT - 14 + random.nextInt(4);
        for (int y = groundY; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                int tuft = random.nextInt(5);
                put(img, x, y, 8 + tuft, 10 + tuft, 6);
            }
        }

        // La bâtisse : masse noire au toit irrégulier, plus noire que le ciel.
        int houseLeft = 28 + random.nextInt(20);
        int houseRight = houseLeft + 44 + random.nextInt(16);
        int houseTop = groundY - 26 - random.nextInt(8);
        int[] roof = new int[houseRight - houseLeft];
        for (int i = 0; i < roof.length; i++) {
            roof[i] = houseTop + (i % 9 == 0 ? random.nextInt(3) : 0);
        }
        for (int x = houseLeft; x < houseRight; x++) {
            for (int y = roof[x - houseLeft]; y < groundY; y++) {
                int grain = random.nextInt(3);
                put(img, x, y, 7 + grain, 6 + grain, 6 + grain);
            }
        }
        // Une porte devinée, fermée.
        int doorX = houseLeft + 6 + random.nextInt(8);
        for (int y = groundY - 9; y < groundY; y++) {
            put(img, doorX, y, 4, 3, 3);
            put(img, doorX + 1, y, 4, 3, 3);
            put(img, doorX + 2, y, 4, 3, 3);
        }
        // LA fenêtre allumée — chaude, seule, avec un halo.
        int winX = houseLeft + 14 + random.nextInt(Math.max(4, houseRight - houseLeft - 26));
        int winY = houseTop + 8 + random.nextInt(8);
        for (int dy = 0; dy < 4; dy++) {
            for (int dx = 0; dx < 4; dx++) {
                put(img, winX + dx, winY + dy, 172, 122, 44);
            }
        }
        for (int dy = -2; dy < 6; dy++) {
            for (int dx = -2; dx < 6; dx++) {
                if (dx >= 0 && dx < 4 && dy >= 0 && dy < 4) {
                    continue;
                }
                add(img, winX + dx, winY + dy, 26, 17, 5);
            }
        }
        // Dans la fenêtre : une tête d'un pixel sombre, ou rien. Selon la nuit.
        if (random.nextBoolean()) {
            put(img, winX + 1 + random.nextInt(2), winY + 1, 30, 22, 12);
            put(img, winX + 1, winY + 2, 30, 22, 12);
            put(img, winX + 2, winY + 2, 30, 22, 12);
        }

        grainAndVignette(img, random, 7);
        return img;
    }

    // ================= Outils =================

    private static void put(byte[] img, int x, int y, int r, int g, int b) {
        if (x < 0 || x >= WIDTH || y < 0 || y >= HEIGHT) {
            return;
        }
        int i = (y * WIDTH + x) * 3;
        img[i] = clamp(r);
        img[i + 1] = clamp(g);
        img[i + 2] = clamp(b);
    }

    private static void add(byte[] img, int x, int y, int r, int g, int b) {
        if (x < 0 || x >= WIDTH || y < 0 || y >= HEIGHT) {
            return;
        }
        int i = (y * WIDTH + x) * 3;
        img[i] = clamp((img[i] & 0xFF) + r);
        img[i + 1] = clamp((img[i + 1] & 0xFF) + g);
        img[i + 2] = clamp((img[i + 2] & 0xFF) + b);
    }

    private static byte clamp(int value) {
        return (byte) Math.max(0, Math.min(255, value));
    }

    /** Grain argentique + assombrissement des bords : l'album fait le reste. */
    private static void grainAndVignette(byte[] img, Random random, int grain) {
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                int noise = random.nextInt(grain * 2 + 1) - grain;
                double dx = (x - WIDTH / 2.0) / (WIDTH / 2.0);
                double dy = (y - HEIGHT / 2.0) / (HEIGHT / 2.0);
                int edge = (int) Math.round(14 * Math.max(0, dx * dx + dy * dy - 0.45));
                add(img, x, y, noise - edge, noise - edge, noise - edge);
            }
        }
    }
}
