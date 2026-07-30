package com.sairo.sairo_backend.spot;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SpotRepository extends JpaRepository<Spot, String> {

    // ORDER BY spot_id: get(0)으로 canonical region_name을 고를 때 결과가 항상 같아야 한다
    @Query(value = "SELECT * FROM spots WHERE region_name ILIKE '%' || :region || '%' ORDER BY spot_id LIMIT :limit",
            nativeQuery = true)
    List<Spot> findByRegionContaining(@Param("region") String region, @Param("limit") int limit);
}
