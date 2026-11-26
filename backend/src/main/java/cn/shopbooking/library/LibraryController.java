package cn.shopbooking.library;

import cn.shopbooking.account.Accounts.Viewer;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class LibraryController {
    private final Libraries libraries;
    private final SeatReservations reservations;
    private final SeatLifecycle lifecycle;

    public LibraryController(Libraries libraries, SeatReservations reservations, SeatLifecycle lifecycle) {
        this.libraries = libraries;
        this.reservations = reservations;
        this.lifecycle = lifecycle;
    }

    @GetMapping("/api/v1/public/libraries")
    public List<Libraries.Venue> directory() { return libraries.directory(); }

    @GetMapping("/api/v1/public/libraries/{slug}")
    public Libraries.Venue venue(@PathVariable String slug) { return libraries.venue(slug); }

    @GetMapping("/api/v1/public/libraries/{slug}/slots")
    public List<Libraries.Slot> slots(@PathVariable String slug, @RequestParam LocalDate date) {
        return libraries.slots(slug, date);
    }

    @GetMapping("/api/v1/public/libraries/{slug}/seats")
    public List<Libraries.SeatOption> seats(@PathVariable String slug, @RequestParam OffsetDateTime startsAt) {
        lifecycle.refresh();
        return libraries.available(slug, startsAt);
    }

    @GetMapping("/api/v1/reader/profile")
    public SeatReservations.ReaderState profile(@AuthenticationPrincipal Viewer viewer) {
        lifecycle.refresh();
        return reservations.reader(viewer.readerId());
    }

    @GetMapping("/api/v1/reader/reservations")
    public List<SeatReservations.Reservation> mine(@AuthenticationPrincipal Viewer viewer) {
        lifecycle.refresh();
        return reservations.mine(viewer.readerId());
    }

    @PostMapping("/api/v1/reader/libraries/{slug}/reservations")
    public SeatReservations.Reservation reserve(@AuthenticationPrincipal Viewer viewer, @PathVariable String slug,
                                               @RequestBody SeatReservations.Create input) {
        lifecycle.refresh();
        return reservations.create(viewer.readerId(), slug, input);
    }

    @PostMapping("/api/v1/reader/reservations/{id}/{action:cancel|finish}")
    public SeatReservations.Reservation readerAction(@AuthenticationPrincipal Viewer viewer, @PathVariable UUID id,
                                                     @PathVariable String action) {
        lifecycle.refresh();
        return reservations.readerAction(viewer.readerId(), id, action);
    }
}
