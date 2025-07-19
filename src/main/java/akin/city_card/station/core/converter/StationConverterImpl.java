package akin.city_card.station.core.converter;

import akin.city_card.bus.core.response.StationDTO;
import akin.city_card.station.model.Station;
import org.springframework.stereotype.Component;

@Component
public class StationConverterImpl implements StationConverter {
    @Override
    public StationDTO convertToDTO(Station station) {
        return StationDTO.builder()
                .id(station.getId())
                .name(station.getName())
                .latitude(station.getLocation().getLatitude())
                .longitude(station.getLocation().getLongitude())
                .build();
    }

    @Override
    public StationDTO toDTO(Station station) {
        return StationDTO.builder()
                .id(station.getId())
                .name(station.getName())
                .latitude(station.getLocation().getLatitude())
                .longitude(station.getLocation().getLongitude())
                .type(station.getType().name())
                .active(station.isActive())
                .city(station.getAddress().getCity())
                .district(station.getAddress().getDistrict())
                .street(station.getAddress().getStreet())
                .postalCode(station.getAddress().getPostalCode())
                .build();
    }
}
