package akin.city_card.station.service.concretes;

import akin.city_card.admin.exceptions.AdminNotFoundException;
import akin.city_card.bus.core.response.StationDTO;
import akin.city_card.news.core.response.PageDTO;
import akin.city_card.news.exceptions.UnauthorizedAreaException;
import akin.city_card.paymentPoint.model.Address;
import akin.city_card.paymentPoint.model.Location;
import akin.city_card.response.DataResponseMessage;
import akin.city_card.response.ResponseMessage;
import akin.city_card.route.core.converter.RouteConverter;
import akin.city_card.route.core.response.PublicRouteDTO;
import akin.city_card.route.core.response.RouteDTO;
import akin.city_card.route.model.Route;
import akin.city_card.route.repository.RouteRepository;
import akin.city_card.security.repository.SecurityUserRepository;
import akin.city_card.station.core.converter.StationConverter;
import akin.city_card.station.core.request.CreateStationRequest;
import akin.city_card.station.core.request.SearchStationRequest;
import akin.city_card.station.core.request.UpdateStationRequest;
import akin.city_card.station.exceptions.StationNotActiveException;
import akin.city_card.station.exceptions.StationNotFoundException;
import akin.city_card.station.model.Station;
import akin.city_card.station.model.StationType;
import akin.city_card.station.repository.StationRepository;
import akin.city_card.station.service.abstracts.StationService;
import akin.city_card.user.model.User;
import akin.city_card.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StationManager implements StationService {
    private final StationRepository stationRepository;
    private final UserRepository userRepository;
    private final SecurityUserRepository securityUserRepository;
    private final StationConverter stationConverter;
    private final RouteRepository routeRepository;
    private final RouteConverter routeConverter;

    @Override
    @Transactional
    public DataResponseMessage<StationDTO> createStation(UserDetails userDetails, CreateStationRequest request) throws AdminNotFoundException, UnauthorizedAreaException {
        if (!isAdminOrSuperAdmin(userDetails)) {
            throw new UnauthorizedAreaException();
        }
        Station station = Station.builder()
                .name(request.getName())
                .type(request.getType())
                .location(new Location(request.getLatitude(), request.getLongitude()))
                .address(Address.builder()
                        .city(request.getCity())
                        .district(request.getDistrict())
                        .street(request.getStreet())
                        .postalCode(request.getPostalCode())
                        .build())
                .active(true)
                .deleted(false)
                .createdBy(securityUserRepository.findByUserNumber(userDetails.getUsername())
                        .orElseThrow(AdminNotFoundException::new))
                .createdDate(LocalDateTime.now())
                .updatedDate(LocalDateTime.now())
                .build();

        station = stationRepository.save(station);
        return new DataResponseMessage<>("Durak başarıyla eklendi.", true, stationConverter.toDTO(station));
    }

    @Override
    @Transactional
    public DataResponseMessage<StationDTO> updateStation(String username, UpdateStationRequest request) {
        Station station = stationRepository.findById(request.getId())
                .orElseThrow(() -> new EntityNotFoundException("Durak bulunamadı."));

        if (request.getName() != null) station.setName(request.getName());
        if (request.getLatitude() != null && request.getLongitude() != null) {
            station.setLocation(new Location(request.getLatitude(), request.getLongitude()));
        }
        if (request.getType() != null) station.setType(request.getType());

        if (station.getAddress() == null) station.setAddress(new Address());

        if (request.getCity() != null) station.getAddress().setCity(request.getCity());
        if (request.getDistrict() != null) station.getAddress().setDistrict(request.getDistrict());
        if (request.getStreet() != null) station.getAddress().setStreet(request.getStreet());
        if (request.getPostalCode() != null) station.getAddress().setPostalCode(request.getPostalCode());

        if (request.getActive() != null) station.setActive(request.getActive());

        station.setUpdatedDate(LocalDateTime.now());
        stationRepository.save(station);

        return new DataResponseMessage<>("Durak başarıyla güncellendi.", true, stationConverter.toDTO(station));
    }

    @Override
    @Transactional
    public DataResponseMessage<StationDTO> changeStationStatus(Long id, boolean active, String username) {
        Station station = stationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Durak bulunamadı."));
        station.setActive(active);
        station.setUpdatedDate(LocalDateTime.now());
        stationRepository.save(station);
        return new DataResponseMessage<>("Durak durumu güncellendi.", true, stationConverter.toDTO(station));
    }

    @Override
    @Transactional
    public ResponseMessage deleteStation(Long id, String username) {
        Station station = stationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Durak bulunamadı."));
        station.setDeleted(true);
        station.setActive(false);
        station.setUpdatedDate(LocalDateTime.now());
        stationRepository.save(station);
        return new ResponseMessage("Durak başarıyla silindi.", true);
    }

    @Override
    public DataResponseMessage<PageDTO<StationDTO>> getAllStations(double latitude, double longitude, StationType type, int page, int size) {
        List<Station> stations = stationRepository.findAll()
                .stream()
                .filter(s -> !s.isDeleted() && s.isActive())
                .filter(type != null ? s -> s.getType().equals(type) : s -> true)
                .sorted((s1, s2) -> Double.compare(
                        distance(latitude, longitude, s1.getLocation().getLatitude(), s1.getLocation().getLongitude()),
                        distance(latitude, longitude, s2.getLocation().getLatitude(), s2.getLocation().getLongitude())
                ))
                .toList();

        int totalElements = stations.size();
        int totalPages = (int) Math.ceil((double) totalElements / size);

        int fromIndex = Math.min(page * size, totalElements);
        int toIndex = Math.min(fromIndex + size, totalElements);

        List<StationDTO> pagedList = stations.subList(fromIndex, toIndex)
                .stream()
                .map(stationConverter::toDTO)
                .collect(Collectors.toList());

        PageDTO<StationDTO> pageDTO = new PageDTO<>();
        pageDTO.setContent(pagedList);
        pageDTO.setPageNumber(page);
        pageDTO.setPageSize(size);
        pageDTO.setTotalElements(totalElements);
        pageDTO.setTotalPages(totalPages);
        pageDTO.setFirst(page == 0);
        pageDTO.setLast(page + 1 >= totalPages);

        return new DataResponseMessage<>("Duraklar başarıyla listelendi.", true, pageDTO);
    }


    @Override
    public DataResponseMessage<StationDTO> getStationById(Long id) {
        Station station = stationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Durak bulunamadı."));
        return new DataResponseMessage<>("Durak bulundu.", true, stationConverter.toDTO(station));
    }

    @Override
    public DataResponseMessage<PageDTO<StationDTO>> searchStationsByName(String name, int page, int size) {
        String query = name != null ? name.toLowerCase() : "";

        List<Station> stations = stationRepository.findAll().stream()
                .filter(station -> station.isActive() && !station.isDeleted())
                .filter(station -> {
                    String stationName = station.getName() != null ? station.getName().toLowerCase() : "";
                    String street = station.getAddress() != null && station.getAddress().getStreet() != null
                            ? station.getAddress().getStreet().toLowerCase() : "";
                    String district = station.getAddress() != null && station.getAddress().getDistrict() != null
                            ? station.getAddress().getDistrict().toLowerCase() : "";
                    String city = station.getAddress() != null && station.getAddress().getCity() != null
                            ? station.getAddress().getCity().toLowerCase() : "";

                    return stationName.contains(query) || street.contains(query) || district.contains(query) || city.contains(query);
                })
                .toList();

        int total = stations.size();
        int totalPages = (int) Math.ceil((double) total / size);
        int fromIndex = Math.min(page * size, total);
        int toIndex = Math.min(fromIndex + size, total);

        List<StationDTO> content = stations.subList(fromIndex, toIndex)
                .stream()
                .map(stationConverter::toDTO)
                .collect(Collectors.toList());

        PageDTO<StationDTO> pageDTO = new PageDTO<>();
        pageDTO.setContent(content);
        pageDTO.setPageNumber(page);
        pageDTO.setPageSize(size);
        pageDTO.setTotalElements(total);
        pageDTO.setTotalPages(totalPages);
        pageDTO.setFirst(page == 0);
        pageDTO.setLast(page + 1 >= totalPages);

        return new DataResponseMessage<>("İstasyon arama sonuçları başarıyla listelendi.", true, pageDTO);
    }


    @Override
    public DataResponseMessage<PageDTO<StationDTO>> searchNearbyStations(SearchStationRequest request, int page, int size) {
        double radiusKm = 5.0;
        double lat = request.getLatitude();
        double lon = request.getLongitude();
        String query = request.getQuery() != null ? request.getQuery().toLowerCase() : "";

        List<Station> allStations = stationRepository.findAll();

        List<Station> matchedStations = allStations.stream()
                .filter(station -> station.isActive() && !station.isDeleted())
                .filter(station -> {
                    String name = station.getName() != null ? station.getName().toLowerCase() : "";
                    String street = station.getAddress().getStreet() != null ? station.getAddress().getStreet().toLowerCase() : "";
                    String district = station.getAddress().getDistrict() != null ? station.getAddress().getDistrict().toLowerCase() : "";
                    String city = station.getAddress().getCity() != null ? station.getAddress().getCity().toLowerCase() : "";

                    return name.contains(query) ||
                            street.contains(query) ||
                            district.contains(query) ||
                            city.contains(query);
                })
                .toList();

        if (matchedStations.isEmpty() && lat != 0 && lon != 0) {
            matchedStations = allStations.stream()
                    .filter(station -> station.isActive() && !station.isDeleted())
                    .filter(station -> {
                        double stationLat = station.getLocation().getLatitude();
                        double stationLon = station.getLocation().getLongitude();
                        return haversine(lat, lon, stationLat, stationLon) <= radiusKm;
                    })
                    .toList();
        }

        // Sayfalama
        int total = matchedStations.size();
        int totalPages = (int) Math.ceil((double) total / size);
        int fromIndex = Math.min(page * size, total);
        int toIndex = Math.min(fromIndex + size, total);

        List<StationDTO> content = matchedStations.subList(fromIndex, toIndex)
                .stream()
                .map(stationConverter::toDTO)
                .toList();

        PageDTO<StationDTO> pageDTO = new PageDTO<>();
        pageDTO.setContent(content);
        pageDTO.setPageNumber(page);
        pageDTO.setPageSize(size);
        pageDTO.setTotalElements(total);
        pageDTO.setTotalPages(totalPages);
        pageDTO.setFirst(page == 0);
        pageDTO.setLast(page + 1 >= totalPages);

        return new DataResponseMessage<>("İstasyonlar başarıyla listelendi.", true, pageDTO);
    }

    @Override
    public DataResponseMessage<List<StationDTO>> getFavorite(String username) {
        User user = userRepository.findByUserNumber(username).orElseThrow(EntityNotFoundException::new);
        return new DataResponseMessage<>("favoriler", true, user.getFavoriteStations().stream().map(stationConverter::toDTO).collect(Collectors.toList()));
    }

    @Override
    @Transactional
    public ResponseMessage removeFavoriteStation(String username, Long stationId) {
        User user = userRepository.findByUserNumber(username).orElseThrow(EntityNotFoundException::new);
        boolean isDeleted = user.getFavoriteStations().removeIf(station -> station.getId().equals(stationId));
        return new ResponseMessage("silme işlemi :" + isDeleted, true);
    }

    @Override
    @Transactional
    public ResponseMessage addFavoriteStation(String username, Long stationId) throws StationNotFoundException, StationNotActiveException {
        User user = userRepository.findByUserNumber(username).orElseThrow(EntityNotFoundException::new);
        if (user.getFavoriteStations().isEmpty()) {
            List<Station> stations = new ArrayList<>();
            user.setFavoriteStations(stations);
        }
        if ((long) user.getFavoriteStations().size() > 10) {
            return new ResponseMessage("Daha fazla durak favorilere eklenemez", false);
        }
        Station station = stationRepository.findById(stationId).orElseThrow(StationNotFoundException::new);
        if (!station.isActive() || station.isDeleted()) {
            throw new StationNotActiveException();
        }
        user.getFavoriteStations().add(station);
        userRepository.save(user);

        return new ResponseMessage("Durak favorilere eklendi.", true);
    }

    @Override
    public Set<String> getMatchingKeywords(String query) {
        String lowerQuery = query.toLowerCase();

        return stationRepository.findAll().stream()
                .filter(station -> station.isActive() && !station.isDeleted())
                .flatMap(station -> {
                    Set<String> keywords = new HashSet<>();

                    if (station.getName() != null) {
                        keywords.addAll(splitWords(station.getName()));
                    }

                    if (station.getAddress() != null) {
                        if (station.getAddress().getStreet() != null)
                            keywords.addAll(splitWords(station.getAddress().getStreet()));
                        if (station.getAddress().getDistrict() != null)
                            keywords.addAll(splitWords(station.getAddress().getDistrict()));
                        if (station.getAddress().getCity() != null)
                            keywords.addAll(splitWords(station.getAddress().getCity()));
                    }

                    return keywords.stream();
                })
                .filter(word -> word.toLowerCase().contains(lowerQuery))
                .collect(Collectors.toSet());
    }

    @Override
    public DataResponseMessage<List<PublicRouteDTO>> getRoutes(Long stationId) throws StationNotFoundException {
        Station station = stationRepository.findById(stationId)
                .orElseThrow(StationNotFoundException::new);

        List<Route> allRoutes = routeRepository.findAll();

        Set<Route> matchedRoutes = allRoutes.stream()
                .filter(route -> route.getStationNodes().stream()
                        .anyMatch(node -> node.getFromStation().getId().equals(station.getId()))
                )
                .filter(route -> route.isActive() && !route.isDeleted())
                .collect(Collectors.toSet());

        List<PublicRouteDTO> result = matchedRoutes.stream()
                .map(routeConverter::toPublicRoute)
                .toList();

        return new DataResponseMessage<>("Rotalar başarıyla listelendi.", true, result);
    }

    @Override
    public DataResponseMessage<PageDTO<StationDTO>> NearbyStations(double userLat, double userLon, int page, int size) {
        double radiusKm = 5.0;

        List<Station> nearbyStations = stationRepository.findAll().stream()
                .filter(station -> station.isActive() && !station.isDeleted())
                .filter(station -> {
                    double stationLat = station.getLocation().getLatitude();
                    double stationLon = station.getLocation().getLongitude();
                    double distance = haversine(userLat, userLon, stationLat, stationLon);
                    return distance <= radiusKm;
                })
                .sorted(Comparator.comparingDouble(station ->
                        haversine(userLat, userLon,
                                station.getLocation().getLatitude(),
                                station.getLocation().getLongitude())))
                .toList();

        int totalElements = nearbyStations.size();
        int totalPages = (int) Math.ceil((double) totalElements / size);
        int fromIndex = page * size;
        int toIndex = Math.min(fromIndex + size, totalElements);

        List<StationDTO> pagedStations = nearbyStations.subList(fromIndex, toIndex)
                .stream()
                .map(stationConverter::toDTO)
                .toList();

        PageDTO<StationDTO> pageDTO = new PageDTO<>();
        pageDTO.setContent(pagedStations);
        pageDTO.setPageNumber(page);
        pageDTO.setPageSize(size);
        pageDTO.setTotalElements(totalElements);
        pageDTO.setTotalPages(totalPages);
        pageDTO.setFirst(page == 0);
        pageDTO.setLast(page + 1 >= totalPages);

        return new DataResponseMessage<>("Yakındaki duraklar başarıyla listelendi.", true, pageDTO);
    }



    private Set<String> splitWords(String text) {
        if (text == null) return Set.of();

        String[] parts = text.split("[\\s,.:;-]+");

        return Arrays.stream(parts)
                .filter(part -> !part.isBlank())
                .collect(Collectors.toSet());
    }


    private double haversine(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // Dünya yarıçapı km
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }


    // Helper methods

    private boolean isAdminOrSuperAdmin(UserDetails userDetails) {
        return userDetails.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ADMIN") || auth.getAuthority().equals("SUPERADMIN"));
    }


    private double distance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // Dünya yarıçapı (km)
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a =
                Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                        Math.cos(Math.toRadians(lat1)) *
                                Math.cos(Math.toRadians(lat2)) *
                                Math.sin(dLon / 2) *
                                Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}
