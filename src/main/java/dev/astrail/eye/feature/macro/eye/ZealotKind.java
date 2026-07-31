package dev.astrail.eye.feature.macro.eye;

import java.util.Locale;

enum ZealotKind {
    ZEALOT,
    BRUISER;

    static ZealotKind fromLabel(String label, boolean bruiserArea) {
        String normalized = label == null ? "" : label.toLowerCase(Locale.ROOT);
        if (normalized.contains("zealot bruiser") || normalized.contains("bruiser")) return BRUISER;
        if (normalized.contains("zealot")) return ZEALOT;
        return bruiserArea ? BRUISER : ZEALOT;
    }
}
