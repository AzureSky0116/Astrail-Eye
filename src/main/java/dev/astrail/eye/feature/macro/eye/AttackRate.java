package dev.astrail.eye.feature.macro.eye;

/** Converts a configured attacks-per-second rate to Minecraft client ticks. */
final class AttackRate {
    private static final double TICKS_PER_SECOND = 20.0D;

    private AttackRate() {
    }

    static int intervalTicks(double attacksPerSecond) {
        return Math.max(1, (int) Math.round(TICKS_PER_SECOND / Math.max(0.01D, attacksPerSecond)));
    }
}
