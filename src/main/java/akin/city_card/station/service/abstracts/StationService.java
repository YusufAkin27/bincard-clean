package akin.city_card.station.service.abstracts;

import akin.city_card.admin.exceptions.AdminNotFoundException;
import akin.city_card.bus.core.response.StationDTO;
import akin.city_card.response.DataResponseMessage;
import akin.city_card.response.ResponseMessage;
import akin.city_card.station.core.request.CreateStationRequest;
import akin.city_card.station.core.request.SearchStationRequest;
import akin.city_card.station.core.request.UpdateStationRequest;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

public interface StationService {


    DataResponseMessage<StationDTO> createStation(UserDetails userDetails, CreateStationRequest request) throws AdminNotFoundException;

    DataResponseMessage<StationDTO> updateStation(String username, UpdateStationRequest request);

    DataResponseMessage<StationDTO> changeStationStatus(Long id, boolean active, String username);

    ResponseMessage deleteStation(Long id, String username);

    DataResponseMessage<List<StationDTO>> getAllStations(double latitude, double longitude);

    DataResponseMessage<StationDTO> getStationById(Long id);

    DataResponseMessage<List<StationDTO>> searchStationsByName(String name);

    DataResponseMessage<List<StationDTO>> searchNearbyStations(SearchStationRequest request);
}
