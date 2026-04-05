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
}
