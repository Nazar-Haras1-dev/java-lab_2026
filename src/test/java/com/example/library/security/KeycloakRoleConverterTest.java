package com.example.library.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakRoleConverterTest {

    @Test
    void extractAuthorities_rolesPresent_returnsRolePrefixedAuthorities() {
        // Arrange
        Map<String, Object> realmAccess = Map.of("roles", List.of("ADMIN", "USER"));

        // Act
        Collection<GrantedAuthority> result = KeycloakRoleConverter.extractAuthorities(realmAccess);

        // Assert
        assertThat(result).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN", "ROLE_USER");
    }

    @Test
    void extractAuthorities_nullRealmAccess_returnsEmpty() {
        // Act
        Collection<GrantedAuthority> result = KeycloakRoleConverter.extractAuthorities(null);

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    void extractAuthorities_rolesKeyMissing_returnsEmpty() {
        // Act
        Collection<GrantedAuthority> result = KeycloakRoleConverter.extractAuthorities(Map.of());

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    void extractAuthorities_rolesNotACollection_returnsEmpty() {
        // Arrange
        Map<String, Object> realmAccess = Map.of("roles", "ADMIN");

        // Act
        Collection<GrantedAuthority> result = KeycloakRoleConverter.extractAuthorities(realmAccess);

        // Assert
        assertThat(result).isEmpty();
    }
}