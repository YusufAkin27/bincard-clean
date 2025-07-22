package akin.city_card.bus.controller;

import akin.city_card.bus.service.abstracts.BusArrivalInfo;
import akin.city_card.bus.service.abstracts.BusTrackingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/api/tracking")
@RequiredArgsConstructor
public class BusTrackingController {

    private final BusTrackingService busTrackingService;

    /**
     * Senaryo 1: Belirli durak ve rota için araç varış sürelerini getir
     */
    @GetMapping("/arrivals/station/{stationId}/route/{routeId}")
    public ResponseEntity<List<BusArrivalInfo>> getBusArrivals(
            @PathVariable Long stationId,
            @PathVariable Long routeId) {
        
        List<BusArrivalInfo> arrivals = busTrackingService.calculateBusArrivals(stationId, routeId);
        return ResponseEntity.ok(arrivals);
    }





    /**
     * Senaryo 2: Belirli durak için tüm araç varış sürelerini getir
     */
    @GetMapping("/arrivals/station/{stationId}")
    public ResponseEntity<List<BusArrivalInfo>> getAllBusArrivalsForStation(@PathVariable Long stationId) {
        List<BusArrivalInfo> arrivals = busTrackingService.calculateAllBusArrivalsForStation(stationId);
        return ResponseEntity.ok(arrivals);
    }


}