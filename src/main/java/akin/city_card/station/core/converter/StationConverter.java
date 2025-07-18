package akin.city_card.station.core.converter;

import akin.city_card.bus.core.response.StationDTO;
import akin.city_card.station.model.Station;

public interface StationConverter {
    StationDTO convertToDTO(Station station);
    StationDTO toDTO(Station station);
}
