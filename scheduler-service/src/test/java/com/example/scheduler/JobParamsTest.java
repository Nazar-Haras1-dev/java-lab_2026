package com.example.scheduler;

import org.junit.jupiter.api.Test;
import org.quartz.JobDataMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JobParamsTest {

    @Test
    void string_keyMissing_returnsDocumentedDefault() {
        assertThat(JobParams.string(new JobDataMap(), "source", "daily")).isEqualTo("daily");
    }

    @Test
    void string_keyPresent_returnsValue() {
        // Arrange
        JobDataMap map = new JobDataMap();
        map.put("source", "manual");

        // Act + Assert
        assertThat(JobParams.string(map, "source", "daily")).isEqualTo("manual");
    }

    @Test
    void string_keyWithTypoInName_isTreatedAsMissingAndFallsBackToDefault() {
        // Arrange
        JobDataMap map = new JobDataMap();
        map.put("sourse", "manual");   // помилка в імені ключа

        // Act + Assert
        assertThat(JobParams.string(map, "source", "daily")).isEqualTo("daily");
    }

    @Test
    void string_wrongTypeOrBlank_throwsIllegalArgument() {
        // Arrange
        JobDataMap wrongType = new JobDataMap();
        wrongType.put("source", 42);
        JobDataMap blank = new JobDataMap();
        blank.put("source", "  ");

        // Act + Assert
        assertThatThrownBy(() -> JobParams.string(wrongType, "source", "daily"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("source");
        assertThatThrownBy(() -> JobParams.string(blank, "source", "daily"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void positiveInt_keyMissing_returnsDefault() {
        assertThat(JobParams.positiveInt(new JobDataMap(), "retentionDays", 90)).isEqualTo(90);
    }

    @Test
    void positiveInt_integerOrNumericString_isParsed() {
        // Arrange
        JobDataMap map = new JobDataMap();
        map.put("a", 30);
        map.put("b", " 45 ");

        // Act + Assert
        assertThat(JobParams.positiveInt(map, "a", 90)).isEqualTo(30);
        assertThat(JobParams.positiveInt(map, "b", 90)).isEqualTo(45);
    }

    @Test
    void positiveInt_notANumber_throwsIllegalArgument() {
        // Arrange
        JobDataMap map = new JobDataMap();
        map.put("retentionDays", "abc");

        // Act + Assert
        assertThatThrownBy(() -> JobParams.positiveInt(map, "retentionDays", 90))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("retentionDays");
    }

    @Test
    void positiveInt_zeroOrNegative_throwsIllegalArgument() {
        // Arrange
        JobDataMap zero = new JobDataMap();
        zero.put("retentionDays", 0);
        JobDataMap negative = new JobDataMap();
        negative.put("retentionDays", -5);

        // Act + Assert
        assertThatThrownBy(() -> JobParams.positiveInt(zero, "retentionDays", 90)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> JobParams.positiveInt(negative, "retentionDays", 90)).isInstanceOf(IllegalArgumentException.class);
    }
}
