package cn.shopbooking.social;

import cn.shopbooking.account.Accounts.Viewer;
import cn.shopbooking.library.SeatLifecycle;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reader/social")
public class SocialController {
    private final ReaderSocial social;
    private final SeatLifecycle lifecycle;
    public SocialController(ReaderSocial social, SeatLifecycle lifecycle) {
        this.social = social;
        this.lifecycle = lifecycle;
    }
    @GetMapping("/preferences")
    public ReaderSocial.Preferences preferences(@AuthenticationPrincipal Viewer viewer) {
        return social.preferences(viewer.readerId());
    }
    @PutMapping("/preferences")
    public ReaderSocial.Preferences preferences(@AuthenticationPrincipal Viewer viewer,
                                               @RequestBody ReaderSocial.Preferences input) {
        return social.preferences(viewer.readerId(), input);
    }
    @GetMapping("/venues/{slug}/seats")
    public List<ReaderSocial.PresentSeat> seats(@AuthenticationPrincipal Viewer viewer, @PathVariable String slug) {
        lifecycle.refresh();
        return social.present(viewer.readerId(), slug);
    }
    @PostMapping("/venues/{slug}/reminders")
    public Map<String, String> remind(@AuthenticationPrincipal Viewer viewer, @PathVariable String slug,
                                     @RequestBody ReaderSocial.Target input) {
        lifecycle.refresh();
        return social.remind(viewer.readerId(), slug, input);
    }
    @PostMapping("/venues/{slug}/invitations")
    public ReaderSocial.Chat invite(@AuthenticationPrincipal Viewer viewer, @PathVariable String slug,
                                   @RequestBody ReaderSocial.Target input) {
        lifecycle.refresh();
        return social.invite(viewer.readerId(), slug, input);
    }
    @GetMapping("/reminders")
    public List<ReaderSocial.Notice> reminders(@AuthenticationPrincipal Viewer viewer) {
        return social.notices(viewer.readerId());
    }
    @GetMapping("/sent-reminders")
    public List<ReaderSocial.SentNotice> sentReminders(@AuthenticationPrincipal Viewer viewer) {
        return social.sentNotices(viewer.readerId());
    }
    @PostMapping("/reminders/{id}/{action:acknowledge|block}")
    public Map<String, String> reminderAction(@AuthenticationPrincipal Viewer viewer, @PathVariable UUID id,
                                            @PathVariable String action) {
        if (action.equals("block")) social.blockNotice(viewer.readerId(), id);
        else social.acknowledge(viewer.readerId(), id);
        return Map.of("message", action.equals("block") ? "已屏蔽该发送者。" : "已回应提醒。");
    }
    @GetMapping("/chats")
    public List<ReaderSocial.Chat> chats(@AuthenticationPrincipal Viewer viewer) {
        lifecycle.refresh();
        return social.chats(viewer.readerId());
    }
    @PostMapping("/chats/{id}/{action:accept|decline|withdraw|close|block}")
    public Map<String, String> chatAction(@AuthenticationPrincipal Viewer viewer, @PathVariable UUID id,
                                        @PathVariable String action) {
        lifecycle.refresh();
        social.action(viewer.readerId(), id, action);
        return Map.of("message", "操作已完成。");
    }
    @GetMapping("/chats/{id}/messages")
    public List<ReaderSocial.Message> messages(@AuthenticationPrincipal Viewer viewer, @PathVariable UUID id) {
        return social.messages(viewer.readerId(), id);
    }
    @PostMapping("/chats/{id}/messages")
    public ReaderSocial.Message send(@AuthenticationPrincipal Viewer viewer, @PathVariable UUID id,
                                     @RequestBody ReaderSocial.Send input) {
        return social.send(viewer.readerId(), id, input);
    }
    public record Seen(long through) {}
    @PostMapping("/chats/{id}/seen")
    public Map<String, String> seen(@AuthenticationPrincipal Viewer viewer, @PathVariable UUID id,
                                   @RequestBody Seen input) {
        social.seen(viewer.readerId(), id, input.through());
        return Map.of("message", "已读。");
    }
}
