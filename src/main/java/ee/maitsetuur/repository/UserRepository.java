package ee.maitsetuur.repository;

import ee.maitsetuur.model.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    Optional<User> findByActivationCode(String code);

    List<User> findUsersByRestaurantIsNotNull();
}
