package cn.shopbooking.account;

import cn.shopbooking.account.Accounts.Credentials;
import cn.shopbooking.account.Accounts.Viewer;
import cn.shopbooking.web.ApiException;
import cn.shopbooking.web.Inputs;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class Readers {
    private record Login(long id, String username, String passwordHash) {}
    private final JdbcClient db;
    private final PasswordEncoder encoder;
    private final String dummyHash;

    public Readers(JdbcClient db, PasswordEncoder encoder) {
        this.db = db;
        this.encoder = encoder;
        this.dummyHash = encoder.encode("reader-placeholder-password");
    }

    public Viewer register(Credentials input) {
        String name = Accounts.username(input.username());
        String hash = encoder.encode(Inputs.password(input.password()));
        long id = db.sql("insert into reader_account(username, password_hash) values (?, ?) returning id")
                .params(name, hash).query(Long.class).single();
        return new Viewer(0, null, name, "READER", id);
    }

    public Viewer login(Credentials input) {
        String name = Accounts.username(input.username());
        String password = Inputs.password(input.password());
        var row = db.sql("select id, username, password_hash from reader_account where username = ?")
                .param(name).query(Login.class).optional();
        if (!encoder.matches(password, row.map(Login::passwordHash).orElse(dummyHash)) || row.isEmpty()) {
            throw new ApiException(401, "INVALID_CREDENTIALS", "读者账号或密码有误。");
        }
        return new Viewer(0, null, row.get().username(), "READER", row.get().id());
    }
}
