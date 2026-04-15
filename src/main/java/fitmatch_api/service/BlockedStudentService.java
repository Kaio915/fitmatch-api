package fitmatch_api.service;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BlockedStudentService {

    private final JdbcTemplate jdbcTemplate;

    public BlockedStudentService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void unblockStudent(Long trainerId, Long studentId) {
        try {
            jdbcTemplate.update(
                    "DELETE FROM blocked_students WHERE trainer_id = ? AND student_id = ?",
                    trainerId,
                    studentId
            );
        } catch (DataAccessException ex) {
            jdbcTemplate.update(
                    "DELETE FROM blocked_students WHERE trainerId = ? AND studentId = ?",
                    trainerId,
                    studentId
            );
        }
    }
}