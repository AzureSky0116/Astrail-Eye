package dev.astrail.eye.api.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.util.Objects;
import java.util.function.LongSupplier;

/** A transient action control that requires a second click within a short window. */
public final class ConfirmActionSetting extends Setting<Boolean> {
    private static final long DEFAULT_CONFIRM_MILLIS = 5_000L;

    private final Runnable action;
    private final LongSupplier clock;
    private final long confirmMillis;
    private long armedUntil;

    public ConfirmActionSetting(String id, String displayName, Runnable action) {
        this(id, displayName, action, System::currentTimeMillis, DEFAULT_CONFIRM_MILLIS);
    }

    ConfirmActionSetting(
        String id,
        String displayName,
        Runnable action,
        LongSupplier clock,
        long confirmMillis
    ) {
        super(id, displayName, false);
        this.action = Objects.requireNonNull(action, "action");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (confirmMillis < 1L) throw new IllegalArgumentException("confirmMillis must be positive");
        this.confirmMillis = confirmMillis;
    }

    /** Arms on the first click and runs the action on a timely second click. */
    public boolean activate() {
        if (armed()) {
            set(false);
            armedUntil = 0L;
            action.run();
            return true;
        }
        set(true);
        armedUntil = clock.getAsLong() + confirmMillis;
        return false;
    }

    public boolean armed() {
        if (!get()) return false;
        if (clock.getAsLong() <= armedUntil) return true;
        set(false);
        armedUntil = 0L;
        return false;
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(false);
    }

    @Override
    public void fromJson(JsonElement value) {
        disarm();
    }

    @Override
    public void fromString(String value) {
        disarm();
    }

    @Override
    public String displayValue() {
        return armed() ? "Confirm" : "Reset";
    }

    private void disarm() {
        set(false);
        armedUntil = 0L;
    }
}
