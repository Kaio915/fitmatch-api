package fitmatch_api.controller;

import fitmatch_api.model.TrainerRating;
import fitmatch_api.repository.TrainerRatingRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/ratings")
public class TrainerRatingController {

    private final TrainerRatingRepository ratingRepo;

    public TrainerRatingController(TrainerRatingRepository ratingRepo) {
        this.ratingRepo = ratingRepo;
    }

    // Aluno avalia personal (upsert – se já avaliou, atualiza)
    @PostMapping
    public TrainerRating rate(@RequestBody RatingDto dto) {
        if (dto.trainerId() == null || dto.studentId() == null || dto.stars() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "trainerId, studentId e stars são obrigatórios");
        }
        if (dto.stars() < 1 || dto.stars() > 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "stars deve estar entre 1 e 5");
        }
        TrainerRating rating = ratingRepo
                .findByTrainerIdAndStudentId(dto.trainerId(), dto.studentId())
                .orElseGet(TrainerRating::new);
        rating.setTrainerId(dto.trainerId());
        rating.setStudentId(dto.studentId());
        rating.setStudentName(dto.studentName() != null ? dto.studentName() : "Aluno");
        rating.setStars(dto.stars());
        rating.setComment(dto.comment());
        return ratingRepo.save(rating);
    }

    // Retorna todas as avaliações de um personal
    @GetMapping("/trainer/{trainerId}")
    public List<TrainerRating> getRatings(@PathVariable Long trainerId) {
        return ratingRepo.findByTrainerIdOrderByCreatedAtDesc(trainerId);
    }

    record RatingDto(Long trainerId, Long studentId, String studentName, Integer stars, String comment) {}
}
