package cn.shopbooking.shop;

import cn.shopbooking.account.Accounts;
import cn.shopbooking.account.Accounts.Viewer;
import cn.shopbooking.booking.Bookings;
import cn.shopbooking.schedule.ScheduleService;
import cn.shopbooking.web.ApiException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/owner")
public class OwnerController {
    public record Publication(Boolean published) {}
    public record Setup(boolean hasActiveService, boolean hasBookableEmployee) {}
    private final ShopService shops;
    private final ScheduleService schedules;
    private final Accounts accounts;
    private final Bookings bookings;
    private final PlatformOperations platform;

    public OwnerController(ShopService shops, ScheduleService schedules, Accounts accounts, Bookings bookings, PlatformOperations platform) {
        this.shops = shops;
        this.schedules = schedules;
        this.accounts = accounts;
        this.bookings = bookings;
        this.platform = platform;
    }

    public record Overview(ShopService.Shop shop, Setup setup, List<Bookings.Order> today) {}
    @GetMapping("/overview")
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Overview overview(@AuthenticationPrincipal Viewer viewer) {
        return new Overview(shop(viewer), setup(viewer), bookings.today(viewer.shopId()));
    }

    @GetMapping("/shop")
    public ShopService.Shop shop(@AuthenticationPrincipal Viewer viewer) { return shops.get(viewer.shopId()); }

    @PutMapping("/shop")
    public ShopService.Shop update(@AuthenticationPrincipal Viewer viewer, @RequestBody ShopService.Details input) {
        return shops.update(viewer.shopId(), input);
    }

    @GetMapping("/shop/setup")
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Setup setup(@AuthenticationPrincipal Viewer viewer) {
        return new Setup(shops.offerings(viewer.shopId()).stream().anyMatch(ShopService.Offering::active),
                schedules.readyToPublish(viewer.shopId()));
    }

    @PutMapping("/shop/publication")
    @Transactional
    public ShopService.Shop publish(@AuthenticationPrincipal Viewer viewer, @RequestBody Publication input) {
        if (input.published() == null) {
            throw ApiException.invalid("请选择是否发布。");
        }
        return platform.submit(viewer.shopId(), input.published());
    }

    @GetMapping("/services")
    public List<ShopService.Offering> services(@AuthenticationPrincipal Viewer viewer) {
        return shops.offerings(viewer.shopId());
    }

    @PostMapping("/services")
    public ShopService.Offering newService(@AuthenticationPrincipal Viewer viewer,
                                           @RequestBody ShopService.OfferingInput input) {
        return shops.saveOffering(viewer.shopId(), null, input);
    }

    @PutMapping("/services/{id}")
    public ShopService.Offering saveService(@AuthenticationPrincipal Viewer viewer, @PathVariable long id,
                                            @RequestBody ShopService.OfferingInput input) {
        return shops.saveOffering(viewer.shopId(), id, input);
    }

    @GetMapping("/employees")
    public List<ScheduleService.Employee> employees(@AuthenticationPrincipal Viewer viewer) {
        return schedules.employees(viewer.shopId());
    }

    @PostMapping("/employees")
    public ScheduleService.Employee newEmployee(@AuthenticationPrincipal Viewer viewer,
                                                @RequestBody ScheduleService.EmployeeInput input) {
        return schedules.save(viewer.shopId(), null, input);
    }

    @PutMapping("/employees/{id}")
    public ScheduleService.Employee saveEmployee(@AuthenticationPrincipal Viewer viewer, @PathVariable long id,
                                                 @RequestBody ScheduleService.EmployeeInput input) {
        return schedules.save(viewer.shopId(), id, input);
    }

    @PostMapping("/employees/{id}/invite")
    public Accounts.Invite invite(@AuthenticationPrincipal Viewer viewer, @PathVariable long id) {
        return accounts.invite(viewer.shopId(), id);
    }

    @GetMapping("/bookings")
    public List<Bookings.Order> orders(@AuthenticationPrincipal Viewer viewer,
                                       @RequestParam(required = false) LocalDate from,
                                       @RequestParam(required = false) LocalDate through) {
        return bookings.list(viewer.shopId(), null, from, through);
    }

    @PostMapping("/bookings/{id}/cancel")
    public Bookings.Order cancel(@AuthenticationPrincipal Viewer viewer, @PathVariable UUID id) {
        return bookings.cancel(viewer.shopId(), id);
    }
}
