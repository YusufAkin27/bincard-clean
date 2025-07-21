package akin.city_card.route.service.abstracts;

import akin.city_card.bus.exceptions.RouteNotFoundException;
import akin.city_card.news.exceptions.UnauthorizedAreaException;
import akin.city_card.response.DataResponseMessage;
import akin.city_card.response.ResponseMessage;
import akin.city_card.route.core.request.CreateRouteRequest;
import akin.city_card.route.core.request.UpdateRouteRequest;
import akin.city_card.route.core.response.RouteDTO;
import akin.city_card.station.exceptions.StationNotFoundException;

import java.util.List;

public interface RouteService {

    DataResponseMessage<List<RouteDTO>> searchRoutesByName(String name);

    DataResponseMessage<List<RouteDTO>> findRoutesByStationId(Long stationId) throws StationNotFoundException;

    ResponseMessage createRoute(String username, CreateRouteRequest request) throws UnauthorizedAreaException, StationNotFoundException;

    DataResponseMessage<RouteDTO> updateRoute(String username, UpdateRouteRequest request) throws UnauthorizedAreaException, StationNotFoundException, RouteNotFoundException;

    ResponseMessage deleteRoute(String username, Long id) throws UnauthorizedAreaException, RouteNotFoundException;


    DataResponseMessage<RouteDTO> getRouteById(Long id) throws RouteNotFoundException;

    DataResponseMessage<List<RouteDTO>> getAllRoutes();

    DataResponseMessage<RouteDTO> addStationToRoute(Long routeId, Long afterStationId, Long newStationId, String username) throws StationNotFoundException, RouteNotFoundException;

    DataResponseMessage<RouteDTO> removeStationFromRoute(Long routeId, Long stationId, String username) throws RouteNotFoundException, StationNotFoundException;
}
