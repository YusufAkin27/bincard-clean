package akin.city_card.station.service.concretes;

import akin.city_card.admin.exceptions.AdminNotFoundException;
import akin.city_card.admin.model.Admin;
import akin.city_card.bus.core.response.StationDTO;
import akin.city_card.paymentPoint.model.Address;
import akin.city_card.paymentPoint.model.Location;
import akin.city_card.response.DataResponseMessage;
import akin.city_card.response.ResponseMessage;
import akin.city_card.security.repository.SecurityUserRepository;
import akin.city_card.station.core.converter.StationConverter;
import akin.city_card.station.core.request.CreateStationRequest;
import akin.city_card.station.core.request.SearchStationRequest;
import akin.city_card.station.core.request.UpdateStationRequest;
import akin.city_card.station.model.Station;
import akin.city_card.station.repository.StationRepository;
import akin.city_card.station.service.abstracts.StationService;
import akin.city_card.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StationManager implements StationService {
    private final StationRepository stationRepository;
    private final UserRepository userRepository;
    private final SecurityUserRepository securityUserRepository;
    private final StationConverter stationConverter;

    @Override
    public DataResponseMessage<StationDTO> createStation(UserDetails userDetails, CreateStationRequest request) throws AdminNotFoundException {
        if (!isAdminOrSuperAdmin(userDetails)) {
            throw new SecurityException("Yetkiniz yok.");
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
                .createdBy((Admin) securityUserRepository.findByUserNumber(userDetails.getUsername())
                        .orElseThrow(AdminNotFoundException::new))
                .createdDate(LocalDateTime.now())
                .updatedDate(LocalDateTime.now())
                .build();

        station = stationRepository.save(station);
        return new DataResponseMessage<>("Durak başarıyla eklendi.", true, stationConverter.toDTO(station));
    }

    @Override
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
    public DataResponseMessage<StationDTO> changeStationStatus(Long id, boolean active, String username) {
        Station station = stationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Durak bulunamadı."));
        station.setActive(active);
        station.setUpdatedDate(LocalDateTime.now());
        stationRepository.save(station);
        return new DataResponseMessage<>("Durak durumu güncellendi.", true, stationConverter.toDTO(station));
    }

    @Override
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
    public DataResponseMessage<List<StationDTO>> getAllStations(double latitude, double longitude) {
        List<Station> stations = stationRepository.findAll()
                .stream()
                .filter(s -> !s.isDeleted() && s.isActive())
                .sorted((s1, s2) -> Double.compare(
                        distance(latitude, longitude, s1.getLocation().getLatitude(), s1.getLocation().getLongitude()),
                        distance(latitude, longitude, s2.getLocation().getLatitude(), s2.getLocation().getLongitude())
                ))
                .toList();

        List<StationDTO> result = stations.stream().map(stationConverter::toDTO).collect(Collectors.toList());
        return new DataResponseMessage<>("Duraklar başarıyla listelendi.", true, result);
    }

    @Override
    public DataResponseMessage<StationDTO> getStationById(Long id) {
        Station station = stationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Durak bulunamadı."));
        return new DataResponseMessage<>("Durak bulundu.", true, stationConverter.toDTO(station));
    }

    @Override
    public DataResponseMessage<List<StationDTO>> searchStationsByName(String name) {
        List<Station> stations = stationRepository.findByNameContainingIgnoreCase(name);
        List<StationDTO> result = stations.stream().map(stationConverter::toDTO).collect(Collectors.toList());
        return new DataResponseMessage<>("Arama sonucu listelendi.", true, result);
    }

    @Override
    public DataResponseMessage<List<StationDTO>> searchNearbyStations(SearchStationRequest request) {
        double radiusKm = 5.0;
        double lat = request.getLatitude();
        double lon = request.getLongitude();
        String query = request.getQuery().toLowerCase();

        List<Station> allStations = stationRepository.findAll();

        List<StationDTO> nearbyStations = allStations.stream()
                .filter(station -> station.isActive() && !station.isDeleted())
                .filter(station -> {
                    double stationLat = station.getLocation().getLatitude();
                    double stationLon = station.getLocation().getLongitude();
                    double distance = haversine(lat, lon, stationLat, stationLon);
                    return distance <= radiusKm &&
                            (station.getName().toLowerCase().contains(query));
                })
                .map(stationConverter::convertToDTO)
                .toList();

        return new DataResponseMessage<>("Yakındaki istasyonlar başarıyla getirildi.", true, nearbyStations);
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



    // Haversine formülü ile mesafe hesabı (km cinsinden)
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
