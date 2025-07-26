package akin.city_card.bus.service.concretes;

import akin.city_card.admin.core.request.UpdateLocationRequest;
import akin.city_card.admin.exceptions.AdminNotFoundException;
import akin.city_card.admin.model.Admin;
import akin.city_card.admin.repository.AdminRepository;
import akin.city_card.bus.core.converter.BusConverter;
import akin.city_card.bus.core.request.BusStatusUpdateRequest;
import akin.city_card.bus.core.request.CreateBusRequest;
import akin.city_card.bus.core.request.UpdateBusRequest;
import akin.city_card.bus.core.response.BusDTO;
import akin.city_card.bus.core.response.BusLocationDTO;
import akin.city_card.bus.core.response.StationDTO;
import akin.city_card.bus.exceptions.*;
import akin.city_card.bus.model.Bus;
import akin.city_card.bus.model.BusLocation;
import akin.city_card.bus.model.BusStatus;
import akin.city_card.bus.repository.BusLocationRepository;
import akin.city_card.bus.repository.BusRepository;
import akin.city_card.bus.service.abstracts.BusService;
import akin.city_card.bus.service.abstracts.GoogleMapsService;
import akin.city_card.driver.model.Driver;
import akin.city_card.driver.repository.DriverRepository;
import akin.city_card.news.exceptions.UnauthorizedAreaException;
import akin.city_card.response.DataResponseMessage;
import akin.city_card.response.ResponseMessage;
import akin.city_card.route.model.Route;
import akin.city_card.route.model.RouteDirection;
import akin.city_card.route.model.RouteStationNode;
import akin.city_card.route.repository.RouteRepository;
import akin.city_card.station.model.Station;
import akin.city_card.station.repository.StationRepository;
import akin.city_card.superadmin.model.SuperAdmin;
import akin.city_card.superadmin.repository.SuperAdminRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BusManager implements BusService {

    private final BusConverter busConverter;
    private final AdminRepository adminRepository;
    private final SuperAdminRepository superAdminRepository;
    private final RouteRepository routeRepository;
    private final DriverRepository driverRepository;
    private final BusRepository busRepository;
    private final BusLocationRepository busLocationRepository;
    private final StationRepository stationRepository;
    private final GoogleMapsService googleMapsService;

    // === YARDIMCI METOTLAR ===

    private boolean isAdminOrSuperAdmin(String username) {
        Admin admin = adminRepository.findByUserNumber(username);
        SuperAdmin superAdmin = superAdminRepository.findByUserNumber(username);
        return admin != null || superAdmin != null;
    }

    private Object getAdminOrSuperAdmin(String username) throws AdminNotFoundException {
        Admin admin = adminRepository.findByUserNumber(username);
        SuperAdmin superAdmin = superAdminRepository.findByUserNumber(username);

        if (admin == null && superAdmin == null) {
            throw new AdminNotFoundException();
        }
        return admin != null ? admin : superAdmin;
    }

    // === TEMEL CRUD İŞLEMLERİ ===

    @Override
    public DataResponseMessage<List<BusDTO>> getAllBuses(String username)
            throws AdminNotFoundException, UnauthorizedAreaException {
        if (!isAdminOrSuperAdmin(username)) {
            throw new UnauthorizedAreaException();
        }

        List<Bus> buses = busRepository.findAllByIsDeletedFalse();
        List<BusDTO> busDTOs = busConverter.toBusDTOList(buses);

        return new DataResponseMessage<>("Tüm otobüsler başarıyla getirildi.", true, busDTOs);
    }

    @Override
    public DataResponseMessage<BusDTO> getBusById(Long busId, String username)
            throws AdminNotFoundException, BusNotFoundException, UnauthorizedAreaException {
        if (!isAdminOrSuperAdmin(username)) {
            throw new UnauthorizedAreaException();
        }

        Bus bus = busRepository.findByIdAndIsDeletedFalse(busId)
                .orElseThrow(() -> new BusNotFoundException(busId));
        BusDTO busDTO = busConverter.toBusDTO(bus);

        return new DataResponseMessage<>("Otobüs başarıyla getirildi.", true, busDTO);
    }

    @Override
    public DataResponseMessage<List<BusDTO>> getActiveBuses(String username) {
        List<Bus> activeBuses = busRepository.findAllByIsActiveTrueAndIsDeletedFalse();
        List<BusDTO> busDTOs = busConverter.toBusDTOList(activeBuses);

        return new DataResponseMessage<>("Aktif otobüsler başarıyla getirildi.", true, busDTOs);
    }

    @Override
    @Transactional
    public ResponseMessage createBus(CreateBusRequest request, String username)
            throws AdminNotFoundException, DuplicateBusPlateException,
            RouteNotFoundException, DriverNotFoundException {

        Object adminOrSuperAdmin = getAdminOrSuperAdmin(username);

        // Plaka kontrolü
        String plateNumber = request.getNumberPlate().trim().toUpperCase();
        if (busRepository.existsByNumberPlateAndIsDeletedFalse(plateNumber)) {
            throw new DuplicateBusPlateException();
        }

        Bus bus = busConverter.fromCreateBusRequest(request);
        bus.setNumberPlate(plateNumber);

        // Rota atama
        if (request.getRouteId() != null) {
            Route route = routeRepository.findById(request.getRouteId())
                    .orElseThrow(RouteNotFoundException::new);
            bus.setAssignedRoute(route);

            // Varsayılan olarak gidiş yönü ata
            if (route.getOutgoingDirection() != null) {
                bus.setCurrentDirection(route.getOutgoingDirection());
            }
        }

        // Şoför atama
        if (request.getDriverId() != null) {
            Driver driver = driverRepository.findById(request.getDriverId())
                    .orElseThrow(() -> new DriverNotFoundException(request.getDriverId()));

            // Şoför başka bir otobüse atanmış mı?
            if (busRepository.existsByDriverIdAndIsActiveTrueAndIsDeletedFalse(driver.getId())) {
                return new ResponseMessage("Şoför zaten başka bir aktif otobüse atanmış.", false);
            }

            bus.setDriver(driver);
        }

        // Varsayılan değerler
        bus.setStatus(BusStatus.CALISIYOR);
        bus.setCurrentPassengerCount(0);
        bus.setCreatedBy((akin.city_card.security.entity.SecurityUser) adminOrSuperAdmin);
        bus.setUpdatedBy((akin.city_card.security.entity.SecurityUser) adminOrSuperAdmin);

        busRepository.save(bus);
        log.info("Bus created successfully with plate: {}", plateNumber);

        return new ResponseMessage("Otobüs başarıyla oluşturuldu.", true);
    }

    @Override
    @Transactional
    public ResponseMessage updateBus(Long busId, UpdateBusRequest request, String username)
            throws AdminNotFoundException, DuplicateBusPlateException,
            DriverNotFoundException, RouteNotFoundException, BusNotFoundException {

        Object adminOrSuperAdmin = getAdminOrSuperAdmin(username);

        Bus bus = busRepository.findByIdAndIsDeletedFalse(busId)
                .orElseThrow(() -> new BusNotFoundException(busId));

        // Plaka kontrolü
        if (request.getNumberPlate() != null && !request.getNumberPlate().trim().isEmpty()) {
            String newPlate = request.getNumberPlate().trim().toUpperCase();
            if (!bus.getNumberPlate().equals(newPlate) &&
                    busRepository.existsByNumberPlateAndIsDeletedFalse(newPlate)) {
                throw new DuplicateBusPlateException();
            }
        }

        // Şoför değişikliği kontrolü
        if (request.getDriverId() != null) {
            Driver newDriver = driverRepository.findById(request.getDriverId())
                    .orElseThrow(() -> new DriverNotFoundException(request.getDriverId()));

            // Yeni şoför başka bir otobüse atanmış mı?
            Optional<Bus> existingBusWithDriver = busRepository.findByDriverIdAndIsActiveTrueAndIsDeletedFalse(newDriver.getId());
            if (existingBusWithDriver.isPresent() && !existingBusWithDriver.get().getId().equals(busId)) {
                return new ResponseMessage("Şoför zaten başka bir aktif otobüse atanmış.", false);
            }

            bus.setDriver(newDriver);
        }

        // Rota değişikliği
        if (request.getRouteId() != null) {
            Route route = routeRepository.findById(request.getRouteId())
                    .orElseThrow(RouteNotFoundException::new);
            bus.setAssignedRoute(route);

            // Yön değişikliği
            if (request.getCurrentDirectionId() != null) {
                // RouteDirection kontrolü yapılabilir
                // bus.setCurrentDirection(routeDirection);
            }
        }

        // Diğer alanları güncelle
        busConverter.updateBusFromRequest(bus, request);
        bus.setUpdatedBy((akin.city_card.security.entity.SecurityUser) adminOrSuperAdmin);

        busRepository.save(bus);
        log.info("Bus updated successfully: {}", bus.getNumberPlate());

        return new ResponseMessage("Otobüs başarıyla güncellendi.", true);
    }

    @Override
    @Transactional
    public ResponseMessage deleteBus(Long busId, String username)
            throws AdminNotFoundException, BusNotFoundException {

        Object adminOrSuperAdmin = getAdminOrSuperAdmin(username);

        Bus bus = busRepository.findByIdAndIsDeletedFalse(busId)
                .orElseThrow(() -> new BusNotFoundException(busId));

        // Soft delete
        bus.setDeleted(true);
        bus.setDeletedAt(LocalDateTime.now());
        bus.setDeletedBy((akin.city_card.security.entity.SecurityUser) adminOrSuperAdmin);
        bus.setActive(false);
        bus.setStatus(BusStatus.SERVIS_DISI);

        // Şoför atamasını temizle
        if (bus.getDriver() != null) {
            bus.setDriver(null);
        }

        busRepository.save(bus);
        log.info("Bus soft deleted: {}", bus.getNumberPlate());

        return new ResponseMessage("Otobüs başarıyla silindi.", true);
    }

    @Override
    @Transactional
    public ResponseMessage toggleBusActive(Long busId, String username)
            throws AdminNotFoundException, BusNotFoundException {

        Object adminOrSuperAdmin = getAdminOrSuperAdmin(username);

        Bus bus = busRepository.findByIdAndIsDeletedFalse(busId)
                .orElseThrow(() -> new BusNotFoundException(busId));

        boolean newActiveStatus = !bus.isActive();
        bus.setActive(newActiveStatus);

        if (newActiveStatus) {
            bus.setStatus(BusStatus.CALISIYOR);
        } else {
            bus.setStatus(BusStatus.SERVIS_DISI);
            bus.setCurrentPassengerCount(0);
        }

        bus.setUpdatedBy((akin.city_card.security.entity.SecurityUser) adminOrSuperAdmin);
        busRepository.save(bus);

        String message = newActiveStatus ?
                "Otobüs başarıyla aktif hale getirildi." :
                "Otobüs başarıyla pasif hale getirildi.";

        log.info("Bus active status changed: {} - {}", bus.getNumberPlate(), newActiveStatus);
        return new ResponseMessage(message, true);
    }

    // === ŞOFÖR YÖNETİMİ ===

    @Override
    @Transactional
    public ResponseMessage assignDriver(Long busId, Long driverId, String username)
            throws AdminNotFoundException, BusNotFoundException, DriverNotFoundException, DriverAlreadyAssignedException {

        Object adminOrSuperAdmin = getAdminOrSuperAdmin(username);

        Bus bus = busRepository.findByIdAndIsDeletedFalse(busId)
                .orElseThrow(() -> new BusNotFoundException(busId));

        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new DriverNotFoundException(driverId));

        // Şoför başka bir aktif otobüse atanmış mı?
        Optional<Bus> existingBus = busRepository.findByDriverIdAndIsActiveTrueAndIsDeletedFalse(driverId);
        if (existingBus.isPresent() && !existingBus.get().getId().equals(busId)) {
            throw new DriverAlreadyAssignedException(driverId);
        }

        // Eski şoför atamasını temizle - BURADAKİ HATA DÜZELTİLDİ
        if (bus.getDriver() != null && !bus.getDriver().getId().equals(driverId)) {
            // Eski şoförün tüm otobüslerini temizle
            List<Bus> oldDriverBuses = busRepository.findByDriverIdAndIsDeletedFalse(bus.getDriver().getId());
            for (Bus oldBus : oldDriverBuses) {
                oldBus.setDriver(null);
                busRepository.save(oldBus);
            }
        }

        bus.setDriver(driver);
        bus.setUpdatedBy((akin.city_card.security.entity.SecurityUser) adminOrSuperAdmin);
        busRepository.save(bus);

        log.info("Driver assigned to bus: {} -> {}", driver.getId(), bus.getNumberPlate());
        return new ResponseMessage("Şoför başarıyla otobüse atandı.", true);
    }

    // === KONUM YÖNETİMİ ===

    @Override
    public DataResponseMessage<BusLocationDTO> getCurrentLocation(Long busId) throws BusNotFoundException {
        Bus bus = busRepository.findByIdAndIsDeletedFalse(busId)
                .orElseThrow(() -> new BusNotFoundException(busId));

        BusLocation currentLocation = busLocationRepository.findTopByBusOrderByTimestampDesc(bus)
                .orElse(null);

        if (currentLocation == null) {
            return new DataResponseMessage<>("Otobüs için konum bilgisi bulunamadı.", false, null);
        }

        BusLocationDTO dto = busConverter.toBusLocationDTO(currentLocation);
        return new DataResponseMessage<>("Otobüsün güncel konumu getirildi.", true, dto);
    }

    @Override
    @Transactional
    public ResponseMessage updateLocation(Long busId, UpdateLocationRequest request)
            throws UnauthorizedLocationUpdateException, BusNotFoundException {

        // Konum validasyonu
        if (request.getLatitude() == null || request.getLongitude() == null ||
                request.getLatitude() < -90 || request.getLatitude() > 90 ||
                request.getLongitude() < -180 || request.getLongitude() > 180) {
            return new ResponseMessage("Geçersiz konum bilgisi.", false);
        }

        Bus bus = busRepository.findByIdAndIsDeletedFalse(busId)
                .orElseThrow(() -> new BusNotFoundException(busId));

        // BusLocation kaydı oluştur
        BusLocation location = busConverter.fromUpdateLocationRequest(request);
        location.setBus(bus);

        // En yakın durak hesapla (opsiyonel)
        try {
            Station closestStation = findClosestStation(request.getLatitude(), request.getLongitude());
            if (closestStation != null) {
                location.setClosestStation(closestStation);
                // Mesafe hesaplama burada yapılabilir
            }
        } catch (Exception e) {
            log.warn("Could not find closest station for bus {}: {}", busId, e.getMessage());
        }

        busLocationRepository.save(location);

        // Bus'ın güncel konumunu güncelle
        bus.setCurrentLatitude(request.getLatitude());
        bus.setCurrentLongitude(request.getLongitude());
        bus.setLastLocationUpdate(LocalDateTime.now());

        if (request.getSpeed() != null) {
            bus.setLastKnownSpeed(request.getSpeed());
        }

        busRepository.save(bus);

        log.debug("Location updated for bus {}: {}, {}", busId, request.getLatitude(), request.getLongitude());
        return new ResponseMessage("Otobüs konumu başarıyla güncellendi.", true);
    }

    @Override
    public DataResponseMessage<List<BusLocationDTO>> getLocationHistory(Long busId, LocalDate date, String username)
            throws UnauthorizedAccessException, BusNotFoundException {

        if (!isAdminOrSuperAdmin(username)) {
            throw new UnauthorizedAccessException();
        }

        Bus bus = busRepository.findByIdAndIsDeletedFalse(busId)
                .orElseThrow(() -> new BusNotFoundException(busId));

        List<BusLocation> locations;

        if (date != null) {
            LocalDateTime startOfDay = date.atStartOfDay();
            LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();
            locations = busLocationRepository.findAllByBusAndTimestampBetweenOrderByTimestampDesc(
                    bus, startOfDay, endOfDay);
        } else {
            // Son 24 saat
            LocalDateTime yesterday = LocalDateTime.now().minusDays(1);
            locations = busLocationRepository.findAllByBusAndTimestampAfterOrderByTimestampDesc(bus, yesterday);
        }

        List<BusLocationDTO> dtos = busConverter.toBusLocationDTOList(locations);
        return new DataResponseMessage<>("Konum geçmişi başarıyla getirildi.", true, dtos);
    }

    // === ROTA YÖNETİMİ ===

    @Override
    @Transactional
    public ResponseMessage assignRoute(Long busId, Long routeId, String username) {
        try {
            if (!isAdminOrSuperAdmin(username)) {
                return new ResponseMessage("Yetkisiz erişim.", false);
            }

            Object adminOrSuperAdmin = getAdminOrSuperAdmin(username);

            Bus bus = busRepository.findByIdAndIsDeletedFalse(busId)
                    .orElseThrow(() -> new BusNotFoundException(busId));
            Route route = routeRepository.findById(routeId)
                    .orElseThrow(() -> new RouteNotFoundException());

            bus.setAssignedRoute(route);

            // Varsayılan olarak gidiş yönü ata
            if (route.getOutgoingDirection() != null) {
                bus.setCurrentDirection(route.getOutgoingDirection());
            }

            bus.setUpdatedBy((akin.city_card.security.entity.SecurityUser) adminOrSuperAdmin);
            busRepository.save(bus);

            log.info("Route assigned to bus: {} -> {}", route.getName(), bus.getNumberPlate());
            return new ResponseMessage("Rota başarıyla otobüse atandı.", true);
        } catch (Exception e) {
            log.error("Error assigning route to bus: ", e);
            return new ResponseMessage("Rota ataması sırasında hata oluştu.", false);
        }
    }

    @Override
    public DataResponseMessage<List<StationDTO>> getRouteStations(Long busId, String username) {
        try {
            Bus bus = busRepository.findByIdAndIsDeletedFalse(busId)
                    .orElseThrow(() -> new BusNotFoundException(busId));

            Route route = bus.getAssignedRoute();
            if (route == null) {
                return new DataResponseMessage<>("Otobüse atanmış rota bulunamadı.", false, null);
            }

            // Otobüsün currentDirection'ı varsa kullan, yoksa rota'nın gidiş yönünü al
            RouteDirection direction = bus.getCurrentDirection();
            if (direction == null) {
                direction = route.getOutgoingDirection();
            }

            if (direction == null) {
                return new DataResponseMessage<>("Rota yönü bulunamadı.", false, null);
            }

            List<RouteStationNode> nodes = direction.getStationNodes();
            if (nodes == null || nodes.isEmpty()) {
                return new DataResponseMessage<>("Rota yönüne ait istasyon bağlantıları bulunamadı.", false, null);
            }

            // İstasyoları sırayla al
            List<StationDTO> stationDTOs = nodes.stream()
                    .map(RouteStationNode::getFromStation)  // Her node'un fromStation'ı
                    .map(this::convertToStationDTO)
                    .collect(Collectors.toList());

            // Son node'un toStation'ını da ekle
            Station lastStation = nodes.get(nodes.size() - 1).getToStation();
            stationDTOs.add(convertToStationDTO(lastStation));

            return new DataResponseMessage<>("Rota istasyonları başarıyla getirildi.", true, stationDTOs);

        } catch (Exception e) {
            log.error("Error getting route stations: ", e);
            return new DataResponseMessage<>("Rota istasyonları getirilirken hata oluştu.", false, null);
        }
    }



    @Override
    public DataResponseMessage<Double> getEstimatedArrivalTime(Long busId, Long stationId, String username) {
        try {
            Bus bus = busRepository.findByIdAndIsDeletedFalse(busId)
                    .orElseThrow(() -> new BusNotFoundException(busId));

            Station station = stationRepository.findById(stationId)
                    .orElseThrow(() -> new RuntimeException("İstasyon bulunamadı"));

            if (bus.getCurrentLatitude() == null || bus.getCurrentLongitude() == null) {
                return new DataResponseMessage<>("Otobüsün güncel konumu bulunamadı.", false, null);
            }

            if (station.getLocation() == null) {
                return new DataResponseMessage<>("İstasyonun konum bilgisi bulunamadı.", false, null);
            }

            // Google Maps API ile ETA hesapla
            Integer estimatedMinutes = googleMapsService.getEstimatedTimeInMinutes(
                    bus.getCurrentLatitude(), bus.getCurrentLongitude(),
                    station.getLocation().getLatitude(), station.getLocation().getLongitude()
            );

            if (estimatedMinutes == null) {
                return new DataResponseMessage<>("Tahmini varış süresi hesaplanamadı.", false, null);
            }

            return new DataResponseMessage<>("Tahmini varış süresi başarıyla hesaplandı.",
                    true, estimatedMinutes.doubleValue());

        } catch (Exception e) {
            log.error("Error calculating estimated arrival time: ", e);
            return new DataResponseMessage<>("Tahmini varış süresi hesaplanırken hata oluştu.", false, null);
        }
    }

    // === YÖN YÖNETİMİ ===

    @Override
    @Transactional
    public ResponseMessage switchDirection(Long busId, String username) throws BusNotFoundException {
        try {
            if (!isAdminOrSuperAdmin(username)) {
                return new ResponseMessage("Yetkisiz erişim.", false);
            }

            Object adminOrSuperAdmin = getAdminOrSuperAdmin(username);

            Bus bus = busRepository.findByIdAndIsDeletedFalse(busId)
                    .orElseThrow(() -> new BusNotFoundException(busId));

            if (bus.getAssignedRoute() == null) {
                return new ResponseMessage("Otobüse rota atanmamış.", false);
            }

            bus.switchDirection();
            bus.setUpdatedBy((akin.city_card.security.entity.SecurityUser) adminOrSuperAdmin);
            busRepository.save(bus);

            log.info("Direction switched for bus: {}", bus.getNumberPlate());
            return new ResponseMessage("Otobüs yönü başarıyla değiştirildi.", true);

        } catch (Exception e) {
            log.error("Error switching bus direction: ", e);
            return new ResponseMessage("Yön değiştirilirken hata oluştu.", false);
        }
    }

    // === İSTATİSTİKLER ===

    @Override
    public DataResponseMessage<Object> getBusStatistics(String username) {
        try {
            if (!isAdminOrSuperAdmin(username)) {
                return new DataResponseMessage<>("Yetkisiz erişim.", false, null);
            }

            Map<String, Object> stats = new HashMap<>();

            // Temel sayılar
            stats.put("totalBuses", busRepository.countByIsDeletedFalse());
            stats.put("activeBuses", busRepository.countByIsActiveTrueAndIsDeletedFalse());
            stats.put("inactiveBuses", busRepository.countByIsActiveFalseAndIsDeletedFalse());

            // Durum bazlı dağılım
            Map<String, Long> statusDistribution = new HashMap<>();
            for (BusStatus status : BusStatus.values()) {
                Long count = busRepository.countByStatusAndIsDeletedFalse(status);
                statusDistribution.put(status.getDisplayName(), count);
            }
            stats.put("statusDistribution", statusDistribution);

            // Şoför ataması
            stats.put("busesWithDriver", busRepository.countByDriverIsNotNullAndIsDeletedFalse());
            stats.put("busesWithoutDriver", busRepository.countByDriverIsNullAndIsDeletedFalse());

            // Rota ataması
            stats.put("busesWithRoute", busRepository.countByAssignedRouteIsNotNullAndIsDeletedFalse());
            stats.put("busesWithoutRoute", busRepository.countByAssignedRouteIsNullAndIsDeletedFalse());

            return new DataResponseMessage<>("İstatistikler başarıyla getirildi.", true, stats);

        } catch (Exception e) {
            log.error("Error getting bus statistics: ", e);
            return new DataResponseMessage<>("İstatistikler alınırken hata oluştu.", false, null);
        }
    }

    // === FİLTRELEME VE ARAMA ===

    @Override
    public DataResponseMessage<List<BusDTO>> searchByNumberPlate(String numberPlate, String username) {
        try {
            if (!isAdminOrSuperAdmin(username)) {
                return new DataResponseMessage<>("Yetkisiz erişim.", false, null);
            }

            List<Bus> buses = busRepository.findByNumberPlateContainingIgnoreCaseAndIsDeletedFalse(numberPlate);
            List<BusDTO> busDTOs = busConverter.toBusDTOList(buses);

            return new DataResponseMessage<>("Arama sonuçları getirildi.", true, busDTOs);
        } catch (Exception e) {
            log.error("Error searching buses by plate: ", e);
            return new DataResponseMessage<>("Arama yapılırken hata oluştu.", false, null);
        }
    }

    @Override
    public DataResponseMessage<List<BusDTO>> getBusesByRoute(Long routeId, String username) {
        try {
            if (!isAdminOrSuperAdmin(username)) {
                return new DataResponseMessage<>("Yetkisiz erişim.", false, null);
            }

            List<Bus> buses = busRepository.findByAssignedRouteIdAndIsDeletedFalse(routeId);
            List<BusDTO> busDTOs = busConverter.toBusDTOList(buses);

            return new DataResponseMessage<>("Rotadaki otobüsler getirildi.", true, busDTOs);
        } catch (Exception e) {
            log.error("Error getting buses by route: ", e);
            return new DataResponseMessage<>("Rotadaki otobüsler getirilirken hata oluştu.", false, null);
        }
    }

    @Override
    public DataResponseMessage<List<BusDTO>> getBusesByDriver(Long driverId, String username) {
        try {
            if (!isAdminOrSuperAdmin(username)) {
                return new DataResponseMessage<>("Yetkisiz erişim.", false, null);
            }

            List<Bus> buses = busRepository.findByDriverIdAndIsDeletedFalse(driverId);
            List<BusDTO> busDTOs = busConverter.toBusDTOList(buses);

            return new DataResponseMessage<>("Şoförün otobüsleri getirildi.", true, busDTOs);
        } catch (Exception e) {
            log.error("Error getting buses by driver: ", e);
            return new DataResponseMessage<>("Şoförün otobüsleri getirilirken hata oluştu.", false, null);
        }
    }

    // === DURUM YÖNETİMİ ===

    @Override
    @Transactional
    public ResponseMessage updateBusStatus(Long busId, BusStatusUpdateRequest request, String username)
            throws BusNotFoundException {
        try {
            if (!isAdminOrSuperAdmin(username)) {
                return new ResponseMessage("Yetkisiz erişim.", false);
            }

            Object adminOrSuperAdmin = getAdminOrSuperAdmin(username);

            Bus bus = busRepository.findByIdAndIsDeletedFalse(busId)
                    .orElseThrow(() -> new BusNotFoundException(busId));

            // Status'u güncelle
            if (request.getStatus() != null) {
                bus.setStatus(request.getStatus());
            }

            bus.setUpdatedBy((akin.city_card.security.entity.SecurityUser) adminOrSuperAdmin);
            busRepository.save(bus);

            log.info("Bus status updated: {} -> {}", bus.getNumberPlate(), request.getStatus());
            return new ResponseMessage("Otobüs durumu başarıyla güncellendi.", true);

        } catch (Exception e) {
            log.error("Error updating bus status: ", e);
            return new ResponseMessage("Durum güncellenirken hata oluştu.", false);
        }
    }

    @Override
    @Transactional
    public ResponseMessage updatePassengerCount(Long busId, Integer count, String username)
            throws BusNotFoundException {
        try {
            if (!isAdminOrSuperAdmin(username)) {
                return new ResponseMessage("Yetkisiz erişim.", false);
            }

            Object adminOrSuperAdmin = getAdminOrSuperAdmin(username);

            Bus bus = busRepository.findByIdAndIsDeletedFalse(busId)
                    .orElseThrow(() -> new BusNotFoundException(busId));

            // Yolcu sayısı validasyonu
            if (count < 0) {
                return new ResponseMessage("Yolcu sayısı negatif olamaz.", false);
            }

            if (count > bus.getCapacity()) {
                return new ResponseMessage("Yolcu sayısı kapasiteyi aşamaz.", false);
            }

            bus.setCurrentPassengerCount(count);
            bus.setUpdatedBy((akin.city_card.security.entity.SecurityUser) adminOrSuperAdmin);
            busRepository.save(bus);

            log.info("Passenger count updated: {} -> {}", bus.getNumberPlate(), count);
            return new ResponseMessage("Yolcu sayısı başarıyla güncellendi.", true);

        } catch (Exception e) {
            log.error("Error updating passenger count: ", e);
            return new ResponseMessage("Yolcu sayısı güncellenirken hata oluştu.", false);
        }
    }

    // === TOPLU İŞLEMLER ===

    @Override
    @Transactional
    public ResponseMessage bulkActivate(List<Long> busIds, String username) {
        try {
            if (!isAdminOrSuperAdmin(username)) {
                return new ResponseMessage("Yetkisiz erişim.", false);
            }

            Object adminOrSuperAdmin = getAdminOrSuperAdmin(username);
            int updatedCount = 0;

            for (Long busId : busIds) {
                Optional<Bus> busOpt = busRepository.findByIdAndIsDeletedFalse(busId);
                if (busOpt.isPresent()) {
                    Bus bus = busOpt.get();
                    if (!bus.isActive()) {
                        bus.setActive(true);
                        bus.setStatus(BusStatus.CALISIYOR);
                        bus.setUpdatedBy((akin.city_card.security.entity.SecurityUser) adminOrSuperAdmin);
                        busRepository.save(bus);
                        updatedCount++;
                    }
                }
            }

            log.info("Bulk activate completed: {} buses activated", updatedCount);
            return new ResponseMessage(updatedCount + " otobüs başarıyla aktif hale getirildi.", true);

        } catch (Exception e) {
            log.error("Error in bulk activate: ", e);
            return new ResponseMessage("Toplu aktifleştirme sırasında hata oluştu.", false);
        }
    }

    @Override
    @Transactional
    public ResponseMessage bulkDeactivate(List<Long> busIds, String username) {
        try {
            if (!isAdminOrSuperAdmin(username)) {
                return new ResponseMessage("Yetkisiz erişim.", false);
            }

            Object adminOrSuperAdmin = getAdminOrSuperAdmin(username);
            int updatedCount = 0;

            for (Long busId : busIds) {
                Optional<Bus> busOpt = busRepository.findByIdAndIsDeletedFalse(busId);
                if (busOpt.isPresent()) {
                    Bus bus = busOpt.get();
                    if (bus.isActive()) {
                        bus.setActive(false);
                        bus.setStatus(BusStatus.SERVIS_DISI);
                        bus.setCurrentPassengerCount(0);
                        bus.setUpdatedBy((akin.city_card.security.entity.SecurityUser) adminOrSuperAdmin);
                        busRepository.save(bus);
                        updatedCount++;
                    }
                }
            }

            log.info("Bulk deactivate completed: {} buses deactivated", updatedCount);
            return new ResponseMessage(updatedCount + " otobüs başarıyla pasif hale getirildi.", true);

        } catch (Exception e) {
            log.error("Error in bulk deactivate: ", e);
            return new ResponseMessage("Toplu pasifleştirme sırasında hata oluştu.", false);
        }
    }

    @Override
    public DataResponseMessage<List<BusDTO>> getBusesByStatus(String status, String username) {
        try {
            if (!isAdminOrSuperAdmin(username)) {
                return new DataResponseMessage<>("Yetkisiz erişim.", false, null);
            }

            BusStatus busStatus = BusStatus.valueOf(status.toUpperCase());
            List<Bus> buses = busRepository.findByStatusAndIsDeletedFalse(busStatus);
            List<BusDTO> busDTOs = busConverter.toBusDTOList(buses);

            return new DataResponseMessage<>("Durumdaki otobüsler getirildi.", true, busDTOs);
        } catch (IllegalArgumentException e) {
            return new DataResponseMessage<>("Geçersiz durum değeri.", false, null);
        } catch (Exception e) {
            log.error("Error getting buses by status: ", e);
            return new DataResponseMessage<>("Durumdaki otobüsler getirilirken hata oluştu.", false, null);
        }
    }

    // === YARDIMCI METOTLAR ===

    private StationDTO convertToStationDTO(Station station) {
        return StationDTO.builder()
                .id(station.getId())
                .name(station.getName())
                .latitude(station.getLocation() != null ? station.getLocation().getLatitude() : 0.0)
                .longitude(station.getLocation() != null ? station.getLocation().getLongitude() : 0.0)
                .active(station.isActive())
                .type(station.getType() != null ? station.getType().name() : null)
                .city(station.getAddress() != null ? station.getAddress().getCity() : null)
                .district(station.getAddress() != null ? station.getAddress().getDistrict() : null)
                .street(station.getAddress() != null ? station.getAddress().getStreet() : null)
                .postalCode(station.getAddress() != null ? station.getAddress().getPostalCode() : null)
                .build();
    }

    private Station findClosestStation(double latitude, double longitude) {
        // Basit mesafe hesaplama - gerçek implementasyonda daha karmaşık olabilir
        List<Station> allStations = stationRepository.findAllByActiveTrue();

        Station closest = null;
        double minDistance = Double.MAX_VALUE;

        for (Station station : allStations) {
            if (station.getLocation() != null) {
                double distance = calculateDistance(latitude, longitude,
                        station.getLocation().getLatitude(), station.getLocation().getLongitude());
                if (distance < minDistance) {
                    minDistance = distance;
                    closest = station;
                }
            }
        }

        return closest;
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        // Haversine formülü - basit mesafe hesaplama
        final int R = 6371; // Earth's radius in kilometers

        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c * 1000; // Metre cinsinden döndür
    }
}