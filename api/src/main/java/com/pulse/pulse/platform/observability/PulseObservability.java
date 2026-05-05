package com.pulse.pulse.platform.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Tags;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;
import java.util.function.Supplier;

@Component
public class PulseObservability {

    private final ObservationRegistry observationRegistry;
    private final MeterRegistry meterRegistry;

    public PulseObservability(ObservationRegistry observationRegistry, MeterRegistry meterRegistry) {
        this.observationRegistry = observationRegistry;
        this.meterRegistry = meterRegistry;
    }

    public Observation start(String name) {
        return Observation.createNotStarted(name, observationRegistry).start();
    }

    public <T> T scoped(Observation observation, Supplier<T> supplier) {
        try (Observation.Scope ignored = observation.openScope()) {
            return supplier.get();
        }
    }

    public void scoped(Observation observation, Runnable runnable) {
        scoped(observation, () -> {
            runnable.run();
            return null;
        });
    }

    public <T> T observe(String name, Consumer<Observation> customizer, Supplier<T> supplier) {
        Observation observation = Observation.createNotStarted(name, observationRegistry);
        if (customizer != null) {
            customizer.accept(observation);
        }
        observation.start();
        try (Observation.Scope ignored = observation.openScope()) {
            T result = supplier.get();
            low(observation, "result", "success");
            return result;
        } catch (RuntimeException e) {
            low(observation, "result", "error");
            observation.error(e);
            throw e;
        } finally {
            observation.stop();
        }
    }

    public void observe(String name, Consumer<Observation> customizer, Runnable runnable) {
        observe(name, customizer, () -> {
            runnable.run();
            return null;
        });
    }

    public Counter counter(String name, String... tags) {
        return Counter.builder(name)
                .tags(tags)
                .register(meterRegistry);
    }

    public Timer.Sample timerSample() {
        return Timer.start(meterRegistry);
    }

    public void stopTimer(Timer.Sample sample, String name, String... tags) {
        sample.stop(Timer.builder(name)
                .tags(tags)
                .register(meterRegistry));
    }

    public Observation low(Observation observation, String key, Object value) {
        if (value == null) {
            return observation;
        }
        String normalized = String.valueOf(value);
        if (!normalized.isBlank()) {
            observation.lowCardinalityKeyValue(key, normalized);
        }
        return observation;
    }

    public Observation high(Observation observation, String key, Object value) {
        if (value == null) {
            return observation;
        }
        String normalized = String.valueOf(value);
        if (!normalized.isBlank()) {
            observation.highCardinalityKeyValue(key, normalized);
        }
        return observation;
    }

    public Tags tags(String... tags) {
        return Tags.of(tags);
    }
}
