package akin.city_card.route.service.concretes;

import akin.city_card.bus.exceptions.RouteNotFoundException;
import akin.city_card.bus.model.Bus;
import akin.city_card.bus.service.abstracts.GoogleMapsService;
import akin.city_card.news.exceptions.UnauthorizedAreaException;
import akin.city_card.response.DataResponseMessage;
import akin.city_card.response.ResponseMessage;
import akin.city_card.route.core.converter.RouteConverter;
import akin.city_card.route.core.request.CreateRouteNodeRequest;
import akin.city_card.route.core.request.CreateRouteRequest;
import akin.city_card.route.core.response.NextBusDTO;
import akin.city_card.route.core.response.RouteDTO;
import akin.city_card.route.core.response.RouteNameDTO;
import akin.city_card.route.core.request.RouteSuggestionRequest;
import akin.city_card.route.core.response.RouteSuggestionResponse;
import akin.city_card.route.exceptions.RouteAlreadyFavoriteException;
import akin.city_card.route.exceptions.RouteNotActiveException;
import akin.city_card.route.model.Route;
import akin.city_card.route.model.RouteSchedule;
import akin.city_card.route.model.RouteStationNode;
import akin.city_card.route.repository.RouteRepository;
import akin.city_card.route.service.abstracts.RouteService;
import akin.city_card.security.entity.Role;
import akin.city_card.security.entity.SecurityUser;
import akin.city_card.security.exception.UserNotFoundException;
import akin.city_card.security.repository.SecurityUserRepository;
import akin.city_card.station.exceptions.StationNotFoundException;
import akin.city_card.station.model.Station;
import akin.city_card.station.repository.StationRepository;
import akin.city_card.user.model.User;
import akin.city_card.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RouteManager implements RouteService {
    private static final double MAX_DISTANCE_KM = 0.5;
    private final RouteRepository routeRepository;
    private final RouteConverter routeConverter;
    private final SecurityUserRepository securityUserRepository;
    private final StationRepository stationRepository;
    private final UserRepository userRepository;
    private final GoogleMapsService googleMapsService;

    @Override
    public DataResponseMessage<List<RouteNameDTO>> searchRoutesByName(String name) {
        List<Route> routes = routeRepository.searchByKeyword(name);
        List<RouteNameDTO> dtos = routes.stream()
                .filter(route -> route.isActive())
                .filter(route -> !route.isDeleted())
                .filter(route -> !route.getStationNodes().isEmpty())
                .map(routeConverter::toRouteNameDTO)
                .toList();

        return new DataResponseMessage<>("Arama sonuçları", true, dtos);
    }


    @Override
    public DataResponseMessage<List<RouteWithNextBusDTO>> findRoutesWithNextBus(Long stationId) throws StationNotFoundException {
        Station station = stationRepository.findById(stationId)
                .orElseThrow(StationNotFoundException::new);

        List<Route> routes = routeRepository.findRoutesByStation(stationId);
        List<RouteWithNextBusDTO> result = new ArrayList<>();

        for (Route route : routes) {
            // Hem gidiş hem dönüş otobüslerini birleştir
            List<Bus> buses = new ArrayList<>();
            if (route.getBuses() != null) {
                buses.addAll(route.getBuses());
            }

            // Filtrele: aktif ve silinmemiş otobüsler
            buses = buses.stream()
                    .filter(Bus::isActive)
                    .filter(bus -> !bus.isDeleted())
                    .toList();

            Bus fastestBus = null;
            Integer minEta = Integer.MAX_VALUE;

            for (Bus bus : buses) {
                Double lat = bus.getCurrentLatitude();
                Double lon = bus.getCurrentLongitude();

                if (lat == null || lon == null) continue;

                Integer eta = googleMapsService.getEstimatedTimeInMinutes(
                        lat, lon,
                        station.getLocation().getLatitude(),
                        station.getLocation().getLongitude()
                );

                if (eta != null && eta < 60 && eta < minEta) {
                    minEta = eta;
                    fastestBus = bus;
                }
            }

            RouteWithNextBusDTO dto = routeConverter.toRouteWithNextBusDTO(route);
            if (fastestBus != null) {
                dto.setNextBus(new NextBusDTO(fastestBus.getNumberPlate(), minEta));
            } else {
                dto.setNextBus(null);
            }

            result.add(dto);
        }

        return new DataResponseMessage<>("Duraktan geçen rotalar", true, result);
    }



    @Override
    public ResponseMessage createRoute(String username, CreateRouteRequest request)
            throws UnauthorizedAreaException, StationNotFoundException {

        // 1. Kullanıcı doğrulama
        SecurityUser securityUser = securityUserRepository.findByUserNumber(username)
                .orElseThrow(UnauthorizedAreaException::new);

        boolean isAdmin = securityUser.getRoles().contains(Role.ADMIN) ||
                securityUser.getRoles().contains(Role.SUPERADMIN);
        if (!isAdmin) {
            throw new UnauthorizedAreaException();
        }

        // 2. Başlangıç ve bitiş duraklarının kontrolü
        Station startStation = stationRepository.findById(request.getStartStationId())
                .orElseThrow(StationNotFoundException::new);
        Station endStation = stationRepository.findById(request.getEndStationId())
                .orElseThrow(StationNotFoundException::new);

        // 3. Route nesnesi oluşturuluyor
        Route route = new Route();
        route.setName(request.getRouteName());
        route.setStartStation(startStation);
        route.setEndStation(endStation);
        route.setCreatedAt(LocalDateTime.now());
        route.setUpdatedAt(LocalDateTime.now());
        route.setActive(true);
        route.setDeleted(false);
        route.setCreatedBy(securityUser);
        route.setUpdatedBy(securityUser);

        // 4. RouteSchedule set ediliyor
        RouteSchedule schedule = new RouteSchedule();
        schedule.setWeekdayHours(request.getWeekdayHours());
        schedule.setWeekendHours(request.getWeekendHours());
        route.setSchedule(schedule);

        // 5. RouteStationNode listesi hazırlanıyor
        List<RouteStationNode> nodeList = new ArrayList<>();
        int order = 0;

        for (CreateRouteNodeRequest nodeRequest : request.getRouteNodes()) {
            Station fromStation = stationRepository.findById(nodeRequest.getFromStationId())
                    .orElseThrow(StationNotFoundException::new);
            Station toStation = stationRepository.findById(nodeRequest.getToStationId())
                    .orElseThrow(StationNotFoundException::new);

            RouteStationNode node = new RouteStationNode();
            node.setRoute(route);
            node.setFromStation(fromStation);
            node.setToStation(toStation);
            node.setSequenceOrder(order++);

            nodeList.add(node);
        }

        route.setStationNodes(nodeList);

        routeRepository.save(route);

        return new ResponseMessage("Rota başarıyla oluşturuldu.", true);


    }


    @Override
    @Transactional
    public ResponseMessage deleteRoute(String username, Long id) throws UnauthorizedAreaException, RouteNotFoundException {
        Optional<SecurityUser> securityUser = securityUserRepository.findByUserNumber(username);

        if (!securityUser.get().getRoles().contains(Role.ADMIN) &&
                !securityUser.get().getRoles().contains(Role.SUPERADMIN)) {
            throw new UnauthorizedAreaException();
        }
        Route route = routeRepository.findById(id).orElseThrow(RouteNotFoundException::new);
        route.setDeletedAt(LocalDateTime.now());
        route.setDeleted(true);
        route.setActive(false);
        route.setDeletedBy(securityUser.get());

        return new ResponseMessage("Rota kaldırıldı", true);
    }


    @Override
    public DataResponseMessage<RouteDTO> getRouteById(Long id) throws RouteNotFoundException {
        return new DataResponseMessage<>("rota ", true, routeConverter.toRouteDTO(routeRepository.findById(id).orElseThrow(RouteNotFoundException::new)));
    }

    @Override
    public DataResponseMessage<List<RouteNameDTO>> getAllRoutes() {
        List<RouteNameDTO> activeRoutes = routeRepository.findAll().stream()
                .filter(route -> route.isActive() && !route.isDeleted())
                .filter(route -> route.getStationNodes() != null && !route.getStationNodes().isEmpty())
                .map(routeConverter::toRouteNameDTO)
                .toList();

        return new DataResponseMessage<>("Aktif rotalar başarıyla listelendi.", true, activeRoutes);
    }


    @Override
    public DataResponseMessage<RouteDTO> addStationToRoute(Long routeId, Long afterStationId, Long newStationId, String username) throws StationNotFoundException, RouteNotFoundException {
        Optional<SecurityUser> user = securityUserRepository.findByUserNumber(username);
        Route route = routeRepository.findById(routeId)
                .orElseThrow(RouteNotFoundException::new);
        Station afterStation = stationRepository.findById(afterStationId)
                .orElseThrow(StationNotFoundException::new);
        Station newStation = stationRepository.findById(newStationId)
                .orElseThrow(StationNotFoundException::new);

        // 1. A → B şeklinde bağlantıyı bul
        RouteStationNode oldEdge = route.getStationNodes().stream()
                .filter(node -> node.getFromStation().equals(afterStation))
                .findFirst()
                .orElseThrow(StationNotFoundException::new);

        Station toStation = oldEdge.getToStation();

        // 2. Eski bağlantıyı sil
        route.getStationNodes().remove(oldEdge);

        // 3. Yeni node’ları oluştur
        RouteStationNode node1 = new RouteStationNode();
        node1.setRoute(route);
        node1.setFromStation(afterStation);
        node1.setToStation(newStation);
        node1.setSequenceOrder(oldEdge.getSequenceOrder());

        RouteStationNode node2 = new RouteStationNode();
        node2.setRoute(route);
        node2.setFromStation(newStation);
        node2.setToStation(toStation);
        node2.setSequenceOrder(oldEdge.getSequenceOrder() + 1);

        route.getStationNodes().add(node1);
        route.getStationNodes().add(node2);

        route.setUpdatedAt(LocalDateTime.now());
        route.setUpdatedBy(user.get());

        routeRepository.save(route);

        return new DataResponseMessage<>("Station added successfully", true, routeConverter.toRouteDTO(route));
    }


    @Override
    public DataResponseMessage<RouteDTO> removeStationFromRoute(Long routeId, Long stationId, String username) throws RouteNotFoundException, StationNotFoundException {
        Optional<SecurityUser> user = securityUserRepository.findByUserNumber(username);
        Route route = routeRepository.findById(routeId)
                .orElseThrow(RouteNotFoundException::new);
        Station stationToRemove = stationRepository.findById(stationId)
                .orElseThrow(StationNotFoundException::new);

        // 1. Bu durağa gelen bağlantı (A → stationToRemove)
        Optional<RouteStationNode> incomingOpt = route.getStationNodes().stream()
                .filter(n -> n.getToStation().equals(stationToRemove))
                .findFirst();

        // 2. Bu duraktan çıkan bağlantı (stationToRemove → C)
        Optional<RouteStationNode> outgoingOpt = route.getStationNodes().stream()
                .filter(n -> n.getFromStation().equals(stationToRemove))
                .findFirst();

        if (incomingOpt.isPresent() && outgoingOpt.isPresent()) {
            RouteStationNode incoming = incomingOpt.get();
            RouteStationNode outgoing = outgoingOpt.get();

            // yeni bağlantı: A → C
            RouteStationNode newNode = new RouteStationNode();
            newNode.setRoute(route);
            newNode.setFromStation(incoming.getFromStation());
            newNode.setToStation(outgoing.getToStation());
            newNode.setSequenceOrder(incoming.getSequenceOrder());

            route.getStationNodes().remove(incoming);
            route.getStationNodes().remove(outgoing);
            route.getStationNodes().add(newNode);
        } else {
            route.getStationNodes().removeIf(n ->
                    n.getFromStation().equals(stationToRemove) ||
                            n.getToStation().equals(stationToRemove));
        }

        route.setUpdatedAt(LocalDateTime.now());
        route.setUpdatedBy(user.get());

        routeRepository.save(route);

        return new DataResponseMessage<>("Station removed and nodes updated", true, routeConverter.toRouteDTO(route));
    }

    @Override
    @Transactional
    public ResponseMessage addFavorite(String username, Long routeId) throws RouteNotActiveException, UserNotFoundException, RouteNotFoundException, RouteAlreadyFavoriteException {
        User user = userRepository.findByUserNumber(username).orElseThrow(UserNotFoundException::new);
        Route route = routeRepository.findById(routeId).orElseThrow(RouteNotFoundException::new);
        if (!route.isActive() || route.isDeleted()) {
            throw new RouteNotActiveException();
        }
        if (user.getFavoriteRoutes().isEmpty()) {
            List<Route> routes = new ArrayList<>();
            user.setFavoriteRoutes(routes);
        }
        boolean isPresent = user.getFavoriteRoutes().stream().anyMatch(route1 -> route1.getId().equals(route.getId()));

        if (isPresent) {
            throw new RouteAlreadyFavoriteException();
        }
        user.getFavoriteRoutes().add(route);
        return new ResponseMessage("Rota eklendi", true);
    }

    @Override
    @Transactional
    public ResponseMessage removeFavorite(String username, Long routeId) throws RouteNotFoundException, UserNotFoundException, RouteNotActiveException {
        User user = userRepository.findByUserNumber(username).orElseThrow(UserNotFoundException::new);
        Route route = routeRepository.findById(routeId).orElseThrow(RouteNotFoundException::new);
        if (!route.isActive() || route.isDeleted()) {
            throw new RouteNotActiveException();
        }
        if (user.getFavoriteRoutes().isEmpty()) {
            List<Route> routes = new ArrayList<>();
            user.setFavoriteRoutes(routes);
        }
        boolean isDeleted = user.getFavoriteRoutes().removeIf(route1 -> route1.getId().equals(route.getId()));
        return new ResponseMessage("Rota silme  " + isDeleted, true);
    }

    @Override
    public DataResponseMessage<List<RouteNameDTO>> favotiteRoutes(String username) throws UserNotFoundException {
        User user = userRepository.findByUserNumber(username).orElseThrow(UserNotFoundException::new);
        return new DataResponseMessage<>("favoriler", true, user.getFavoriteRoutes().stream().filter(Route::isActive).map(routeConverter::toRouteNameDTO).toList());

    }

    @Override
    public DataResponseMessage<RouteSuggestionResponse> suggestRoute(RouteSuggestionRequest request) {
        log.info("Route suggestion started for user location: ({}, {}) and destination: {}",
                request.getUserLat(), request.getUserLng(), request.getDestinationAddress());

        // 1. Hedef konumu Google Maps API ile al
        GoogleMapsService.LatLng destinationLatLng = googleMapsService.getCoordinatesFromAddress(request.getDestinationAddress());
        if (destinationLatLng == null) {
            log.warn("Destination address '{}' could not be resolved to coordinates.", request.getDestinationAddress());
            return new DataResponseMessage<>("Hedef adres bulunamadı.", false, null);
        }
        log.info("Destination coordinates: lat={}, lng={}", destinationLatLng.lat(), destinationLatLng.lng());

        // 2. Kullanıcıya yakın durakları filtrele
        List<Station> userNearbyStations = stationRepository.findAll().stream()
                .filter(st -> isWithinRadius(request.getUserLat(), request.getUserLng(),
                        st.getLocation().getLatitude(), st.getLocation().getLongitude(), MAX_DISTANCE_KM))
                .toList();
        log.info("User nearby stations found: {}", userNearbyStations.size());
        userNearbyStations.forEach(st -> log.debug("User nearby station: {} at ({}, {})",
                st.getName(), st.getLocation().getLatitude(), st.getLocation().getLongitude()));

        // 3. Hedefe yakın durakları filtrele
        List<Station> destinationNearbyStations = stationRepository.findAll().stream()
                .filter(st -> isWithinRadius(destinationLatLng.lat(), destinationLatLng.lng(),
                        st.getLocation().getLatitude(), st.getLocation().getLongitude(), MAX_DISTANCE_KM))
                .toList();
        log.info("Destination nearby stations found: {}", destinationNearbyStations.size());
        destinationNearbyStations.forEach(st -> log.debug("Destination nearby station: {} at ({}, {})",
                st.getName(), st.getLocation().getLatitude(), st.getLocation().getLongitude()));

        // 4. Ortak rotaları bul
        for (Station userStation : userNearbyStations) {
            for (Station destStation : destinationNearbyStations) {
                log.debug("Checking routes between user station '{}' and destination station '{}'",
                        userStation.getName(), destStation.getName());

                List<Route> routes = routeRepository.findRoutesByStations(userStation.getId(), destStation.getId());
                if (!routes.isEmpty()) {
                    Route matchedRoute = routes.get(0);
                    log.info("Route found: '{}' between stations '{}' and '{}'", matchedRoute.getName(),
                            userStation.getName(), destStation.getName());

                    RouteSuggestionResponse response = RouteSuggestionResponse.builder()
                            .routeFound(true)
                            .message("Rota bulundu.")
                            .routeName(matchedRoute.getName())
                            .boardAt(userStation.getName())
                            .getOffAt(destStation.getName())
                            .googleMapUrl("https://maps.google.com/?q=" +
                                    destStation.getLocation().getLatitude() + "," +
                                    destStation.getLocation().getLongitude())
                            .build();

                    return new DataResponseMessage<>("Başarılı", true, response);
                } else {
                    log.debug("No routes found between '{}' and '{}'", userStation.getName(), destStation.getName());
                }
            }
        }

        // Rota bulunamadıysa
        log.warn("No suitable route found for user location ({}, {}) to destination '{}'",
                request.getUserLat(), request.getUserLng(), request.getDestinationAddress());
        return new DataResponseMessage<>("Uygun rota bulunamadı.", false, null);
    }

    private boolean isWithinRadius(double lat1, double lng1, double lat2, double lng2, double radiusKm) {
        double dist = distanceKm(lat1, lng1, lat2, lng2);
        log.debug("Distance between ({},{}) and ({},{}) = {} km (max allowed: {})",
                lat1, lng1, lat2, lng2, dist, radiusKm);
        return dist <= radiusKm;
    }

    private double distanceKm(double lat1, double lng1, double lat2, double lng2) {
        double R = 6371; // Dünya yarıçapı km
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }


}
