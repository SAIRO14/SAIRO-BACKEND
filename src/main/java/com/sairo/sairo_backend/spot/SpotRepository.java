package com.sairo.sairo_backend.spot;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SpotRepository extends JpaRepository<Spot, String> {

    @Query(value = "SELECT * FROM spots WHERE region_name ILIKE '%' || :region || '%' LIMIT :limit",
            nativeQuery = true)
    List<Spot> findByRegionContaining(@Param("region") String region, @Param("limit") int limit);
}
