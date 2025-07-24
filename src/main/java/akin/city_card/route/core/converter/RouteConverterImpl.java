package akin.city_card.route.core.converter;

import akin.city_card.bus.core.converter.BusConverter;
import akin.city_card.route.core.response.*;
import akin.city_card.route.model.Route;
import akin.city_card.route.model.RouteSchedule;
import akin.city_card.route.model.RouteStationNode;
import akin.city_card.station.core.converter.StationConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class RouteConverterImpl implements RouteConverter {
    private final BusConverter busConverter;
    private final StationConverter stationConverter;

    @Override
    public RouteDTO toRouteDTO(Route route) {
        return RouteDTO.builder()
                .id(route.getId())
                .name(route.getName())
                .createdAt(route.getCreatedAt())
                .updatedAt(route.getUpdatedAt())
                .isActive(route.isActive())
                .isDeleted(route.isDeleted())
                .deletedAt(route.getDeletedAt())
                .startStation(stationConverter.toDTO(route.getStartStation()))
                .endStation(stationConverter.toDTO(route.getEndStation()))
                .stationNodes(toStationNodeDTOList(route.getStationNodes()))
                .busDTOS(busConverter.toBusDTOList(route.getBuses()))
                .build();
    }

    @Override
    public RouteNameDTO toRouteNameDTO(Route route) {
        return RouteNameDTO.builder()
                .id(route.getId())
                .name(route.getName())
                .endStationName(route.getEndStation().getName())
                .startStationName(route.getStartStation().getName())
                .routeSchedule(toScheduleDTO(route.getSchedule()))
                .build();
    }

    @Override
    public PublicRouteDTO toPublicRoute(Route route) {
        return PublicRouteDTO.builder()
                .name(route.getName())
                .endStation(stationConverter.toDTO(route.getEndStation()))
                .startStation(stationConverter.toDTO(route.getStartStation()))
                .id(route.getId())
                .build();

    }

    @Override
    public List<RouteStationNodeDTO> toStationNodeDTOList(List<RouteStationNode> nodes) {
        return nodes.stream().map(this::toStationNodeDTO).toList();
    }

    @Override
    public RouteStationNodeDTO toStationNodeDTO(RouteStationNode node) {
        return RouteStationNodeDTO.builder()
                .id(node.getId())
                .fromStation(stationConverter.toDTO(node.getFromStation()))
                .toStation(stationConverter.toDTO(node.getToStation()))
                .sequenceOrder(node.getSequenceOrder())
                .build();
    }

    @Override
    public RouteScheduleDTO toScheduleDTO(RouteSchedule schedule) {
        if (schedule == null) return null;

        return RouteScheduleDTO.builder()
                .weekdayHours(schedule.getWeekdayHours())
                .weekendHours(schedule.getWeekendHours())
                .build();
    }

}
