package com.example.library.aop;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class SelfInvocationTest {

    private AuditTrail auditTrail;
    private AopDemoService proxy;

    @BeforeEach
    void setUp() {
        auditTrail = new AuditTrail();
        Clock clock = Clock.fixed(Instant.parse("2026-11-06T10:00:00Z"), ZoneOffset.UTC);

        AspectJProxyFactory factory = new AspectJProxyFactory(new AopDemoService());
        factory.setProxyTargetClass(true);
        factory.addAspect(new AuditAspect(auditTrail, clock));
        proxy = factory.getProxy();
    }

    @Test
    void externalCall_annotatedMethod_aspectRecordsAuditEntry() {
        // Act
        String result = proxy.auditedAction();

        // Assert
        assertThat(result).isEqualTo("audited action executed");
        assertThat(auditTrail.entries()).hasSize(1);
        assertThat(auditTrail.entries().get(0).action()).isEqualTo("DEMO_ACTION");
    }

    @Test
    void selfInvocation_sameAnnotatedMethod_aspectIsBypassedSilently() {
        // Act
        String result = proxy.callsAuditedActionInternally();

        // Assert: метод виконався, але аудиту немає і помилки немає
        assertThat(result).isEqualTo("audited action executed");
        assertThat(auditTrail.entries()).isEmpty();
    }

    @Test
    void sameMethodTwoWays_onlyTheExternalCallIsAudited() {
        // Act
        proxy.callsAuditedActionInternally();   // самовиклик
        proxy.auditedAction();                  // зовнішній виклик

        // Assert
        assertThat(auditTrail.entries()).hasSize(1);
    }
}