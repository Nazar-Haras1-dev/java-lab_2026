package com.example.library.async;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.List;

/** Спільні допоміжні класи тестів: легкий Spring-контекст БЕЗ Spring Boot, керована «повільність» без sleep. */
final class AsyncTestSupport {

    private AsyncTestSupport() { }

    static AnnotationConfigApplicationContext context(Map<String, Object> props, Class<?>... configs) {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", props));
        ctx.register(configs);
        ctx.refresh();
        return ctx;
    }

    /** Фіксує імена потоків, у яких виконувалась «повільна» дія, і може чекати на латч. */
    static class ControlledLatency extends SimulatedLatency {
        final List<String> threads = new CopyOnWriteArrayList<>();
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release;
        volatile RuntimeException failWith;

        ControlledLatency(CountDownLatch release) {
            super(0);
            this.release = release;
        }

        @Override
        public void pause(String operation) {
            threads.add(Thread.currentThread().getName());
            entered.countDown();
            if (failWith != null) {
                throw failWith;
            }
            if (release != null) {
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("latch was never released");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            }
        }
    }
}
