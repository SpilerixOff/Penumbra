package fr.penumbra.photo.investigation;

/** Reponse comportementale revelee par une paire de preuves. */
public enum Counterplay {
    FLASH_BEFORE_FOLLOWING,
    LIGHT_THE_CEILING,
    KEEP_CAMERA_CLOSED;

    public String translationKey() {
        return "counterplay.penumbra." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
