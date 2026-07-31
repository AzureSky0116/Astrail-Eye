package dev.astrail.eye.feature.macro.eye;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Tracks targets attacked during one pass and opens a fresh pass after a short idle delay. */
final class AttackSweepTracker {
    private final int rescanDelayTicks;
    private final Set<UUID> attacked = new HashSet<>();
    private int rescanTicks = -1;

    AttackSweepTracker(int rescanDelayTicks) {
        if (rescanDelayTicks < 1) throw new IllegalArgumentException("rescanDelayTicks must be positive");
        this.rescanDelayTicks = rescanDelayTicks;
    }

    boolean contains(UUID target) {
        return attacked.contains(target);
    }

    void record(UUID target) {
        attacked.add(target);
        rescanTicks = -1;
    }

    /** Advances the delay while the current pass has no remaining eligible target. */
    void tickExhausted() {
        if (attacked.isEmpty()) return;
        if (rescanTicks < 0) {
            rescanTicks = rescanDelayTicks;
            return;
        }
        if (--rescanTicks <= 0) clear();
    }

    void clear() {
        attacked.clear();
        rescanTicks = -1;
    }
}
