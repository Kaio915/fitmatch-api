package fitmatch_api.repository;

import fitmatch_api.model.TrainerSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface TrainerSlotRepository extends JpaRepository<TrainerSlot, Long> {

    List<TrainerSlot> findByTrainerId(Long trainerId);

    Optional<TrainerSlot> findByTrainerIdAndDayNameAndTime(Long trainerId, String dayName, String time);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from TrainerSlot s where s.trainerId = :trainerId and s.dayName = :dayName and s.time = :time")
    int deleteByTrainerIdAndDayNameAndTime(
            @Param("trainerId") Long trainerId,
            @Param("dayName") String dayName,
            @Param("time") String time
    );

        @Transactional
        @Modifying(clearAutomatically = true, flushAutomatically = true)
        @Query("delete from TrainerSlot s where s.trainerId = :trainerId and s.dayName = :dayName and s.time = :time and s.state = :state")
        int deleteByTrainerIdAndDayNameAndTimeAndState(
            @Param("trainerId") Long trainerId,
            @Param("dayName") String dayName,
            @Param("time") String time,
            @Param("state") String state
        );

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from TrainerSlot s where s.trainerId = :trainerId")
    int deleteByTrainerId(@Param("trainerId") Long trainerId);
}
