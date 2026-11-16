package com.example.library.resolver;

import com.example.library.annotation.CurrentUsername;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.web.context.request.NativeWebRequest;

import java.lang.reflect.Method;
import java.security.Principal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrentUsernameArgumentResolverTest {

    private final CurrentUsernameArgumentResolver resolver = new CurrentUsernameArgumentResolver();

    @Mock
    private NativeWebRequest webRequest;

    @SuppressWarnings("unused")
    private void sampleEndpoint(@CurrentUsername String annotated,
                                String plain,
                                @CurrentUsername Long annotatedWrongType) {
    }

    private MethodParameter parameter(int index) throws NoSuchMethodException {
        Method method = getClass().getDeclaredMethod("sampleEndpoint", String.class, String.class, Long.class);
        return new MethodParameter(method, index);
    }

    // ---------- AC6: supportsParameter ----------

    @Test
    void supportsParameter_annotatedString_returnsTrue() throws Exception {
        assertThat(resolver.supportsParameter(parameter(0))).isTrue();
    }

    @Test
    void supportsParameter_parameterWithoutAnnotation_returnsFalse() throws Exception {
        assertThat(resolver.supportsParameter(parameter(1))).isFalse();
    }

    @Test
    void supportsParameter_annotatedButNotString_returnsFalse() throws Exception {
        assertThat(resolver.supportsParameter(parameter(2))).isFalse();
    }

    // ---------- AC7: resolveArgument ----------

    @Test
    void resolveArgument_authenticatedRequest_returnsPrincipalNameAsString() throws Exception {
        // Arrange
        Principal principal = () -> "alice";
        when(webRequest.getUserPrincipal()).thenReturn(principal);

        // Act
        Object result = resolver.resolveArgument(parameter(0), null, webRequest, null);

        // Assert
        assertThat(result).isInstanceOf(String.class).isEqualTo("alice");
    }

    @Test
    void resolveArgument_noPrincipal_returnsNull() throws Exception {
        // Arrange
        when(webRequest.getUserPrincipal()).thenReturn(null);

        // Act
        Object result = resolver.resolveArgument(parameter(0), null, webRequest, null);

        // Assert
        assertThat(result).isNull();
    }
}