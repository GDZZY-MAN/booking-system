package cn.shopbooking.booking;

import cn.shopbooking.account.Accounts.Viewer;
import java.time.LocalDate;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EmployeeController {
    private final Bookings bookings;

    public EmployeeController(Bookings bookings) { this.bookings = bookings; }

    @GetMapping("/api/v1/employee/bookings")
    public List<Bookings.Order> orders(@AuthenticationPrincipal Viewer viewer,
                                       @RequestParam(required = false) LocalDate from,
                                       @RequestParam(required = false) LocalDate through) {
        return bookings.list(viewer.shopId(), viewer.employeeId(), from, through);
    }
}
