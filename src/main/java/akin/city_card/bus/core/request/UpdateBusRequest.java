package akin.city_card.bus.core.request;

import akin.city_card.bus.model.BusStatus;
import lombok.Data;

@Data
public class UpdateBusRequest {
    private String numberPlate;
    private Long routeId;
    private Long driverId;
    private double fare;
    private boolean active;
    private BusStatus status;

}
