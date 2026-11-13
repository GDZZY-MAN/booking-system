package cn.shopbooking.web;

import java.nio.charset.StandardCharsets;

public final class Inputs {
    private Inputs() {}

    public static String required(String value, int max, String label) {
        String text = value == null ? "" : value.strip();
        if (text.isEmpty() || text.length() > max || text.chars().anyMatch(Character::isISOControl)) {
            throw ApiException.invalid(label + "须填写，且不超过 " + max + " 个字符。");
        }
        return text;
    }

    public static String password(String password) {
        if (password == null || password.length() < 8
                || password.getBytes(StandardCharsets.UTF_8).length > 72
                || password.chars().anyMatch(Character::isISOControl)) {
            throw ApiException.invalid("密码至少 8 个字符，最多 72 字节。");
        }
        return password;
    }
}
