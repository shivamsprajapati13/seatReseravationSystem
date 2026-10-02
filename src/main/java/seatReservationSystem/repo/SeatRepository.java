package seatReservationSystem.repo;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import seatReservationSystem.entity.Seat;

import java.util.List;
import java.util.UUID;

public interface SeatRepository extends JpaRepository<Seat, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT s
        FROM Seat s
        WHERE s.show.id = :showId
        AND s.seatNumber IN :seatNumbers
        ORDER BY s.seatNumber
    """)
    List<Seat> findSeatsForUpdate(
            UUID showId,
            List<String> seatNumbers
    );

    List<Seat> findByShow_IdOrderBySeatNumberAsc(UUID showId);
}