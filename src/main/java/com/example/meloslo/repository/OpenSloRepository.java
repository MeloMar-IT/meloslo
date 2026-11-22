package com.example.meloslo.repository;

import com.example.meloslo.model.OpenSlo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OpenSloRepository extends JpaRepository<OpenSlo, Long> {
    Optional<OpenSlo> findByName(String name);
    List<OpenSlo> findByKind(String kind);

    @org.springframework.data.jpa.repository.Query("SELECT o FROM OpenSlo o LEFT JOIN FETCH o.slis WHERE o.kind = :kind")
    List<OpenSlo> findByKindWithSlis(String kind);

    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true)
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Query("UPDATE OpenSlo o SET o.lastFetchFailureTime = :time, o.lastFetchError = :error WHERE o.id = :id")
    int recordFetchFailure(@org.springframework.data.repository.query.Param("id") Long id,
                           @org.springframework.data.repository.query.Param("time") java.time.LocalDateTime time,
                           @org.springframework.data.repository.query.Param("error") String error);
}
