package cn.shopbooking.shop;

import cn.shopbooking.account.Accounts.Viewer;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class CampusController {
    private final Campus campus;
    public CampusController(Campus campus) { this.campus = campus; }
    @GetMapping("/api/v1/public/campus/services")
    public List<Campus.Listing> services() { return campus.services(); }
    @GetMapping("/api/v1/owner/discovery")
    public Campus.Profile profile(@AuthenticationPrincipal Viewer viewer) { return campus.profile(viewer.shopId()); }
    @PutMapping("/api/v1/owner/discovery")
    public Campus.Profile save(@AuthenticationPrincipal Viewer viewer, @RequestBody Campus.Profile input) {
        return campus.save(viewer.shopId(), input);
    }
}
