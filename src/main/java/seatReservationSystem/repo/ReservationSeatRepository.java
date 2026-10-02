package seatReservationSystem.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import seatReservationSystem.entity.ReservationSeat;
import seatReservationSystem.entity.ReservationSeatId;

import java.util.List;
import java.util.UUID;

public interface ReservationSeatRepository
        extends JpaRepository<ReservationSeat, ReservationSeatId> {

    List<ReservationSeat> findByReservationIdOrderBySeatNumberAsc(
            UUID reservationId
    );
}
