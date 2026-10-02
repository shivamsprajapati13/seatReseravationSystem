package seatReservationSystem.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import seatReservationSystem.dto.request.CreateShowRequest;
import seatReservationSystem.dto.response.ShowDetailResponse;
import seatReservationSystem.dto.response.ShowResponse;
import seatReservationSystem.security.AdminAuth;
import seatReservationSystem.service.ShowService;

import java.util.UUID;

@RestController
@RequestMapping("/shows")
@RequiredArgsConstructor
public class ShowController {

    private final ShowService showService;
    private final AdminAuth adminAuth;

    @PostMapping
    public ResponseEntity<ShowResponse> createShow(
            @RequestBody CreateShowRequest request,
            HttpServletRequest httpRequest
    ) {
        adminAuth.requireAdmin(httpRequest);

        ShowResponse created = showService.createShow(
                request.name(),
                request.seats(),
                request.pricePaise(),
                request.perUserLimit()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{showId}")
    public ShowDetailResponse getShow(@PathVariable UUID showId) {
        return showService.getShow(showId);
    }
}
