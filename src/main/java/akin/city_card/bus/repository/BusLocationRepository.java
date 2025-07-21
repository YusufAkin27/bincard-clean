package akin.city_card.bus.repository;

import akin.city_card.bus.model.Bus;
import akin.city_card.bus.model.BusLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BusLocationRepository extends JpaRepository<BusLocation, Long> {
    Optional<BusLocation> findTopByBusOrderByTimestampDesc(Bus bus);

    List<BusLocation> findAllByBusAndTimestampBetweenOrderByTimestampDesc(Bus bus, LocalDateTime startOfDay, LocalDateTime endOfDay);

    List<BusLocation> findAllByBusOrderByTimestampDesc(Bus bus);

    BusLocation findFirstByBusAndTimestampAfterOrderByTimestampDesc(Bus bus, LocalDateTime cutoffTime);

    @Query("SELECT bl FROM BusLocation bl WHERE bl.bus.id = :busId " +
            "AND bl.timestamp >= :fromTime ORDER BY bl.timestamp DESC")
    List<BusLocation> findRecentLocationsByBusId(Long busId, LocalDateTime fromTime);

    @Query("SELECT bl FROM BusLocation bl WHERE bl.timestamp >= :fromTime " +
            "AND bl.bus.active = true ORDER BY bl.timestamp DESC")
    List<BusLocation> findRecentActiveLocations(LocalDateTime fromTime);
}
