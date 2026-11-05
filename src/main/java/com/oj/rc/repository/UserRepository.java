package com.oj.rc.repository;

import com.oj.rc.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Used for both normal users and admins, because they share the same table. */
public interface UserRepository extends JpaRepository<User, Integer> {

    // "First" because the table has no unique constraint on username
    Optional<User> findFirstByUsername(String username);

    Optional<User> findFirstByUsernameAndTeamname(String username, String teamname);

    boolean existsByUsername(String username);
}
