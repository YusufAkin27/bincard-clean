package akin.city_card.bus.service.abstracts;

import akin.city_card.bus.model.Bus;
import akin.city_card.bus.model.BusLocation;
import akin.city_card.route.model.Direction;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class BusArrivalInfo {
    private Bus bus;
    private Long targetStationId;
    private String targetStationName;
    private int estimatedArrivalMinutes;
    private BusLocation lastKnownLocation;
    private Direction currentDirection;
    private List<String> pathStations;
    private LocalDateTime calculatedAt;
    private boolean isDelayed;
    private String delayReason;
    
    public String getFormattedArrivalTime() {
        if (estimatedArrivalMinutes <= 0) {
            return "Yakında";
        } else if (estimatedArrivalMinutes == 1) {
            return "1 dakika";
        } else {
            return estimatedArrivalMinutes + " dakika";
        }
    }
    
    public boolean isArriving() {
        return estimatedArrivalMinutes <= 2;
    }
}