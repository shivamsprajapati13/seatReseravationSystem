package seatReservationSystem.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import seatReservationSystem.entity.Reservation;
import seatReservationSystem.entity.ReservationStatus;

import java.util.Optional;
import java.util.UUID;

public interface ReservationRepository
        extends JpaRepository<Reservation, UUID> {

    Optional<Reservation> findByUserIdAndIdempotencyKey(
            String userId,
            String idempotencyKey
    );

    long countByShowIdAndUserIdAndStatus(
            UUID showId,
            String userId,
            ReservationStatus status
    );

    @Query(
            value = """
                    SELECT *
                    FROM reservations
                    WHERE id = :id
                    FOR UPDATE
                    """,
            nativeQuery = true
    )
    Optional<Reservation> findByIdForUpdate(UUID id);
}