package fitmatch_api.repository;

import fitmatch_api.model.UserHistory;
import fitmatch_api.model.UserType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserHistoryRepository extends JpaRepository<UserHistory, Long> {

    List<UserHistory> findByTypeOrderByRecordedAtDesc(UserType type);

    List<UserHistory> findByTypeAndStatusOrderByRecordedAtDesc(UserType type, String status);

    boolean existsByUserId(Long userId);
}
