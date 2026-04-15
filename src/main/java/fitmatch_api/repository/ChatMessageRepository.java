package fitmatch_api.repository;

import fitmatch_api.model.ChatMessage;
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

    @Query("SELECT COUNT(m) > 0 FROM ChatMessage m WHERE " +
           "((m.senderId = :userId1 AND m.receiverId = :userId2) OR " +
           "(m.senderId = :userId2 AND m.receiverId = :userId1)) AND " +
            "(" +
            "LOWER(m.text) LIKE '%sua solicitação foi recusada%' OR " +
            "LOWER(m.text) LIKE '%não faz mais parte de meus alunos%' OR " +
            "LOWER(m.text) LIKE '%nao faz mais parte de meus alunos%' OR " +
            "LOWER(m.text) LIKE '%removeu você de meus alunos%' OR " +
            "LOWER(m.text) LIKE '%removeu voce de meus alunos%' OR " +
            "LOWER(m.text) LIKE '%este chat ficará somente para leitura%' OR " +
            "LOWER(m.text) LIKE '%chat está disponível apenas para leitura%' OR " +
            "LOWER(m.text) LIKE '%somente para leitura%' OR " +
            "LOWER(m.text) LIKE '%disponível apenas para leitura%'" +
            ")")
    boolean hasTerminationMessage(
            @Param("userId1") Long userId1,
            @Param("userId2") Long userId2
    );

    @Query("SELECT COUNT(m) > 0 FROM ChatMessage m WHERE " +
           "((m.senderId = :userId1 AND m.receiverId = :userId2) OR " +
           "(m.senderId = :userId2 AND m.receiverId = :userId1)) AND " +
           "m.sentAt >= :since AND " +
            "(" +
            "LOWER(m.text) LIKE '%sua solicitação foi recusada%' OR " +
            "LOWER(m.text) LIKE '%não faz mais parte de meus alunos%' OR " +
            "LOWER(m.text) LIKE '%nao faz mais parte de meus alunos%' OR " +
            "LOWER(m.text) LIKE '%removeu você de meus alunos%' OR " +
            "LOWER(m.text) LIKE '%removeu voce de meus alunos%' OR " +
            "LOWER(m.text) LIKE '%este chat ficará somente para leitura%' OR " +
            "LOWER(m.text) LIKE '%chat está disponível apenas para leitura%' OR " +
            "LOWER(m.text) LIKE '%somente para leitura%' OR " +
            "LOWER(m.text) LIKE '%disponível apenas para leitura%'" +
            ")")
    boolean hasTerminationMessageSince(
            @Param("userId1") Long userId1,
            @Param("userId2") Long userId2,
            @Param("since") LocalDateTime since
    );
}
