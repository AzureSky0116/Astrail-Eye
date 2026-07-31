package dev.astrail.eye.api.setting;

import java.util.List;

public interface ChoiceSetting {
    List<String> options();

    boolean isSelected(String option);

    boolean choose(String option);

    boolean multiSelect();
}
