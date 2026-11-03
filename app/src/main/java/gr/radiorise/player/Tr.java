package gr.radiorise.player;

/** Language switch shared by Java helpers (ICS parser) and Kotlin code. Kotlin keeps it in sync via Lang. */
public final class Tr {
    private Tr() {}
    public static volatile boolean english = false;
    public static String t(String greek, String englishText) { return english ? englishText : greek; }
}
