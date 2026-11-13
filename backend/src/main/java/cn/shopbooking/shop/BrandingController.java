package cn.shopbooking.shop;

import cn.shopbooking.account.Accounts.Viewer;
import cn.shopbooking.shop.ShopBranding.Brand;
import cn.shopbooking.web.ApiException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BrandingController {
    public record Change(Brand brand, boolean publish) {}

    private final ShopBranding branding;
    private final ShopService shops;

    public BrandingController(ShopBranding branding, ShopService shops) {
        this.branding = branding;
        this.shops = shops;
    }

    @GetMapping("/api/v1/owner/shop/branding")
    public Brand draft(@AuthenticationPrincipal Viewer viewer) {
        return branding.get(viewer.shopId(), false);
    }

    @PutMapping("/api/v1/owner/shop/branding")
    public Brand save(@AuthenticationPrincipal Viewer viewer, @RequestBody Change input) {
        if (input.brand() == null) {
            throw ApiException.invalid("请填写预约站外观。");
        }
        return branding.save(viewer.shopId(), input.brand(), input.publish());
    }

    @GetMapping("/api/v1/public/{slug}/branding")
    public Brand published(@PathVariable String slug) {
        return branding.get(shops.published(slug).id(), true);
    }
}
