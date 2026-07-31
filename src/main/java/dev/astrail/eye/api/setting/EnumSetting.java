package dev.astrail.eye.api.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class EnumSetting<E extends Enum<E>> extends Setting<E> implements ChoiceSetting {
    private final Class<E> enumType;

    public EnumSetting(String id, String displayName, Class<E> enumType, E defaultValue) {
        super(id, displayName, defaultValue);
        this.enumType = enumType;
    }

    public List<String> options() {
        return Arrays.stream(enumType.getEnumConstants()).map(value -> value.name().toLowerCase(Locale.ROOT)).toList();
    }

    @Override
    public boolean isSelected(String option) {
        return get().name().equalsIgnoreCase(option.trim().replace(' ', '_').replace('-', '_'));
    }

    @Override
    public boolean choose(String option) {
        fromString(option);
        return true;
    }

    @Override
    public boolean multiSelect() {
        return false;
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(get().name().toLowerCase(Locale.ROOT));
    }

    @Override
    public void fromJson(JsonElement value) {
        fromString(value.getAsString());
    }

    @Override
    public void fromString(String value) {
        String normalized = value.trim().replace(' ', '_').replace('-', '_');
        for (E candidate : enumType.getEnumConstants()) {
            if (candidate.name().equalsIgnoreCase(normalized)) {
                set(candidate);
                return;
            }
        }
        throw new IllegalArgumentException("Expected one of: " + String.join(", ", options()));
    }

    @Override
    public String displayValue() {
        return get().name().toLowerCase(Locale.ROOT);
    }
}
