package akin.city_card.route.service.concretes;

import akin.city_card.route.core.response.NextBusDTO;
import akin.city_card.route.model.RouteSchedule;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RouteWithNextBusDTO {
    private Long id;
    private String name;
    private String startStationName;
    private String endStationName;
    private RouteSchedule routeSchedule;
    private NextBusDTO nextBus; // null olabilir
}