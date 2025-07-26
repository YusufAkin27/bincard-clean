package akin.city_card.bus.repository;

import akin.city_card.bus.model.Bus;
import akin.city_card.bus.model.BusStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BusRepository extends JpaRepository<Bus, Long> {

    // === TEMEL CRUD SORGULARI ===

    Optional<Bus> findByIdAndIsDeletedFalse(Long id);

    List<Bus> findAllByIsDeletedFalse();

    List<Bus> findAllByIsActiveTrueAndIsDeletedFalse();

    List<Bus> findAllByIsActiveFalseAndIsDeletedFalse();

    // === PLAKA SORGULARI ===

    boolean existsByNumberPlateAndIsDeletedFalse(String numberPlate);

    @Query("SELECT b FROM Bus b WHERE UPPER(b.numberPlate) LIKE UPPER(CONCAT('%', :numberPlate, '%')) AND b.isDeleted = false")
    List<Bus> findByNumberPlateContainingIgnoreCaseAndIsDeletedFalse(@Param("numberPlate") String numberPlate);

    // === ŞOFÖR SORGULARI ===

    @Query("SELECT CASE WHEN COUNT(b) > 0 THEN true ELSE false END FROM Bus b WHERE b.driver.id = :driverId AND b.isActive = true AND b.isDeleted = false")
    boolean existsByDriverIdAndIsActiveTrueAndIsDeletedFalse(@Param("driverId") Long driverId);

    @Query("SELECT b FROM Bus b WHERE b.driver.id = :driverId AND b.isActive = true AND b.isDeleted = false")
    Optional<Bus> findByDriverIdAndIsActiveTrueAndIsDeletedFalse(@Param("driverId") Long driverId);

    @Query("SELECT b FROM Bus b WHERE b.driver.id = :driverId AND b.isDeleted = false")
    List<Bus> findByDriverIdAndIsDeletedFalse(@Param("driverId") Long driverId);

    // === ROTA SORGULARI ===

    @Query("SELECT b FROM Bus b WHERE b.assignedRoute.id = :routeId AND b.isDeleted = false")
    List<Bus> findByAssignedRouteIdAndIsDeletedFalse(@Param("routeId") Long routeId);

    // === DURUM SORGULARI ===

    List<Bus> findByStatusAndIsDeletedFalse(BusStatus status);

    // === İSTATİSTİK SORGULARI ===

    long countByIsDeletedFalse();

    long countByIsActiveTrueAndIsDeletedFalse();

    long countByIsActiveFalseAndIsDeletedFalse();

    long countByStatusAndIsDeletedFalse(BusStatus status);

    @Query("SELECT COUNT(b) FROM Bus b WHERE b.driver IS NOT NULL AND b.isDeleted = false")
    long countByDriverIsNotNullAndIsDeletedFalse();

    @Query("SELECT COUNT(b) FROM Bus b WHERE b.driver IS NULL AND b.isDeleted = false")
    long countByDriverIsNullAndIsDeletedFalse();

    @Query("SELECT COUNT(b) FROM Bus b WHERE b.assignedRoute IS NOT NULL AND b.isDeleted = false")
    long countByAssignedRouteIsNotNullAndIsDeletedFalse();

    @Query("SELECT COUNT(b) FROM Bus b WHERE b.assignedRoute IS NULL AND b.isDeleted = false")
    long countByAssignedRouteIsNullAndIsDeletedFalse();

    // === EK YARDIMCI SORGULAR ===

    @Query("SELECT b FROM Bus b WHERE b.capacity >= :minCapacity AND b.isDeleted = false")
    List<Bus> findByCapacityGreaterThanEqualAndIsDeletedFalse(@Param("minCapacity") int minCapacity);

    @Query("SELECT b FROM Bus b WHERE (CAST(b.currentPassengerCount AS double) / CAST(b.capacity AS double)) >= :occupancyRate AND b.isDeleted = false")
    List<Bus> findByOccupancyRateGreaterThanEqual(@Param("occupancyRate") double occupancyRate);

    @Query("SELECT b FROM Bus b WHERE b.lastLocationUpdate >= :since AND b.isDeleted = false")
    List<Bus> findByLastLocationUpdateAfterAndIsDeletedFalse(@Param("since") LocalDateTime since);

    @Query("SELECT b FROM Bus b WHERE b.currentLatitude IS NOT NULL AND b.currentLongitude IS NOT NULL AND b.isActive = true AND b.isDeleted = false")
    List<Bus> findActiveBusesWithLocation();

    @Query("SELECT b FROM Bus b WHERE b.lastKnownSpeed >= :minSpeed AND b.isDeleted = false")
    List<Bus> findByLastKnownSpeedGreaterThanEqualAndIsDeletedFalse(@Param("minSpeed") Double minSpeed);

}
