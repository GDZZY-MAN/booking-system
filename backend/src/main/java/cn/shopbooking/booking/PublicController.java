package cn.shopbooking.booking;

import cn.shopbooking.schedule.ScheduleService;
import cn.shopbooking.shop.ShopService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/{slug}")
public class PublicController {
    public record Store(String name, String description, List<ShopService.Offering> services) {}
    public record EmployeeOption(long id, String name) {}
    private final ShopService shops;
    private final ScheduleService schedules;
    private final Bookings bookings;

    public PublicController(ShopService shops, ScheduleService schedules, Bookings bookings) {
        this.shops = shops;
        this.schedules = schedules;
        this.bookings = bookings;
    }

    @GetMapping
    public Store store(@PathVariable String slug) {
        var shop = shops.published(slug);
        return new Store(shop.name(), shop.description(),
                shops.offerings(shop.id()).stream().filter(ShopService.Offering::active).toList());
    }

    @GetMapping("/employees")
    public List<EmployeeOption> employees(@PathVariable String slug, @RequestParam long serviceId) {
        var shop = shops.published(slug);
        shops.activeOffering(shop.id(), serviceId);
        return schedules.eligible(shop.id(), serviceId).stream()
                .map(member -> new EmployeeOption(member.id(), member.name())).toList();
    }

    @GetMapping("/slots")
    public List<Bookings.Slot> slots(@PathVariable String slug, @RequestParam long serviceId,
                                     @RequestParam long employeeId, @RequestParam LocalDate date) {
        return bookings.available(slug, serviceId, employeeId, date);
    }

    @PostMapping("/bookings")
    public Bookings.Receipt create(@PathVariable String slug, @RequestBody Bookings.Create input) {
        return bookings.create(slug, input);
    }
}
