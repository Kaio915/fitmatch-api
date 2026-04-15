package fitmatch_api.repository;

import fitmatch_api.model.StudentWorkoutPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StudentWorkoutPlanRepository extends JpaRepository<StudentWorkoutPlan, Long> {
    List<StudentWorkoutPlan> findByTrainerIdAndStudentId(Long trainerId, Long studentId);

    Optional<StudentWorkoutPlan> findByTrainerIdAndStudentIdAndDayName(Long trainerId, Long studentId, String dayName);

    Optional<StudentWorkoutPlan> findByIdAndTrainerIdAndStudentId(Long id, Long trainerId, Long studentId);
}
