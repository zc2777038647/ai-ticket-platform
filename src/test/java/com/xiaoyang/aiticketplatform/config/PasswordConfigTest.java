package com.xiaoyang.aiticketplatform.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordConfigTest {

    @Test
    void shouldCreateBcryptPasswordEncoder() {
        PasswordEncoder passwordEncoder = new PasswordConfig().passwordEncoder();

        assertInstanceOf(BCryptPasswordEncoder.class, passwordEncoder);
    }

    @Test
    void shouldEncodeAndMatchPassword() {
        PasswordEncoder passwordEncoder = new PasswordConfig().passwordEncoder();
        String rawPassword = testPassword();
        String encodedPassword = passwordEncoder.encode(rawPassword);

        assertFalse(rawPassword.equals(encodedPassword), "编码结果不得等于原始密码");
        assertTrue(passwordEncoder.matches(rawPassword, encodedPassword));
        assertFalse(passwordEncoder.matches("different-test-value", encodedPassword));
    }

    @Test
    void shouldUseRandomSaltForEachEncoding() {
        PasswordEncoder passwordEncoder = new PasswordConfig().passwordEncoder();
        String rawPassword = testPassword();
        String firstEncoding = passwordEncoder.encode(rawPassword);
        String secondEncoding = passwordEncoder.encode(rawPassword);

        assertFalse(firstEncoding.equals(secondEncoding), "两次 BCrypt 编码通常应产生不同结果");
        assertTrue(passwordEncoder.matches(rawPassword, firstEncoding));
        assertTrue(passwordEncoder.matches(rawPassword, secondEncoding));
    }

    private static String testPassword() {
        return "P5_2_test_secret_value";
    }
}
