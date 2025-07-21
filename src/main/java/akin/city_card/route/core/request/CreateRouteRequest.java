package akin.city_card.route.core.request;

import lombok.Data;

import java.util.List;

@Data
public class CreateRouteRequest {
    private String routeName;
    private Long startStationId;
    private Long endStationId;

    private List<CreateRouteNodeRequest> routeNodes;
}
