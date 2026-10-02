package seatReservationSystem.repo;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import seatReservationSystem.entity.ShowUserLock;
import seatReservationSystem.entity.ShowUserLockId;

import java.util.Optional;
import java.util.UUID;

public interface ShowUserLockRepository
        extends JpaRepository<
                ShowUserLock,
                ShowUserLockId> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT l
        FROM ShowUserLock l
        WHERE l.id.showId = :showId
        AND l.id.userId = :userId
    """)
    Optional<ShowUserLock> findForUpdate(
            UUID showId,
            String userId
    );
}
