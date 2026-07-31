package dev.astrail.eye.api.module;

/** Supplies compact live statistics for the optional module-stats HUD element. */
public interface HudStatsProvider {
    String hudStatsTitle();

    int hudStatCount();

    String hudStatLabel(int index);

    int hudStatValue(int index);
}
