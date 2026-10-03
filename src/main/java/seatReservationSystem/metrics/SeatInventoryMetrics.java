package seatReservationSystem.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import seatReservationSystem.entity.SeatStatus;
import seatReservationSystem.repo.SeatRepository;

/**
 * Gauges scraped from the database so Prometheus values reconcile with GET /shows/{id} counts.
 */
@Component
public class SeatInventoryMetrics {

    public SeatInventoryMetrics(
            MeterRegistry registry,
            SeatRepository seatRepository
    ) {
        Gauge.builder("seats_available", seatRepository,
                        repo -> repo.countByStatus(SeatStatus.AVAILABLE))
                .description("Seats in AVAILABLE state across all shows")
                .register(registry);

        Gauge.builder("seats_held", seatRepository,
                        repo -> repo.countByStatus(SeatStatus.HELD))
                .description("Seats in HELD state across all shows")
                .register(registry);

        Gauge.builder("seats_confirmed", seatRepository,
                        repo -> repo.countByStatus(SeatStatus.CONFIRMED))
                .description("Seats in CONFIRMED state across all shows")
                .register(registry);

        Gauge.builder("seats_total", seatRepository, SeatRepository::count)
                .description("Total seat rows (available + held + confirmed)")
                .register(registry);
    }
}
