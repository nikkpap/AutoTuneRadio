package gr.radiorise.player;
import java.util.regex.Pattern;
public final class LogRedactor {
    private LogRedactor() {}
    private static final Pattern URI=Pattern.compile("(?i)(?:https?://|content://|file://|mailto:)[^\\s]+");
    public static String sanitize(String text) {
        String masked=URI.matcher(text).replaceAll("[URI]");
        return masked.substring(0,Math.min(12000,masked.length()));
    }
}
