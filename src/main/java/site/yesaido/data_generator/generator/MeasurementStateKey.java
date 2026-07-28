package site.yesaido.data_generator.generator;

import site.yesaido.data_generator.domain.MeasurementType;

record MeasurementStateKey(
        String deviceEui,
        MeasurementType measurementType
) {
    MeasurementStateKey {
        if (deviceEui == null || deviceEui.isBlank()) {
            throw new IllegalArgumentException("deviceEui는 null이거나 공백일 수 없습니다.");
        }

        if (measurementType == null) {
            throw new IllegalArgumentException("measurementType은 null일 수 없습니다.");
        }
    }
}