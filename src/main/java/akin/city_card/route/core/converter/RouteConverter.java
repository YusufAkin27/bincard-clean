package akin.city_card.route.core.converter;

import akin.city_card.route.core.response.*;
import akin.city_card.route.model.Route;
import akin.city_card.route.model.RouteSchedule;
import akin.city_card.route.model.RouteStationNode;
import akin.city_card.route.service.concretes.RouteWithNextBusDTO;

import java.util.List;

public interface RouteConverter {

    List<RouteStationNodeDTO> toStationNodeDTOList(List<RouteStationNode> nodes);
    RouteStationNodeDTO toStationNodeDTO(RouteStationNode node);
    RouteScheduleDTO toScheduleDTO(RouteSchedule schedule);
    RouteDTO toRouteDTO(Route route);
    RouteNameDTO toRouteNameDTO(Route route);

    PublicRouteDTO toPublicRoute(Route route);

    RouteWithNextBusDTO toRouteWithNextBusDTO(Route route);
}
