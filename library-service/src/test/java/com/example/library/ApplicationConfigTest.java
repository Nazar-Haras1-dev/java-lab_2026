package com.example.library;

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
    void applicationYml_ownFile_definesPort8080() {
        // Act
        Properties props = load();

        // Assert
        assertThat(props.getProperty("server.port")).isEqualTo("8080");
    }

    @Test
    void applicationYml_libraryService_hasNoQuartzSettings() {
        // Assert: розклад належить scheduler-service
        assertThat(load().stringPropertyNames()).noneMatch(k -> k.startsWith("spring.quartz"));
    }

    @Test
    void mainClass_libraryApplication_isSpringBootApplication() {
        // Act + Assert
        assertThat(LibraryApplication.class.getAnnotation(SpringBootApplication.class)).isNotNull();
    }
}
