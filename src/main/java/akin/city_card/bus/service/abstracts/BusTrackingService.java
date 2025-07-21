package akin.city_card.bus.service.abstracts;

import akin.city_card.bus.model.Bus;
import akin.city_card.bus.model.BusLocation;
import akin.city_card.bus.repository.BusLocationRepository;
import akin.city_card.bus.repository.BusRepository;
import akin.city_card.route.model.Route;
import akin.city_card.route.model.RouteStationNode;
import akin.city_card.route.model.Direction;
import akin.city_card.route.repository.RouteStationNodeRepository;
import akin.city_card.station.model.Station;
import akin.city_card.station.repository.StationRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BusTrackingService {

    private final GoogleMapsService googleMapsService;
    private final BusLocationRepository busLocationRepository;
    private final RouteStationNodeRepository routeStationNodeRepository;
    private final BusRepository busRepository;
    private final StationRepository stationRepository;

    private static final int STATION_WAITING_TIME_MINUTES = 1;
    private static final int MAX_LOCATION_AGE_MINUTES = 30;

    /**
     * Senaryo 1: Belirli bir durak ve rotaya ait araçların varış sürelerini hesaplar
     */
    public List<BusArrivalInfo> calculateBusArrivals(Long stationId, Long routeId) {
        log.info("Calculating bus arrivals for station: {} and route: {}", stationId, routeId);
        
        // Rotaya ait aktif araçları getir
        List<Bus> activeBuses = busRepository.findByRouteIdAndActiveTrue(routeId);
        
        List<BusArrivalInfo> arrivalInfos = new ArrayList<>();
        
        for (Bus bus : activeBuses) {
            try {
                BusArrivalInfo arrivalInfo = calculateSingleBusArrival(bus, stationId);
                if (arrivalInfo != null) {
                    arrivalInfos.add(arrivalInfo);
                }
            } catch (Exception e) {
                log.error("Error calculating arrival for bus: {}", bus.getNumberPlate(), e);
            }
        }
        
        // Varış sürelerine göre sırala
        return arrivalInfos.stream()
                .sorted(Comparator.comparing(BusArrivalInfo::getEstimatedArrivalMinutes))
                .collect(Collectors.toList());
    }

    /**
     * Senaryo 2: Belirli bir durağa gelen tüm rotalardaki araçların varış sürelerini hesaplar
     */
    public List<BusArrivalInfo> calculateAllBusArrivalsForStation(Long stationId) {
        log.info("Calculating all bus arrivals for station: {}", stationId);
        
        // Bu durağa gelen tüm rotaları bul
        List<RouteStationNode> stationNodes = routeStationNodeRepository
                .findByFromStationIdOrToStationId(stationId, stationId);
        
        Set<Route> routes = stationNodes.stream()
                .map(RouteStationNode::getRoute)
                .collect(Collectors.toSet());
        
        List<BusArrivalInfo> allArrivals = new ArrayList<>();
        
        for (Route route : routes) {
            List<BusArrivalInfo> routeArrivals = calculateBusArrivals(stationId, route.getId());
            allArrivals.addAll(routeArrivals);
        }
        
        // Varış sürelerine göre sırala
        return allArrivals.stream()
                .sorted(Comparator.comparing(BusArrivalInfo::getEstimatedArrivalMinutes))
                .collect(Collectors.toList());
    }

    /**
     * Tek bir araç için varış süresini hesaplar
     */
    private BusArrivalInfo calculateSingleBusArrival(Bus bus, Long targetStationId) {
        // Son konum bilgisini al
        BusLocation lastLocation = getLastValidLocation(bus);
        if (lastLocation == null) {
            log.warn("No valid location found for bus: {}", bus.getNumberPlate());
            return null;
        }

        // Hedef durağa giden yolu bul
        List<RouteStationNode> pathToStation = findPathToStation(bus, targetStationId, lastLocation);
        if (pathToStation.isEmpty()) {
            log.warn("No path found to station {} for bus {}", targetStationId, bus.getNumberPlate());
            return null;
        }

        // Toplam seyahat süresini hesapla
        int totalMinutes = calculateTotalTravelTime(lastLocation, pathToStation);
        
        return BusArrivalInfo.builder()
                .bus(bus)
                .targetStationId(targetStationId)
                .estimatedArrivalMinutes(totalMinutes)
                .lastKnownLocation(lastLocation)
                .currentDirection(lastLocation.getDirection())
                .pathStations(pathToStation.stream()
                        .map(node -> node.getToStation().getName())
                        .collect(Collectors.toList()))
                .calculatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Son geçerli konum bilgisini getirir
     */
    private BusLocation getLastValidLocation(Bus bus) {
        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(MAX_LOCATION_AGE_MINUTES);
        
        return busLocationRepository
                .findFirstByBusAndTimestampAfterOrderByTimestampDesc(bus, cutoffTime);
    }

    /**
     * Hedefe giden yolu bulur
     */
    private List<RouteStationNode> findPathToStation(Bus bus, Long targetStationId, BusLocation currentLocation) {
        List<RouteStationNode> routeNodes = routeStationNodeRepository
                .findByRouteIdOrderBySequenceOrder(bus.getRoute().getId());
        
        // Mevcut konuma en yakın düğümü bul
        RouteStationNode currentNode = findNearestNode(routeNodes, currentLocation);
        if (currentNode == null) {
            return Collections.emptyList();
        }
        
        // Hedefe giden yolu oluştur
        List<RouteStationNode> path = new ArrayList<>();
        boolean foundCurrent = false;
        
        for (RouteStationNode node : routeNodes) {
            if (!foundCurrent && node.getId().equals(currentNode.getId())) {
                foundCurrent = true;
            }
            
            if (foundCurrent) {
                path.add(node);
                if (node.getToStation().getId().equals(targetStationId)) {
                    break;
                }
            }
        }
        
        return path;
    }

    /**
     * Mevcut konuma en yakın rota düğümünü bulur
     */
    private RouteStationNode findNearestNode(List<RouteStationNode> nodes, BusLocation location) {
        return nodes.stream()
                .min(Comparator.comparing(node -> 
                    calculateDistance(
                        location.getLatitude(), location.getLongitude(),
                        node.getFromStation().getLocation().getLatitude(),
                        node.getFromStation().getLocation().getLongitude()
                    )))
                .orElse(null);
    }

    /**
     * Toplam seyahat süresini hesaplar (Google Maps API kullanarak)
     */
    private int calculateTotalTravelTime(BusLocation startLocation, List<RouteStationNode> path) {
        if (path.isEmpty()) {
            return 0;
        }

        int totalMinutes = 0;
        double currentLat = startLocation.getLatitude();
        double currentLng = startLocation.getLongitude();

        for (RouteStationNode node : path) {
            Station toStation = node.getToStation();
            
            // Google Maps API'den seyahat süresini al
            GoogleMapsResponse response = googleMapsService.getDirections(
                currentLat, currentLng,
                toStation.getLocation().getLatitude(),
                toStation.getLocation().getLongitude()
            );
            
            if (response != null && response.isSuccess()) {
                totalMinutes += response.getDurationMinutes();
            } else {
                // API başarısız olursa mesafe tabanlı yaklaşık hesaplama
                double distance = calculateDistance(
                    currentLat, currentLng,
                    toStation.getLocation().getLatitude(),
                    toStation.getLocation().getLongitude()
                );
                totalMinutes += estimateTravelTimeFromDistance(distance);
            }
            
            // Her durakta bekleme süresi ekle
            totalMinutes += STATION_WAITING_TIME_MINUTES;
            
            // Bir sonraki segment için başlangıç noktasını güncelle
            currentLat = toStation.getLocation().getLatitude();
            currentLng = toStation.getLocation().getLongitude();
        }

        return totalMinutes;
    }

    /**
     * İki nokta arasındaki mesafeyi hesaplar (Haversine formülü)
     */
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371; // Earth radius in km

        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return R * c;
    }

    /**
     * Mesafeden yaklaşık seyahat süresini hesaplar
     */
    private int estimateTravelTimeFromDistance(double distanceKm) {
        final double AVERAGE_SPEED_KMH = 25; // Şehir içi ortalama hız
        return (int) Math.ceil((distanceKm / AVERAGE_SPEED_KMH) * 60);
    }

    /**
     * Gerçek zamanlı konum güncellemesi
     */
    public void updateBusLocation(String numberPlate, double latitude, double longitude) {
        Bus bus = busRepository.findByNumberPlate(numberPlate)
                .orElseThrow(() -> new EntityNotFoundException("Bus not found: " + numberPlate));

        BusLocation location = new BusLocation();
        location.setBus(bus);
        location.setLatitude(latitude);
        location.setLongitude(longitude);
        location.setTimestamp(LocalDateTime.now());
        
        // En yakın durağı bul ve kaydet
        Station closestStation = findClosestStation(latitude, longitude);
        if (closestStation != null) {
            location.setClosestStation(closestStation);
            double distance = calculateDistance(
                latitude, longitude,
                closestStation.getLocation().getLatitude(),
                closestStation.getLocation().getLongitude()
            );
            location.setDistanceToClosestStation(distance);
        }

        busLocationRepository.save(location);
        
        // Bus entity'deki son konum bilgisini güncelle
        bus.setCurrentLatitude(latitude);
        bus.setCurrentLongitude(longitude);
        bus.setLastLocationUpdate(LocalDateTime.now());
        bus.setLastSeenStation(closestStation);
        if (closestStation != null) {
            bus.setLastSeenStationName(closestStation.getName());
        }
        
        busRepository.save(bus);
    }

    private Station findClosestStation(double latitude, double longitude) {
        // Bu metod tüm durrakları alıp en yakınını bulacak
        // Performans için spatial index kullanılması önerilir
        return stationRepository.findAll().stream()
                .min(Comparator.comparing(station -> 
                    calculateDistance(
                        latitude, longitude,
                        station.getLocation().getLatitude(),
                        station.getLocation().getLongitude()
                    )))
                .orElse(null);
    }
}