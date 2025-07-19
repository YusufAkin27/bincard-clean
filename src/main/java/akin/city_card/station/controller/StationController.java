package akin.city_card.station.controller;

import akin.city_card.admin.exceptions.AdminNotFoundException;
import akin.city_card.bus.core.response.StationDTO;
import akin.city_card.response.DataResponseMessage;
import akin.city_card.response.ResponseMessage;
import akin.city_card.station.core.request.CreateStationRequest;
import akin.city_card.station.core.request.SearchStationRequest;
import akin.city_card.station.core.request.UpdateStationRequest;
import akin.city_card.station.service.abstracts.StationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/v1/api/station")
public class StationController {

    private final StationService stationService;

    @GetMapping
    public DataResponseMessage<List<StationDTO>> getAllStations(@RequestParam double latitude, @RequestParam double longitude) {
        return stationService.getAllStations(latitude,longitude);
    }

    @GetMapping("/{id}")
    public DataResponseMessage<StationDTO> getStationById(@PathVariable Long id) {
        return stationService.getStationById(id);
    }

    @GetMapping("/search")
    public DataResponseMessage<List<StationDTO>> searchStations(@RequestParam String name) {
        return stationService.searchStationsByName(name);
    }

    @PostMapping
    public DataResponseMessage<StationDTO> createStation(@AuthenticationPrincipal UserDetails userDetails,@RequestBody CreateStationRequest request) throws AdminNotFoundException {
        return stationService.createStation(userDetails,request);
    }

    @PutMapping("/{id}")
    public DataResponseMessage<StationDTO> updateStation(@RequestBody UpdateStationRequest request, @AuthenticationPrincipal UserDetails userDetails) {
        return stationService.updateStation(userDetails.getUsername(), request);
    }

    @PatchMapping("/{id}/status")
    public DataResponseMessage<StationDTO> changeStationStatus(@AuthenticationPrincipal UserDetails userDetails,@PathVariable Long id, @RequestParam boolean active) {
        return stationService.changeStationStatus(id, active,userDetails.getUsername());
    }

    @DeleteMapping("/{id}")
    public ResponseMessage deleteStation(@AuthenticationPrincipal UserDetails userDetails,@PathVariable Long id) {
       return stationService.deleteStation(id,userDetails.getUsername());
    }
    @PostMapping("/search/nearby")
    public DataResponseMessage<List<StationDTO>> searchNearbyStations(@RequestBody SearchStationRequest request) {
        return stationService.searchNearbyStations(request);
    }

}
