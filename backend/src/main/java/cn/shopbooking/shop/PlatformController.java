package cn.shopbooking.shop;

import cn.shopbooking.account.Accounts.Viewer;
import cn.shopbooking.booking.Bookings;
import cn.shopbooking.library.*;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
public class PlatformController {
    private final PlatformOperations platform;
    private final ShopService shops;
    private final Libraries libraries;
    private final Campus campus;
    private final Bookings bookings;
    private final SeatReservations reservations;
    private final SeatLifecycle lifecycle;
    public PlatformController(PlatformOperations platform, ShopService shops, Libraries libraries, Campus campus,
                              Bookings bookings, SeatReservations reservations, SeatLifecycle lifecycle) {
        this.platform = platform; this.shops = shops; this.libraries = libraries; this.campus = campus;
        this.bookings = bookings; this.reservations = reservations; this.lifecycle = lifecycle;
    }
    @GetMapping("/overview") public PlatformOperations.Summary overview() { return platform.summary(lifecycle.now()); }
    @GetMapping("/merchants/{id}/setup") public PlatformOperations.MerchantSetup setup(@PathVariable long id) {
        return platform.merchantSetup(id);
    }
    @GetMapping("/venues/{id}/cover") public PlatformOperations.Cover cover(@PathVariable long id) {
        return platform.cover(id);
    }
    @PutMapping("/venues/{id}/cover") public PlatformOperations.Cover cover(@PathVariable long id, @RequestBody PlatformOperations.Cover input) {
        return platform.saveCover(id, input);
    }
    @GetMapping("/merchants") public List<PlatformOperations.Merchant> merchants() { return platform.merchants(); }
    @DeleteMapping("/merchants/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archiveMerchant(@PathVariable long id, @AuthenticationPrincipal Viewer viewer) { platform.archiveMerchant(id, viewer.username()); }
    @PostMapping("/merchants/{id}/restore") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restoreMerchant(@PathVariable long id, @AuthenticationPrincipal Viewer viewer) { platform.restoreMerchant(id, viewer.username()); }
    @PostMapping("/merchants/{id}/decision")
    public ShopService.Shop decision(@PathVariable long id, @AuthenticationPrincipal Viewer viewer, @RequestBody PlatformOperations.Decision input) {
        return platform.decide(id, viewer.username(), input);
    }
    @GetMapping("/merchants/{id}/bookings")
    public List<Bookings.Order> orders(@PathVariable long id, @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate through) {
        platform.requireMerchant(id); return bookings.list(id, null, from, through);
    }
    @GetMapping("/venues") public List<PlatformOperations.Venue> venues(@RequestParam(defaultValue = "false") boolean archived) {
        return platform.venues(archived);
    }
    @DeleteMapping("/venues/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archiveVenue(@PathVariable long id) { platform.archiveVenue(id); }
    @PostMapping("/venues/{id}/restore") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restoreVenue(@PathVariable long id) { platform.restoreVenue(id); }
    @PostMapping("/venues") public ShopService.Shop create(@RequestBody PlatformOperations.NewVenue input) { return platform.createVenue(input); }
    @GetMapping("/venues/{id}/profile") public ShopService.Shop profile(@PathVariable long id) { platform.requireVenue(id); return shops.get(id); }
    @Transactional
    @PutMapping("/venues/{id}/profile") public ShopService.Shop profile(@PathVariable long id, @RequestBody ShopService.Details input) {
        platform.lockEditableVenue(id); return shops.update(id, input);
    }
    @GetMapping("/venues/{id}/discovery") public Campus.Profile discovery(@PathVariable long id) { platform.requireVenue(id); return campus.profile(id); }
    @Transactional
    @PutMapping("/venues/{id}/discovery") public Campus.Profile discovery(@PathVariable long id, @RequestBody Campus.Profile input) {
        platform.lockEditableVenue(id); return campus.save(id, input);
    }
    @GetMapping("/venues/{id}") public Libraries.Settings settings(@PathVariable long id) { platform.requireVenue(id); return libraries.settings(id); }
    @PutMapping("/venues/{id}") public Libraries.Settings settings(@PathVariable long id, @RequestBody Libraries.Settings input) {
        platform.requireVenue(id); return libraries.save(id, input);
    }
    @GetMapping("/venues/{id}/seats") public List<Libraries.Seat> seats(@PathVariable long id) { platform.requireVenue(id); return libraries.seats(id); }
    @PostMapping("/venues/{id}/seats") public Libraries.Seat addSeat(@PathVariable long id, @RequestBody Libraries.SeatInput input) {
        platform.requireVenue(id); return libraries.saveSeat(id, null, input);
    }
    @PutMapping("/venues/{id}/seats/{seat}") public Libraries.Seat updateSeat(@PathVariable long id, @PathVariable long seat, @RequestBody Libraries.SeatInput input) {
        platform.requireVenue(id); return libraries.saveSeat(id, seat, input);
    }
    @PostMapping("/venues/{id}/seats/batch")
    public List<Libraries.Seat> addSeats(@PathVariable long id, @RequestBody List<Libraries.SeatInput> seats) {
        platform.requireVenue(id);
        return libraries.addBatch(id, seats);
    }
    @GetMapping("/venues/{id}/reservations/unfinished")
    public List<SeatReservations.Reservation> unfinished(@PathVariable long id) {
        platform.requireVenue(id); lifecycle.refresh(); return reservations.unfinished(id);
    }
    @GetMapping("/venues/{id}/reservations") public List<SeatReservations.Reservation> reservations(@PathVariable long id, @RequestParam LocalDate date) {
        platform.requireVenue(id); lifecycle.refresh(); return reservations.forOwner(id, date);
    }
    @PostMapping("/venues/{id}/reservations/{reservation}/cancel") public SeatReservations.Reservation cancel(@PathVariable long id, @PathVariable UUID reservation) {
        platform.requireVenue(id); lifecycle.refresh(); return reservations.ownerAction(id, reservation, "cancel");
    }
}
