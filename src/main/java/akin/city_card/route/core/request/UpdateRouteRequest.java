package akin.city_card.route.core.request;

import lombok.Data;

import java.util.List;

@Data
public class UpdateRouteRequest {
    private Long routeId;
    private String routeName;
    private Long startStationId;
    private Long endStationId;
    private List<UpdateRouteNodeRequest> routeNodes;
}
