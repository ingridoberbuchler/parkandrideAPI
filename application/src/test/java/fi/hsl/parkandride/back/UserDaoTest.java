// Copyright © 2015 HSL <https://www.hsl.fi>
// This program is dual-licensed under the EUPL v1.2 and AGPLv3 licenses.

package fi.hsl.parkandride.back;

import fi.hsl.parkandride.core.back.UserRepository;
import fi.hsl.parkandride.core.domain.SearchResults;
import fi.hsl.parkandride.core.domain.User;
import fi.hsl.parkandride.core.domain.UserSearch;
import fi.hsl.parkandride.core.domain.UserSecret;
import org.joda.time.DateTime;
import org.junit.Test;

import javax.inject.Inject;

import static fi.hsl.parkandride.core.domain.Role.ADMIN;
import static org.assertj.core.api.Assertions.assertThat;

public class UserDaoTest extends AbstractDaoTest {

    @Inject
    UserRepository userRepository;

    @Test
    public void create_read_and_update() throws InterruptedException {
        DateTime referenceTime = userRepository.getCurrentTime();
        final UserSecret newUser = new UserSecret(0, "user", "user-password", ADMIN);
        Thread.sleep(1);
        long userId = userRepository.insertUser(newUser);
        newUser.user.id = userId;
        assertThat(userId).isGreaterThan(0);

        UserSecret userSecret = userRepository.getUser(userId);

        assertThat(userSecret.minTokenTimestamp.getMillis()).isGreaterThan(referenceTime.getMillis());
        assertEquals(userSecret, newUser);
        assertEquals(userRepository.getUser("user"), newUser);

        referenceTime = userSecret.minTokenTimestamp;
        Thread.sleep(1);
        userRepository.revokeTokens(userId, userRepository.getCurrentTime());
        userSecret = userRepository.getUser(userId);
        assertThat(userSecret.minTokenTimestamp.getMillis()).isGreaterThan(referenceTime.getMillis());

        referenceTime = userSecret.minTokenTimestamp;
        Thread.sleep(1);
        userRepository.updatePassword(userId, "new-password");
        userSecret = userRepository.getUser(userId);
        assertThat(userSecret.minTokenTimestamp.getMillis()).isGreaterThan(referenceTime.getMillis());
        assertThat(userSecret.password).isEqualTo("new-password");

        SearchResults<User> users = userRepository.findUsers(new UserSearch());
        assertThat(users.size()).isEqualTo(1);
        assertEquals(users.get(0), newUser.user);
    }

    private void assertEquals(UserSecret actual, UserSecret expected) {
        assertThat(actual).isNotNull();
        assertThat(actual.minTokenTimestamp).isNotNull();
        assertThat(actual.password).isEqualTo(expected.password);
        assertEquals(actual.user, expected.user);
    }

    private void assertEquals(User actual, User expected) {
        assertThat(actual.id).isEqualTo(expected.id);
        assertThat(actual.username).isEqualTo(expected.username);
        assertThat(actual.role).isEqualTo(expected.role);
        assertThat(actual.operatorId).isEqualTo(expected.operatorId);
    }
}
