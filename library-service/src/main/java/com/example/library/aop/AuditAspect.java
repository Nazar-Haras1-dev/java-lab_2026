package com.example.library.aop;

import com.example.library.dto.BookDto;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Aspect
@Component
@Order(3)
public class AuditAspect {

    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("AUDIT");

    private final AuditTrail auditTrail;
    private final Clock clock;

    public AuditAspect(AuditTrail auditTrail, Clock clock) {
        this.auditTrail = auditTrail;
        this.clock = clock;
    }

    /** Спрацьовує лише якщо метод завершився без винятку. */
    @AfterReturning(pointcut = "@annotation(audited)", returning = "result")
    public void audit(JoinPoint joinPoint, Audited audited, Object result) {
        String user = CurrentUserName.get();
        String method = joinPoint.getSignature().toShortString();
        String details = describe(result, joinPoint.getArgs());

        auditTrail.add(new AuditEntry(clock.instant(), user, audited.action(), method, details));
        AUDIT_LOG.info("user={} action={} method={} details={}", user, audited.action(), method, details);
    }

    private static String describe(Object result, Object[] args) {
        if (result instanceof BookDto book) {
            return "bookId=" + book.getId() + ", isbn=" + book.getIsbn();
        }
        if (args != null && args.length > 0 && args[0] instanceof Long id) {
            return "bookId=" + id;
        }
        return "-";
    }
}