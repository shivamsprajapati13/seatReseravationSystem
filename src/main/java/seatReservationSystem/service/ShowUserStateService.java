package seatReservationSystem.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import seatReservationSystem.entity.ShowUserLock;
import seatReservationSystem.repo.ShowUserLockRepository;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShowUserStateService {

    private final ShowUserLockRepository showUserLockRepository;

    /**
     * Pessimistic row lock on (show, user). Lock order: user state before seats (deadlock-safe).
     */
    @Transactional
    public ShowUserLock lockForUpdate(UUID showId, String userId) {
        showUserLockRepository.ensureRowExists(showId, userId);
        return showUserLockRepository.findForUpdate(showId, userId)
                .orElseThrow(() ->
                        new IllegalStateException("Failed to lock show user state")
                );
    }
}
