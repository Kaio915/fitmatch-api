package fitmatch_api.controller;

import fitmatch_api.model.StudentRating;
import fitmatch_api.repository.StudentRatingRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/student-ratings")
public class StudentRatingController {

    private final StudentRatingRepository ratingRepo;

    public StudentRatingController(StudentRatingRepository ratingRepo) {
        this.ratingRepo = ratingRepo;
    }

    // Personal avalia aluno (upsert – se já avaliou, atualiza)
    @PostMapping
    public StudentRating rate(@RequestBody RatingDto dto) {
        if (dto.trainerId() == null || dto.studentId() == null || dto.stars() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "trainerId, studentId e stars são obrigatórios");
        }
        if (dto.stars() < 1 || dto.stars() > 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "stars deve estar entre 1 e 5");
        }
        StudentRating rating = ratingRepo
                .findByTrainerIdAndStudentId(dto.trainerId(), dto.studentId())
                .orElseGet(StudentRating::new);
        rating.setTrainerId(dto.trainerId());
        rating.setStudentId(dto.studentId());
        rating.setTrainerName(dto.trainerName() != null ? dto.trainerName() : "Personal");
        rating.setStars(dto.stars());
        rating.setComment(dto.comment());
        return ratingRepo.save(rating);
    }

    // Retorna todas as avaliações de um aluno
    @GetMapping("/student/{studentId}")
    public List<StudentRating> getRatings(@PathVariable Long studentId) {
        return ratingRepo.findByStudentIdOrderByCreatedAtDesc(studentId);
    }

    record RatingDto(Long trainerId, Long studentId, String trainerName, Integer stars, String comment) {}
}
