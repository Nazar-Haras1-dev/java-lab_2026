package com.example.scheduler;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationConfigTest {

    private static Properties load() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        return yaml.getObject();
    }

    @Test
    void applicationYml_ownFile_definesPort8081DifferentFromLibraryService() {
        // Act
        Properties props = load();

        // Assert
        assertThat(props.getProperty("server.port")).isEqualTo("8081").isNotEqualTo("8080");
    }

    @Test
    void applicationYml_quartz_usesJdbcStoreAndNeverInitializesSchema() {
        // Act
        Properties props = load();

        // Assert
        assertThat(props.getProperty("spring.quartz.job-store-type")).isEqualTo("jdbc");
        assertThat(props.getProperty("spring.quartz.jdbc.initialize-schema")).isEqualTo("never");
    }

    @Test
    void mainClass_schedulerApplication_isSpringBootApplication() {
        // Act + Assert
        assertThat(SchedulerApplication.class.getAnnotation(SpringBootApplication.class)).isNotNull();
    }
}
