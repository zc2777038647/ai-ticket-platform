package com.xiaoyang.aiticketplatform.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xiaoyang.aiticketplatform.entity.UserAccount;
import com.xiaoyang.aiticketplatform.enums.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class UserAccountMapperIntegrationTest {

    private static final String TEST_HASH_PREFIX = "{test-hash}sha256:";

    @Autowired
    private UserAccountMapper userAccountMapper;

    private String usernamePrefix;

    @AfterTransaction
    void verifyPreparedUsersWereRolledBack() {
        if (usernamePrefix != null) {
            assertEquals(0L, userAccountMapper.selectCount(
                    new LambdaQueryWrapper<UserAccount>()
                            .likeRight(UserAccount::getUsername, usernamePrefix)
            ));
        }
    }

    @Test
    void shouldInsertAndSelectUserAccountWithDatabaseGeneratedValues() {
        usernamePrefix = "user-" + UUID.randomUUID();
        String testHash = TEST_HASH_PREFIX + UUID.randomUUID();

        UserAccount userAccount = newUserAccount(
                usernamePrefix,
                testHash,
                "用户持久化测试",
                UserRole.USER
        );

        int affectedRows = userAccountMapper.insert(userAccount);

        assertEquals(1, affectedRows);
        assertNotNull(userAccount.getId());
        assertTrue(userAccount.getId() > 0);

        UserAccount persistedUser = userAccountMapper.selectById(userAccount.getId());

        assertNotNull(persistedUser);
        assertAll(
                () -> assertEquals(usernamePrefix, persistedUser.getUsername()),
                () -> assertTrue(testHash.equals(persistedUser.getPasswordHash()),
                        "passwordHash 应正确映射"),
                () -> assertEquals("用户持久化测试", persistedUser.getDisplayName()),
                () -> assertEquals(UserRole.USER, persistedUser.getRole()),
                () -> assertNotNull(persistedUser.getCreatedAt()),
                () -> assertNotNull(persistedUser.getUpdatedAt())
        );
    }

    @Test
    void shouldRejectDuplicateUsernameThroughDatabaseUniqueConstraint() {
        usernamePrefix = "user-" + UUID.randomUUID();
        UserAccount firstUser = newUserAccount(
                usernamePrefix,
                TEST_HASH_PREFIX + UUID.randomUUID(),
                "用户一",
                UserRole.USER
        );
        UserAccount secondUser = newUserAccount(
                usernamePrefix,
                TEST_HASH_PREFIX + UUID.randomUUID(),
                "用户二",
                UserRole.AGENT
        );

        assertEquals(1, userAccountMapper.insert(firstUser));
        assertNotNull(firstUser.getId());

        assertThrows(DuplicateKeyException.class,
                () -> userAccountMapper.insert(secondUser));
    }

    private static UserAccount newUserAccount(
            String username,
            String passwordHash,
            String displayName,
            UserRole role
    ) {
        UserAccount userAccount = new UserAccount();
        userAccount.setUsername(username);
        userAccount.setPasswordHash(passwordHash);
        userAccount.setDisplayName(displayName);
        userAccount.setRole(role);
        return userAccount;
    }
}
