package akin.city_card.route.repository;

import akin.city_card.route.model.Route;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RouteRepository extends JpaRepository<Route, Long> {


    @Query("""
    SELECT DISTINCT r FROM Route r
    JOIN r.stationNodes n
    JOIN Station s1 ON n.fromStation = s1
    JOIN Station s2 ON n.toStation = s2
    WHERE r.isActive = true AND r.isDeleted = false
      AND (LOWER(r.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
           OR LOWER(s1.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
           OR LOWER(s2.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
""")
    List<Route> searchByKeyword(@Param("keyword") String keyword);
    @Query("""
    SELECT DISTINCT r
    FROM Route r
    JOIN r.stationNodes n
    WHERE (n.fromStation.id = :stationId OR n.toStation.id = :stationId)
      AND r.isActive = true AND r.isDeleted = false
""")
    List<Route> findRoutesByStation(@Param("stationId") Long stationId);

    @Query("""
    SELECT DISTINCT r FROM Route r 
    JOIN r.stationNodes sn1 
    JOIN r.stationNodes sn2 
    WHERE sn1.fromStation.id = :startStationId 
      AND sn2.toStation.id = :endStationId 
      AND sn1.sequenceOrder < sn2.sequenceOrder 
      AND r.isActive = true 
      AND r.isDeleted = false
""")
    List<Route> findRoutesByStations(Long startStationId, Long endStationId);

}
