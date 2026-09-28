package fitmatch_api.repository;

import fitmatch_api.model.StudentWorkoutPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface StudentWorkoutPlanRepository extends JpaRepository<StudentWorkoutPlan, Long> {
    List<StudentWorkoutPlan> findByTrainerIdAndStudentId(Long trainerId, Long studentId);

    Optional<StudentWorkoutPlan> findByTrainerIdAndStudentIdAndDayName(Long trainerId, Long studentId, String dayName);

    Optional<StudentWorkoutPlan> findByIdAndTrainerIdAndStudentId(Long id, Long trainerId, Long studentId);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from StudentWorkoutPlan p where p.trainerId = :trainerId")
    int deleteByTrainerId(@Param("trainerId") Long trainerId);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from StudentWorkoutPlan p where p.studentId = :studentId")
    int deleteByStudentId(@Param("studentId") Long studentId);
}
