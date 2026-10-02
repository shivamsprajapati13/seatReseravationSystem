package seatReservationSystem.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "show_user_locks")
@Data
@NoArgsConstructor
public class ShowUserLock {

    @EmbeddedId
    private ShowUserLockId id;
}