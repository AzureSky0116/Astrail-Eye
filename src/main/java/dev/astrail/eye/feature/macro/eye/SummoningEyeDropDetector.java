package dev.astrail.eye.feature.macro.eye;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Recognizes Hypixel's game-message notification for an obtained Summoning Eye. */
final class SummoningEyeDropDetector {
    private static final Pattern QUANTITY = Pattern.compile("(?:^|\\s)(\\d+)x\\s+summoning eyes?");

    private SummoningEyeDropDetector() {
    }

    static int count(String text, boolean overlay) {
        if (overlay || text == null) return 0;
        String normalized = text.toLowerCase(Locale.ROOT);
        if (!normalized.contains("rare drop") || !normalized.contains("summoning eye")) return 0;
        Matcher quantity = QUANTITY.matcher(normalized);
        if (!quantity.find()) return 1;
        try {
            return Math.max(1, Integer.parseInt(quantity.group(1)));
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }
}
