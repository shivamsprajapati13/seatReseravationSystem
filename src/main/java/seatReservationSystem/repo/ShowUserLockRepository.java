package seatReservationSystem.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import seatReservationSystem.entity.ShowUserLock;
import seatReservationSystem.entity.ShowUserLockId;

import java.util.Optional;
import java.util.UUID;

public interface ShowUserLockRepository
        extends JpaRepository<ShowUserLock, ShowUserLockId> {

    @Modifying
    @Query(
            value = """
                    INSERT IGNORE INTO show_user_state (show_id, user_id, reserved_count)
                    VALUES (:showId, :userId, 0)
                    """,
            nativeQuery = true
    )
    void ensureRowExists(UUID showId, String userId);

    @Query(
            value = """
                    SELECT show_id, user_id, reserved_count
                    FROM show_user_state
                    WHERE show_id = :showId
                      AND user_id = :userId
                    FOR UPDATE
                    """,
            nativeQuery = true
    )
    Optional<ShowUserLock> findForUpdate(UUID showId, String userId);
}
