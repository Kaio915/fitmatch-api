package fitmatch_api.repository;

import fitmatch_api.model.StudentTrainerConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface StudentTrainerConnectionRepository extends JpaRepository<StudentTrainerConnection, Long> {
    List<StudentTrainerConnection> findByStudentId(Long studentId);
    List<StudentTrainerConnection> findByTrainerId(Long trainerId);
    Optional<StudentTrainerConnection> findByStudentIdAndTrainerId(Long studentId, Long trainerId);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from StudentTrainerConnection c where c.studentId = :studentId and c.trainerId = :trainerId")
    int deleteByStudentIdAndTrainerId(@Param("studentId") Long studentId, @Param("trainerId") Long trainerId);
}
