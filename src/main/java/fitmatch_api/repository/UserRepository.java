package fitmatch_api.repository;

import fitmatch_api.model.User;
import fitmatch_api.model.UserStatus;
import fitmatch_api.model.UserType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByCpf(String cpf);

    List<User> findByCref(String cref);

    // Pendentes (status = PENDING)
    List<User> findByTypeAndStatus(UserType type, UserStatus status);

    // Pendentes (status = PENDING ou TEMPORARILY_REJECTED), ordenados por data de criação
    List<User> findByTypeAndStatusInOrderByCreatedAtDesc(UserType type, List<UserStatus> statuses);

    // Histórico: approved + rejected (status IN (...))
    List<User> findByTypeAndStatusIn(UserType type, List<UserStatus> statuses);

    List<User> findByStatus(UserStatus status);
}