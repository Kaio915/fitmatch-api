package fitmatch_api.repository;

import fitmatch_api.model.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    @Query("SELECT m FROM ChatMessage m WHERE " +
           "(m.senderId = :userId1 AND m.receiverId = :userId2) OR " +
           "(m.senderId = :userId2 AND m.receiverId = :userId1) " +
           "ORDER BY m.sentAt ASC")
    List<ChatMessage> findConversation(
            @Param("userId1") Long userId1,
            @Param("userId2") Long userId2
    );

    @Query("SELECT m FROM ChatMessage m WHERE " +
           "(m.senderId = :userId1 AND m.receiverId = :userId2) OR " +
           "(m.senderId = :userId2 AND m.receiverId = :userId1) " +
           "ORDER BY m.sentAt DESC")
    List<ChatMessage> findConversationLatest(
            @Param("userId1") Long userId1,
            @Param("userId2") Long userId2,
            Pageable pageable
    );

    @Query("SELECT COUNT(m) > 0 FROM ChatMessage m WHERE " +
           "((m.senderId = :userId1 AND m.receiverId = :userId2) OR " +
           "(m.senderId = :userId2 AND m.receiverId = :userId1)) AND " +
           "m.termination = true")
    boolean hasTerminationMessage(
            @Param("userId1") Long userId1,
            @Param("userId2") Long userId2
    );

    @Query("SELECT COUNT(m) > 0 FROM ChatMessage m WHERE " +
           "((m.senderId = :userId1 AND m.receiverId = :userId2) OR " +
           "(m.senderId = :userId2 AND m.receiverId = :userId1)) AND " +
           "m.sentAt >= :since AND " +
           "m.termination = true")
    boolean hasTerminationMessageSince(
            @Param("userId1") Long userId1,
            @Param("userId2") Long userId2,
            @Param("since") LocalDateTime since
    );
}
