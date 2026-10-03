package seatReservationSystem.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import seatReservationSystem.entity.Seat;
import seatReservationSystem.entity.SeatStatus;

import java.util.List;
import java.util.UUID;

public interface SeatRepository extends JpaRepository<Seat, UUID> {

    @Query(
            value = """
                    SELECT *
                    FROM seats
                    WHERE show_id = :showId
                      AND seat_number IN (:seatNumbers)
                    ORDER BY seat_number
                    FOR UPDATE
                    """,
            nativeQuery = true
    )
    List<Seat> findSeatsForUpdate(
            UUID showId,
            List<String> seatNumbers
    );

    List<Seat> findByShow_IdOrderBySeatNumberAsc(UUID showId);

    long countByStatus(SeatStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value = """
                    UPDATE seats
                    SET status = 'CONFIRMED',
                        held_by = :userId,
                        reservation_id = :reservationId
                    WHERE id = :seatId
                      AND status = 'AVAILABLE'
                    """,
            nativeQuery = true
    )
    int confirmSeatIfAvailable(
            UUID seatId,
            String userId,
            UUID reservationId
    );

    @Query(
            value = """
                    SELECT *
                    FROM seats
                    WHERE reservation_id = :reservationId
                    ORDER BY seat_number
                    FOR UPDATE
                    """,
            nativeQuery = true
    )
    List<Seat> findByReservationIdForUpdate(UUID reservationId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value = """
                    UPDATE seats
                    SET status = 'AVAILABLE',
                        held_by = NULL,
                        reservation_id = NULL,
                        hold_expires_at = NULL
                    WHERE id = :seatId
                      AND reservation_id = :reservationId
                      AND status = 'CONFIRMED'
                    """,
            nativeQuery = true
    )
    int releaseSeatIfOwned(UUID seatId, UUID reservationId);
}