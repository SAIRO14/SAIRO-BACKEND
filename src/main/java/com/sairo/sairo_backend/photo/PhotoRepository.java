package com.sairo.sairo_backend.photo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PhotoRepository extends JpaRepository<Photo, String> {

    @Query(value = "SELECT * FROM photos ORDER BY RANDOM() LIMIT :limit", nativeQuery = true)
    List<Photo> findRandom(@Param("limit") int limit);
}
