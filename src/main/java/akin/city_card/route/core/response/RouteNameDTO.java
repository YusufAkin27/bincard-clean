package akin.city_card.route.core.response;

import akin.city_card.route.model.RouteSchedule;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.lang.management.LockInfo;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RouteNameDTO {

    private Long id;
    private String name;
    private String startStationName;
    private String endStationName;
    private RouteScheduleDTO  routeSchedule;

}
