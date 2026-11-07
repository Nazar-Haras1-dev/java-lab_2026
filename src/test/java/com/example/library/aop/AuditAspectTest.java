package com.example.library.aop;

import com.example.library.dto.BookDto;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditAspectTest {

    private static final Instant NOW = Instant.parse("2026-11-06T10:00:00Z");

    @Mock
    private JoinPoint joinPoint;
    @Mock
    private Signature signature;
    @Mock
    private AuditTrail auditTrail;

    private AuditAspect aspect;

    @SuppressWarnings("unused")
    @Audited(action = "CREATE_BOOK")
    void createMethod() {
    }

    @SuppressWarnings("unused")
    @Audited(action = "DELETE_BOOK")
    void deleteMethod() {
    }

    private Audited annotation(String methodName) throws NoSuchMethodException {
        return getClass().getDeclaredMethod(methodName).getAnnotation(Audited.class);
    }

    @BeforeEach
    void setUp() {
        aspect = new AuditAspect(auditTrail, Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(joinPoint.getSignature()).thenReturn(signature);
        lenient().when(signature.toShortString()).thenReturn("BookService.method(..)");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void audit_createReturnsBookDto_recordsUserActionAndBookDetails() throws Exception {
        // Arrange
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("alice", "pw"));
        BookDto result = new BookDto();
        result.setId(7L);
        result.setIsbn("9780132350884");

        // Act
        aspect.audit(joinPoint, annotation("createMethod"), result);

        // Assert
        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditTrail).add(captor.capture());
        AuditEntry entry = captor.getValue();
        assertThat(entry.user()).isEqualTo("alice");
        assertThat(entry.action()).isEqualTo("CREATE_BOOK");
        assertThat(entry.method()).isEqualTo("BookService.method(..)");
        assertThat(entry.details()).isEqualTo("bookId=7, isbn=9780132350884");
        assertThat(entry.timestamp()).isEqualTo(NOW);
    }

    @Test
    void audit_voidDeleteWithIdArgument_recordsBookIdFromArgument() throws Exception {
        // Arrange
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("admin", "pw"));
        when(joinPoint.getArgs()).thenReturn(new Object[]{5L});

        // Act
        aspect.audit(joinPoint, annotation("deleteMethod"), null);

        // Assert
        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditTrail).add(captor.capture());
        assertThat(captor.getValue().action()).isEqualTo("DELETE_BOOK");
        assertThat(captor.getValue().details()).isEqualTo("bookId=5");
    }

    @Test
    void audit_noAuthentication_recordsAnonymousUser() throws Exception {
        // Act
        aspect.audit(joinPoint, annotation("createMethod"), "something");

        // Assert
        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditTrail).add(captor.capture());
        assertThat(captor.getValue().user()).isEqualTo("anonymous");
        assertThat(captor.getValue().details()).isEqualTo("-");
    }
}