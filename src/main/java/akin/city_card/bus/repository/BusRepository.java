package akin.city_card.bus.repository;

import akin.city_card.bus.model.Bus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface BusRepository extends JpaRepository<Bus, Long> {
    boolean existsByNumberPlate(String numberPlate);

    Optional<Bus> findByNumberPlate(String numberPlate);

    List<Bus> findByRouteIdAndActiveTrue(Long routeId);

    @Query("SELECT b FROM Bus b WHERE b.route.id = :routeId " +
            "AND b.active = true AND b.deleted = false")
    List<Bus> findActiveBusesByRoute(Long routeId);

    @Query("SELECT b FROM Bus b JOIN RouteStationNode rsn ON b.route.id = rsn.route.id " +
            "WHERE (rsn.fromStation.id = :stationId OR rsn.toStation.id = :stationId) " +
            "AND b.active = true AND b.deleted = false")
    List<Bus> findBusesByStationId(Long stationId);
}
