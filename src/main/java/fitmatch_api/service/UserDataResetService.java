package fitmatch_api.service;

import fitmatch_api.model.DietSavedMeal;
import fitmatch_api.model.UserType;
import fitmatch_api.repository.BlockedStudentRepository;
import fitmatch_api.repository.ChatMessageRepository;
import fitmatch_api.repository.DietEntryRepository;
import fitmatch_api.repository.DietFoodRepository;
import fitmatch_api.repository.DietGoalRepository;
import fitmatch_api.repository.DietSavedMealRepository;
import fitmatch_api.repository.ReportRepository;
import fitmatch_api.repository.StudentRatingRepository;
import fitmatch_api.repository.StudentRequestRepository;
import fitmatch_api.repository.StudentTrainerConnectionRepository;
import fitmatch_api.repository.StudentWorkoutPlanRepository;
import fitmatch_api.repository.TrainerRatingRepository;
import fitmatch_api.repository.TrainerSlotRepository;
import fitmatch_api.repository.WorkoutCustomExerciseRepository;
import fitmatch_api.repository.WorkoutFavoriteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Apaga todos os dados associados a um usuário que foi banido (e, se desbanido,
 * volta como uma conta "nova"): solicitações, conexões, treinos, chats,
 * avaliações, denúncias, dieta, horários e bloqueios.
 */
@Service
public class UserDataResetService {

    private final StudentRequestRepository requestRepo;
    private final StudentTrainerConnectionRepository connectionRepo;
    private final TrainerSlotRepository slotRepo;
    private final StudentWorkoutPlanRepository workoutPlanRepo;
    private final TrainerRatingRepository trainerRatingRepo;
    private final StudentRatingRepository studentRatingRepo;
    private final WorkoutFavoriteRepository favoriteRepo;
    private final WorkoutCustomExerciseRepository customExerciseRepo;
    private final BlockedStudentRepository blockedStudentRepo;
    private final ChatMessageRepository chatMessageRepo;
    private final ReportRepository reportRepo;
    private final DietEntryRepository dietEntryRepo;
    private final DietGoalRepository dietGoalRepo;
    private final DietSavedMealRepository dietSavedMealRepo;
    private final DietFoodRepository dietFoodRepo;

    public UserDataResetService(
            StudentRequestRepository requestRepo,
            StudentTrainerConnectionRepository connectionRepo,
            TrainerSlotRepository slotRepo,
            StudentWorkoutPlanRepository workoutPlanRepo,
            TrainerRatingRepository trainerRatingRepo,
            StudentRatingRepository studentRatingRepo,
            WorkoutFavoriteRepository favoriteRepo,
            WorkoutCustomExerciseRepository customExerciseRepo,
            BlockedStudentRepository blockedStudentRepo,
            ChatMessageRepository chatMessageRepo,
            ReportRepository reportRepo,
            DietEntryRepository dietEntryRepo,
            DietGoalRepository dietGoalRepo,
            DietSavedMealRepository dietSavedMealRepo,
            DietFoodRepository dietFoodRepo
    ) {
        this.requestRepo = requestRepo;
        this.connectionRepo = connectionRepo;
        this.slotRepo = slotRepo;
        this.workoutPlanRepo = workoutPlanRepo;
        this.trainerRatingRepo = trainerRatingRepo;
        this.studentRatingRepo = studentRatingRepo;
        this.favoriteRepo = favoriteRepo;
        this.customExerciseRepo = customExerciseRepo;
        this.blockedStudentRepo = blockedStudentRepo;
        this.chatMessageRepo = chatMessageRepo;
        this.reportRepo = reportRepo;
        this.dietEntryRepo = dietEntryRepo;
        this.dietGoalRepo = dietGoalRepo;
        this.dietSavedMealRepo = dietSavedMealRepo;
        this.dietFoodRepo = dietFoodRepo;
    }

    @Transactional
    public void resetUserData(Long userId, UserType type) {
        if (userId == null) {
            return;
        }

        // Conversas e denúncias (em qualquer direção), independente do tipo.
        chatMessageRepo.deleteAllMessagesInvolvingUser(userId);
        reportRepo.deleteByReporterId(userId);
        reportRepo.deleteByReportedUserId(userId);

        if (type == UserType.personal) {
            requestRepo.deleteByTrainerId(userId);
            connectionRepo.deleteByTrainerId(userId);
            slotRepo.deleteByTrainerId(userId);
            workoutPlanRepo.deleteByTrainerId(userId);
            trainerRatingRepo.deleteByTrainerId(userId);
            studentRatingRepo.deleteByTrainerId(userId);
            favoriteRepo.deleteByTrainerId(userId);
            customExerciseRepo.deleteByTrainerId(userId);
            blockedStudentRepo.deleteByTrainerId(userId);
        } else {
            requestRepo.deleteByStudentId(userId);
            connectionRepo.deleteByStudentId(userId);
            workoutPlanRepo.deleteByStudentId(userId);
            trainerRatingRepo.deleteByStudentId(userId);
            studentRatingRepo.deleteByStudentId(userId);
            blockedStudentRepo.deleteByStudentId(userId);

            // Dieta do aluno. As refeições salvas possuem itens em cascata
            // (orphanRemoval), então são removidas via deleteAll para cascatear.
            List<DietSavedMeal> meals = dietSavedMealRepo.findByUserIdOrderByMealTypeAsc(userId);
            if (!meals.isEmpty()) {
                dietSavedMealRepo.deleteAll(meals);
            }
            dietEntryRepo.deleteByUserId(userId);
            dietGoalRepo.deleteByUserId(userId);
            dietFoodRepo.deleteByUserId(userId);
        }
    }
}
