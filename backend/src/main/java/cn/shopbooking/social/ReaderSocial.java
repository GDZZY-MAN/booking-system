package cn.shopbooking.social;

import cn.shopbooking.library.Libraries;
import cn.shopbooking.library.SeatLifecycle;
import cn.shopbooking.web.ApiException;
import cn.shopbooking.web.Inputs;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReaderSocial {
    public record Preferences(String nickname, boolean chatEnabled) {}
    public record Target(long seatId, String kind, UUID reservationId) {}
    public record PresentSeat(long id, String label, String area, boolean nearWindow, boolean powerOutlet,
                              boolean available, Integer floor, Integer mapRow, Integer mapColumn, UUID reservationId,
                              String nickname, boolean chatEnabled, OffsetDateTime startsAt, OffsetDateTime endsAt,
                              boolean remindersEnabled) {}
    public record Notice(UUID id, String kind, OffsetDateTime createdAt, boolean acknowledged) {}
    public record SentNotice(UUID id, String kind, String seatLabel, OffsetDateTime createdAt, boolean acknowledged) {}
    public record Chat(UUID id, String status, String resolutionReason, boolean incoming, String nickname, long unread) {}
    public record Message(long id, String body, boolean mine, OffsetDateTime createdAt) {}
    public record Send(String body, UUID requestKey) {}
    private record Occupant(UUID id, long readerId, long seatId, long shopId, OffsetDateTime startsAt, OffsetDateTime endsAt) {}
    private record ThreadRow(UUID id, long senderId, long recipientId, UUID senderReservation,
                             UUID recipientReservation, String status) {}
    private static final Map<String, String> REMINDERS = Map.of(
            "VOICE", "附近有位读者希望保持安静，请适当降低交谈声音。感谢理解，祝学习顺利。",
            "HEADPHONES", "附近有位读者希望保持安静，播放音频时请佩戴耳机。感谢理解，祝学习顺利。",
            "ITEMS", "附近有位读者希望保持安静，请轻放物品。感谢理解，祝学习顺利。");
    private final JdbcClient db;
    private final SeatLifecycle lifecycle;
    private final Libraries libraries;

    public ReaderSocial(JdbcClient db, SeatLifecycle lifecycle, Libraries libraries) {
        this.db = db;
        this.lifecycle = lifecycle;
        this.libraries = libraries;
    }

    public Preferences preferences(long reader) {
        return db.sql("select nickname, chat_enabled from reader_social_preferences where reader_id = ?")
                .param(reader).query(Preferences.class).optional().orElse(new Preferences("同馆读者", true));
    }

    @Transactional
    public Preferences preferences(long reader, Preferences input) {
        lifecycle.lock();
        String nickname = Inputs.required(input.nickname(), 30, "聊天昵称");
        db.sql("""
                insert into reader_social_preferences(reader_id, nickname, chat_enabled) values (?, ?, ?)
                on conflict(reader_id) do update set nickname = excluded.nickname, chat_enabled = excluded.chat_enabled
                """).params(reader, nickname, input.chatEnabled()).update();
        if (!input.chatEnabled()) {
            db.sql("""
                    update reader_chat set status = 'DISABLED', resolution_reason = 'RECIPIENT_DISABLED',
                        resolved_at = ?, resolved_by_reader_id = ?
                    where recipient_id = ? and status = 'PENDING'
                    """).params(lifecycle.now(), reader, reader).update();
        }
        return new Preferences(nickname, input.chatEnabled());
    }

    public List<PresentSeat> present(long reader, String slug) {
        var venue = libraries.venue(slug);
        var source = occupant(reader, venue.shopId());
        return db.sql("""
                select s.id, s.label, s.area, s.near_window, s.power_outlet, s.floor, s.map_row, s.map_column,
                    r.id as reservation_id, r.id is not null as available,
                    case when r.id is not null then coalesce(p.nickname, '同馆读者') end as nickname,
                    r.id is not null and coalesce(p.chat_enabled, true) as chat_enabled,
                    r.starts_at, r.ends_at,
                    r.id is not null and r.starts_at <= :now and :source_start <= :now as reminders_enabled
                from library_seat s left join seat_reservation r on r.seat_id = s.id and r.reader_id <> :reader
                    and r.status in ('RESERVED', 'ACTIVE') and r.ends_at > :now
                    and not exists(select 1 from reader_block b
                        where (b.blocker_id = :reader and b.blocked_id = r.reader_id)
                           or (b.blocker_id = r.reader_id and b.blocked_id = :reader))
                left join reader_social_preferences p on p.reader_id = r.reader_id
                where s.shop_id = :shop order by r.starts_at nulls last, s.area, s.label
                """).param("reader", reader).param("now", lifecycle.now()).param("source_start", source.startsAt()).param("shop", venue.shopId())
                .query(PresentSeat.class).list();
    }

    private Occupant occupant(long reader, long shop) {
        return db.sql("""
                select id, reader_id, seat_id, shop_id, starts_at, ends_at from seat_reservation
                where reader_id = ? and shop_id = ? and status in ('RESERVED', 'ACTIVE') and ends_at > ?
                """).params(reader, shop, lifecycle.now()).query(Occupant.class).optional()
                .orElseThrow(() -> ApiException.conflict("RESERVATION_REQUIRED", "请先预约本场所座位，预约成功即可选择聊天对象。"));
    }

    private Occupant recipient(long reader, String slug, Target input) {
        long shop = libraries.venue(slug).shopId();
        occupant(reader, shop);
        if (input.reservationId() == null) {
            throw ApiException.conflict("READER_CHANGED", "请选择读者并重新提交。");
        }
        Occupant target = db.sql("""
                select id, reader_id, seat_id, shop_id, starts_at, ends_at from seat_reservation
                where id = ? and seat_id = ? and shop_id = ? and status in ('RESERVED', 'ACTIVE') and ends_at > ?
                """).params(input.reservationId(), input.seatId(), shop, lifecycle.now()).query(Occupant.class).optional()
                .orElseThrow(() -> ApiException.conflict("READER_CHANGED", "对方预约已变化，请刷新后重新选择。"));
        if (reader == target.readerId() || blocked(reader, target.readerId())) {
            throw ApiException.conflict("CONTACT_UNAVAILABLE", "暂时无法向该使用者发送消息。");
        }
        return target;
    }

    private boolean blocked(long first, long second) {
        return db.sql("""
                select exists(select 1 from reader_block where (blocker_id = ? and blocked_id = ?)
                    or (blocker_id = ? and blocked_id = ?))
                """).params(first, second, second, first).query(Boolean.class).single();
    }

    @Transactional
    public Map<String, String> remind(long reader, String slug, Target input) {
        if (input.kind() == null || !REMINDERS.containsKey(input.kind())) {
            throw ApiException.invalid("请选择可用的礼貌提醒。");
        }
        lifecycle.lock();
        Occupant target = recipient(reader, slug, input);
        var source = occupant(reader, target.shopId());
        if (source.startsAt().isAfter(lifecycle.now()) || target.startsAt().isAfter(lifecycle.now())) {
            throw ApiException.conflict("RESERVATION_NOT_STARTED", "安静提醒仅在双方预约时段内发送，聊天邀请可提前发起。");
        }
        boolean recent = db.sql("select exists(select 1 from quiet_reminder where reservation_id = ? and created_at > ?)")
                .params(target.id(), lifecycle.now().minusMinutes(30)).query(Boolean.class).single();
        if (!recent) {
            int count = db.sql("select count(*) from quiet_reminder where sender_id = ? and created_at > ?")
                    .params(reader, lifecycle.now().minusHours(1)).query(Integer.class).single();
            if (count >= 10) throw new ApiException(429, "TOO_MANY_REMINDERS", "提醒发送较频繁，请稍后再试。");
            db.sql("""
                    insert into quiet_reminder(id, sender_id, recipient_id, reservation_id, kind, created_at)
                    values (?, ?, ?, ?, ?, ?)
                    """).params(UUID.randomUUID(), reader, target.readerId(), target.id(), input.kind(), lifecycle.now()).update();
        }
        return Map.of("message", recent ? "近期已有人提醒，该提醒已合并。" : "提醒已发送，发送者信息已隐藏。");
    }

    public List<Notice> notices(long reader) {
        return db.sql("""
                select id, kind, created_at, acknowledged_at is not null as acknowledged
                from quiet_reminder where recipient_id = ? order by created_at desc, id limit 50
                """).param(reader).query(Notice.class).list();
    }

    public List<SentNotice> sentNotices(long reader) {
        return db.sql("""
                select q.id, q.kind, r.seat_label, q.created_at, q.acknowledged_at is not null as acknowledged
                from quiet_reminder q join seat_reservation r on r.id = q.reservation_id
                where q.sender_id = ? order by q.created_at desc, q.id limit 50
                """).param(reader).query(SentNotice.class).list();
    }

    @Transactional
    public void acknowledge(long reader, UUID id) {
        if (db.sql("update quiet_reminder set acknowledged_at = ? where id = ? and recipient_id = ?")
                .params(lifecycle.now(), id, reader).update() == 0) throw ApiException.missing("提醒不存在。");
    }

    @Transactional
    public Chat invite(long reader, String slug, Target input) {
        lifecycle.lock();
        var target = recipient(reader, slug, input);
        var source = occupant(reader, target.shopId());
        var prior = db.sql("""
                select id from reader_chat where (sender_reservation = ? and recipient_id = ?)
                    or (sender_id = ? and recipient_reservation = ? and status in ('PENDING', 'ACCEPTED'))
                order by created_at desc limit 1
                """).params(source.id(), target.readerId(), target.readerId(), source.id()).query(UUID.class).optional();
        if (prior.isPresent()) {
            var chat = thread(reader, prior.get());
            if (!List.of("PENDING", "ACCEPTED").contains(chat.status())) {
                throw ApiException.conflict("INVITATION_FINISHED", "本次学习的邀请已结束，请尊重对方的选择。");
            }
            return chats(reader).stream().filter(row -> row.id().equals(prior.get())).findFirst().orElseThrow();
        }
        if (!preferences(target.readerId()).chatEnabled()) {
            throw ApiException.conflict("CHAT_DISABLED", "对方正在专注学习，暂不接受聊天邀请。");
        }
        int count = db.sql("select count(*) from reader_chat where sender_id = ? and created_at > ?")
                .params(reader, lifecycle.now().minusHours(1)).query(Integer.class).single();
        if (count >= 5) throw new ApiException(429, "TOO_MANY_INVITATIONS", "邀请发送较频繁，请稍后再试。");
        UUID id = UUID.randomUUID();
        OffsetDateTime expires = source.endsAt().isBefore(target.endsAt()) ? source.endsAt() : target.endsAt();
        db.sql("""
                insert into reader_chat(id, sender_id, recipient_id, sender_reservation, recipient_reservation, status, created_at, expires_at)
                values (?, ?, ?, ?, ?, 'PENDING', ?, ?)
                """).params(id, reader, target.readerId(), source.id(), target.id(), lifecycle.now(), expires).update();
        return chats(reader).stream().filter(row -> row.id().equals(id)).findFirst().orElseThrow();
    }

    @Transactional
    public List<Chat> chats(long reader) {
        lifecycle.lock();
        return db.sql("""
                select c.id, c.status, c.resolution_reason, c.recipient_id = :reader as incoming,
                    case when c.status in ('ACCEPTED', 'CLOSED') then coalesce(p.nickname, '同馆读者')
                    else '同馆读者' end as nickname,
                    (select count(*) from reader_chat_message m where m.chat_id = c.id and m.sender_id <> :reader
                        and m.id > case when c.sender_id = :reader then c.sender_seen else c.recipient_seen end) as unread
                from reader_chat c left join reader_social_preferences p on
                    p.reader_id = case when c.sender_id = :reader then c.recipient_id else c.sender_id end
                where c.sender_id = :reader or c.recipient_id = :reader order by c.created_at desc, c.id limit 100
                """).param("reader", reader).query(Chat.class).list();
    }

    private ThreadRow thread(long reader, UUID id) {
        return db.sql("""
                select id, sender_id, recipient_id, sender_reservation, recipient_reservation, status from reader_chat
                where id = ? and (sender_id = ? or recipient_id = ?)
                """).params(id, reader, reader).query(ThreadRow.class).optional()
                .orElseThrow(() -> ApiException.missing("会话不存在。"));
    }

    @Transactional
    public void action(long reader, UUID id, String action) {
        lifecycle.lock();
        var chat = thread(reader, id);
        switch (action) {
            case "accept", "decline" -> {
                if (chat.recipientId() != reader || !"PENDING".equals(chat.status())) {
                    throw ApiException.conflict("INVITATION_FINISHED", "邀请状态已变化，请刷新。");
                }
                if (blocked(chat.senderId(), chat.recipientId())) throw ApiException.conflict("CHAT_CLOSED", "会话已结束。");
                if (action.equals("accept")) {
                    updateOutcome(id, "ACCEPTED", null, null);
                } else {
                    updateOutcome(id, "DECLINED", "RECIPIENT_DECLINED", reader);
                }
            }
            case "withdraw" -> {
                if (chat.senderId() != reader || !"PENDING".equals(chat.status())) {
                    throw ApiException.conflict("INVITATION_FINISHED", "邀请状态已变化，请刷新。");
                }
                updateOutcome(id, "WITHDRAWN", "SENDER_WITHDREW", reader);
            }
            case "close" -> {
                if ("PENDING".equals(chat.status())) {
                    throw ApiException.conflict("INVITATION_PENDING", "待处理邀请请由接收方接受或拒绝，发起方可撤回。");
                }
                if (List.of("ACCEPTED", "CLOSED").contains(chat.status())) {
                    updateOutcome(id, "CLOSED", "CONVERSATION_ENDED", reader);
                }
            }
            case "block" -> {
                block(reader, reader == chat.senderId() ? chat.recipientId() : chat.senderId());
                return;
            }
            default -> throw ApiException.invalid("操作不可用。");
        }
    }

    private void updateOutcome(UUID id, String status, String reason, Long actor) {
        db.sql("update reader_chat set status = ?, resolution_reason = ?, resolved_at = ?, resolved_by_reader_id = ? where id = ?")
                .params(status, reason, reason == null ? null : lifecycle.now(), actor, id).update();
    }

    @Transactional
    public void blockNotice(long reader, UUID id) {
        lifecycle.lock();
        long sender = db.sql("select sender_id from quiet_reminder where id = ? and recipient_id = ?")
                .params(id, reader).query(Long.class).optional().orElseThrow(() -> ApiException.missing("提醒不存在。"));
        block(reader, sender);
    }

    private void block(long reader, long other) {
        db.sql("insert into reader_block(blocker_id, blocked_id) values (?, ?) on conflict do nothing")
                .params(reader, other).update();
        db.sql("""
                update reader_chat set
                    status = case when status = 'PENDING' then 'BLOCKED' when status in ('ACCEPTED', 'CLOSED') then 'CLOSED' else status end,
                    resolution_reason = case when status = 'PENDING' then 'BLOCKED' when status in ('ACCEPTED', 'CLOSED') then 'BLOCKED' else resolution_reason end,
                    resolved_at = case when status in ('PENDING', 'ACCEPTED', 'CLOSED') then ? else resolved_at end,
                    resolved_by_reader_id = case when status in ('PENDING', 'ACCEPTED', 'CLOSED') then ? else resolved_by_reader_id end
                where (sender_id = ? and recipient_id = ?)
                    or (sender_id = ? and recipient_id = ?)
                """).params(lifecycle.now(), reader, reader, other, other, reader).update();
    }

    public List<Message> messages(long reader, UUID chat) {
        var row = thread(reader, chat);
        if (!List.of("ACCEPTED", "CLOSED").contains(row.status())) {
            throw ApiException.conflict("CONSENT_REQUIRED", "对方接受邀请后才能聊天。");
        }
        return db.sql("""
                select id, body, sender_id = ? as mine, created_at from (
                    select * from reader_chat_message where chat_id = ? order by id desc limit 100
                ) m order by id
                """).params(reader, chat).query(Message.class).list();
    }

    @Transactional
    public Message send(long reader, UUID id, Send input) {
        String body = Inputs.required(input.body(), 500, "消息");
        if (input.requestKey() == null) throw ApiException.invalid("请重新提交消息。");
        lifecycle.lock();
        var chat = thread(reader, id);
        var prior = db.sql("""
                select id, body, true as mine, created_at from reader_chat_message
                where chat_id = ? and sender_id = ? and request_key = ?
                """).params(id, reader, input.requestKey()).query(Message.class).optional();
        if (prior.isPresent()) {
            if (!prior.get().body().equals(body)) throw ApiException.conflict("REQUEST_KEY_REUSED", "消息已变化，请重新发送。");
            return prior.get();
        }
        if (!chat.status().equals("ACCEPTED") || blocked(chat.senderId(), chat.recipientId())) {
            throw ApiException.conflict("CONSENT_REQUIRED", "会话尚未接受或已经结束。");
        }
        int count = db.sql("select count(*) from reader_chat_message where sender_id = ? and created_at > ?")
                .params(reader, lifecycle.now().minusMinutes(1)).query(Integer.class).single();
        if (count >= 20) throw new ApiException(429, "MESSAGE_RATE_LIMIT", "发送较频繁，请稍后再试。");
        return db.sql("""
                insert into reader_chat_message(chat_id, sender_id, body, request_key, created_at)
                values (?, ?, ?, ?, ?) returning id, body, true as mine, created_at
                """).params(id, reader, body, input.requestKey(), lifecycle.now()).query(Message.class).single();
    }

    @Transactional
    public void seen(long reader, UUID id, long through) {
        lifecycle.lock();
        var chat = thread(reader, id);
        String column = chat.senderId() == reader ? "sender_seen" : "recipient_seen";
        db.sql("update reader_chat set " + column + " = greatest(" + column
                + ", coalesce((select max(id) from reader_chat_message where chat_id = ? and id <= ?), 0)) where id = ?")
                .params(id, through, id).update();
    }
}
