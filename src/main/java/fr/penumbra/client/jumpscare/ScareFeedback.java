package fr.penumbra.client.jumpscare;

/** Presentation vocabulary deliberately has no image, texture, or flash channel. */
record ScareFeedback(Sound sound, int shakeTicks, float shakeScale,
                     boolean critical, boolean contact) {
    enum Sound { NONE, STINGER, CREEPER, WHISPER, SHRIEK }

    static ScareFeedback forType(String type) {
        if (type == null) return null;
        return switch (type) {
            case "impact" -> new ScareFeedback(Sound.NONE, 6, 0.8f, true, true);
            case "sting" -> new ScareFeedback(Sound.STINGER, 8, 0.6f, false, false);
            case "fake_creeper" -> new ScareFeedback(Sound.CREEPER, 0, 0.0f, false, false);
            case "apparition" -> new ScareFeedback(Sound.WHISPER, 4, 0.25f, true, false);
            case "screamer" -> new ScareFeedback(Sound.SHRIEK, 8, 0.8f, true, false);
            case "lunge_screamer", "rampant_screamer", "absent_screamer",
                    "chorus_screamer", "refuge_screamer", "mimic_screamer", "listener_screamer" ->
                    new ScareFeedback(Sound.SHRIEK, 8, 0.8f, true, true);
            default -> null;
        };
    }
}
