package seatReservationSystem.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class ReservationMetrics {

    private final Counter confirmed;
    private final Counter seatTaken;
    private final Counter userLimit;
    private final Counter idempotentReplay;
    private final Counter idempotencyConflict;
    private final Counter cancelled;

    public ReservationMetrics(MeterRegistry registry) {
        confirmed = Counter.builder("reservations_confirmed_total")
                .description("Seats successfully confirmed via reserve")
                .register(registry);

        seatTaken = Counter.builder("reservations_declined_total")
                .description("Reserve attempts declined by domain reason")
                .tag("reason", "seat_taken")
                .register(registry);

        userLimit = Counter.builder("reservations_declined_total")
                .description("Reserve attempts declined by domain reason")
                .tag("reason", "per_user_limit")
                .register(registry);

        idempotentReplay = Counter.builder("reservations_declined_total")
                .description("Idempotent retries (same key and body; no new reservation)")
                .tag("reason", "idempotent_replay")
                .register(registry);

        idempotencyConflict = Counter.builder("reservations_declined_total")
                .description("Same idempotency key reused with a different seat set")
                .tag("reason", "idempotency_conflict")
                .register(registry);

        cancelled = Counter.builder("reservations_cancelled_total")
                .description("Seats released via reservation cancel")
                .register(registry);
    }

    public void confirmed(int seatCount) {
        confirmed.increment(seatCount);
    }

    public void seatTaken() {
        seatTaken.increment();
    }

    public void userLimitExceeded() {
        userLimit.increment();
    }

    public void idempotentReplay() {
        idempotentReplay.increment();
    }

    public void idempotencyConflict() {
        idempotencyConflict.increment();
    }

    public void cancelled(int seatCount) {
        cancelled.increment(seatCount);
    }
}
