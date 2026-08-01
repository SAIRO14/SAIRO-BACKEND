package com.sairo.sairo_backend.spot;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SpotRepository extends JpaRepository<Spot, String> {

    // ORDER BY spot_id: get(0)으로 canonical region_name을 고를 때 결과가 항상 같아야 한다.
    // 좌표 있는 것만 반환한다 — 클러스터 샘플링에 좌표가 필요하고, 좌표 없는 스팟은
    // 코스 생성 단계에서 뒤에 붙으므로 추천 풀에서 굳이 가져올 이유가 없다.
    @Query(value = "SELECT * FROM spots WHERE region_name ILIKE '%' || :region || '%' AND lat IS NOT NULL AND lng IS NOT NULL ORDER BY spot_id",
            nativeQuery = true)
    List<Spot> findAllByRegionContainingWithCoords(@Param("region") String region);
}
