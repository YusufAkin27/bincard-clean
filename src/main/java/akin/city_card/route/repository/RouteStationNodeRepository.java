package akin.city_card.route.repository;

import akin.city_card.route.model.RouteStationNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface RouteStationNodeRepository extends JpaRepository<RouteStationNode, Long> {

    List<RouteStationNode> findByRouteIdOrderBySequenceOrder(Long routeId);

    @Query("SELECT rsn FROM RouteStationNode rsn WHERE rsn.fromStation.id = :stationId " +
            "OR rsn.toStation.id = :stationId")
    List<RouteStationNode> findByFromStationIdOrToStationId(Long stationId, Long stationId2);

    @Query("SELECT rsn FROM RouteStationNode rsn WHERE rsn.route.id = :routeId " +
            "AND rsn.toStation.id = :stationId ORDER BY rsn.sequenceOrder")
    List<RouteStationNode> findPathToStation(Long routeId, Long stationId);
}
