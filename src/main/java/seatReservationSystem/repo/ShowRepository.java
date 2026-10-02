package seatReservationSystem.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import seatReservationSystem.entity.Show;

import java.util.UUID;

public interface ShowRepository extends JpaRepository<Show, UUID> {
}
