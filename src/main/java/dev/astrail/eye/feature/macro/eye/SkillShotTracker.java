package dev.astrail.eye.feature.macro.eye;

/** Prevents a ranged skill from firing again while its previous projectile is travelling. */
final class SkillShotTracker {
    private static final float HEALTH_EPSILON = 0.01F;
    private static final int MIN_WAIT_TICKS = 12;
    private static final int MAX_WAIT_TICKS = 36;

    private int targetId = -1;
    private float healthBeforeShot;
    private int waitTicks;

    /** Returns true while the caller should keep waiting for the previous shot to resolve. */
    boolean shouldWait(int currentTargetId, float currentHealth) {
        if (targetId != currentTargetId) {
            clear();
            return false;
        }
        if (currentHealth + HEALTH_EPSILON < healthBeforeShot) {
            clear();
            return false;
        }
        if (waitTicks > 0 && --waitTicks > 0) return true;
        clear();
        return false;
    }

    void recordShot(int currentTargetId, float currentHealth, double distance) {
        targetId = currentTargetId;
        healthBeforeShot = currentHealth;
        waitTicks = waitTicksForDistance(distance);
    }

    void clear() {
        targetId = -1;
        healthBeforeShot = 0.0F;
        waitTicks = 0;
    }

    static int waitTicksForDistance(double distance) {
        int estimate = 8 + (int) Math.ceil(Math.max(0.0D, distance) / 1.2D);
        return Math.max(MIN_WAIT_TICKS, Math.min(MAX_WAIT_TICKS, estimate));
    }
}
