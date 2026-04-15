package fitmatch_api.service;

import fitmatch_api.repository.TrainerSlotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TrainerSlotService {

    private final TrainerSlotRepository slotRepo;

    public TrainerSlotService(TrainerSlotRepository slotRepo) {
        this.slotRepo = slotRepo;
    }

    @Transactional
    public void unblockSlot(Long trainerId, String dayName, String time) {
        slotRepo.deleteByTrainerIdAndDayNameAndTime(trainerId, dayName, time);
    }
}