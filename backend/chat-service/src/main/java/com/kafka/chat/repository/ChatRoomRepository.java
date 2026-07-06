package com.kafka.chat.repository;

import com.kafka.chat.model.ChatRoom;
import com.kafka.chat.model.ChatRoomType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, String> {
    List<ChatRoom> findTop20ByNameContainingIgnoreCaseOrderByCreatedAtDesc(String name);

    List<ChatRoom> findTop20ByOrderByCreatedAtDesc();

    Optional<ChatRoom> findByDirectKey(String directKey);

    @Query("""
            select distinct room
            from ChatRoom room
            left join room.participantEmails participant
            where participant = :email
               or ((room.type = :groupType or room.type is null) and room.participantEmails is empty)
            order by room.createdAt desc
            """)
    List<ChatRoom> findVisibleRooms(
            @Param("email") String email,
            @Param("groupType") ChatRoomType groupType,
            Pageable pageable
    );

    @Query("""
            select distinct room
            from ChatRoom room
            left join room.participantEmails participant
            where (participant = :email or ((room.type = :groupType or room.type is null) and room.participantEmails is empty))
              and lower(room.name) like lower(concat('%', :query, '%'))
            order by room.createdAt desc
            """)
    List<ChatRoom> searchVisibleRooms(
            @Param("email") String email,
            @Param("groupType") ChatRoomType groupType,
            @Param("query") String query,
            Pageable pageable
    );
}
