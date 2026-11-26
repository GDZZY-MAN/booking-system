package cn.shopbooking.booking;

import cn.shopbooking.account.Accounts.Viewer;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reader/service-bookings")
public class ReaderBookingsController {
    private final Bookings bookings;
    public ReaderBookingsController(Bookings bookings) { this.bookings = bookings; }
    @GetMapping
    public List<Bookings.MyBooking> mine(@AuthenticationPrincipal Viewer viewer) { return bookings.mine(viewer.readerId()); }
    @PostMapping("/{id}/cancel")
    public Map<String, String> cancel(@AuthenticationPrincipal Viewer viewer, @PathVariable UUID id) {
        bookings.cancelByReader(viewer.readerId(), id);
        return Map.of("message", "服务预约已取消。");
    }
    @PostMapping("/{id}/review")
    public Map<String, String> review(@AuthenticationPrincipal Viewer viewer, @PathVariable UUID id, @RequestBody Bookings.Review input) {
        bookings.review(viewer.readerId(), id, input);
        return Map.of("message", "评价已发布。");
    }
}
