package akin.city_card.route.core.converter;

import akin.city_card.route.core.response.RouteDTO;
import akin.city_card.route.core.response.RouteScheduleDTO;
import akin.city_card.route.core.response.RouteStationNodeDTO;
import akin.city_card.route.model.Route;
import akin.city_card.route.model.RouteSchedule;
import akin.city_card.route.model.RouteStationNode;

import java.util.List;

public interface RouteConverter {

    List<RouteStationNodeDTO> toStationNodeDTOList(List<RouteStationNode> nodes);
    RouteStationNodeDTO toStationNodeDTO(RouteStationNode node);
    RouteScheduleDTO toScheduleDTO(RouteSchedule schedule);
    RouteDTO toRouteDTO(Route route);
}
