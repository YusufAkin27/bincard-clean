package akin.city_card.route.service.abstracts;

import akin.city_card.bus.exceptions.RouteNotFoundException;
import akin.city_card.news.exceptions.UnauthorizedAreaException;
import akin.city_card.response.DataResponseMessage;
import akin.city_card.response.ResponseMessage;
import akin.city_card.route.core.request.CreateRouteRequest;
import akin.city_card.route.core.response.RouteDTO;
import akin.city_card.route.core.response.RouteNameDTO;
import akin.city_card.route.core.request.RouteSuggestionRequest;
import akin.city_card.route.core.response.RouteSuggestionResponse;
import akin.city_card.route.exceptions.RouteAlreadyFavoriteException;
import akin.city_card.route.exceptions.RouteNotActiveException;
import akin.city_card.security.exception.UserNotFoundException;
import akin.city_card.station.exceptions.StationNotFoundException;

import java.util.List;

public interface RouteService {

    DataResponseMessage<List<RouteNameDTO>> searchRoutesByName(String name);

    DataResponseMessage<List<RouteNameDTO>> findRoutesByStationId(Long stationId) throws StationNotFoundException;

    ResponseMessage createRoute(String username, CreateRouteRequest request) throws UnauthorizedAreaException, StationNotFoundException;


    ResponseMessage deleteRoute(String username, Long id) throws UnauthorizedAreaException, RouteNotFoundException;


    DataResponseMessage<RouteDTO> getRouteById(Long id) throws RouteNotFoundException;

    DataResponseMessage<List<RouteNameDTO>> getAllRoutes();

    DataResponseMessage<RouteDTO> addStationToRoute(Long routeId, Long afterStationId, Long newStationId, String username) throws StationNotFoundException, RouteNotFoundException;

    DataResponseMessage<RouteDTO> removeStationFromRoute(Long routeId, Long stationId, String username) throws RouteNotFoundException, StationNotFoundException;

    ResponseMessage addFavorite(String username, Long routeId) throws RouteNotActiveException, UserNotFoundException, RouteNotFoundException, RouteAlreadyFavoriteException;

    ResponseMessage removeFavorite(String username, Long routeId) throws RouteNotFoundException, UserNotFoundException, RouteNotActiveException;

    DataResponseMessage<List<RouteNameDTO>> favotiteRoutes(String username) throws UserNotFoundException;

    DataResponseMessage<RouteSuggestionResponse> suggestRoute(RouteSuggestionRequest request);
}
