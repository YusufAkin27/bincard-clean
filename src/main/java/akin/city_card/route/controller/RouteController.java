package akin.city_card.route.controller;

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
import akin.city_card.route.service.abstracts.RouteService;
import akin.city_card.security.exception.UserNotFoundException;
import akin.city_card.station.exceptions.StationNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/api/route")
@RequiredArgsConstructor
public class RouteController {

    private final RouteService routeService;

    @PostMapping("/create-route")
    public ResponseMessage createRoute(@AuthenticationPrincipal UserDetails userDetails,
                                       @RequestBody CreateRouteRequest request)
            throws StationNotFoundException, UnauthorizedAreaException {
        return routeService.createRoute(userDetails.getUsername(), request);
    }


    @DeleteMapping("/{id}")
    public ResponseMessage deleteRoute(@AuthenticationPrincipal UserDetails userDetails,
                                       @PathVariable Long id)
            throws UnauthorizedAreaException, RouteNotFoundException {
        return routeService.deleteRoute(userDetails.getUsername(), id);
    }

    @PostMapping("/{routeId}/add-station")
    public DataResponseMessage<RouteDTO> addStationToRoute(@AuthenticationPrincipal UserDetails userDetails,
                                                           @PathVariable Long routeId,
                                                           @RequestParam Long afterStationId,
                                                           @RequestParam Long newStationId)
            throws StationNotFoundException, RouteNotFoundException {
        return routeService.addStationToRoute(routeId, afterStationId, newStationId, userDetails.getUsername());
    }

    @DeleteMapping("/{routeId}/remove-station")
    public DataResponseMessage<RouteDTO> removeStationFromRoute(@AuthenticationPrincipal UserDetails userDetails,
                                                                @PathVariable Long routeId,
                                                                @RequestParam Long stationId)
            throws StationNotFoundException, RouteNotFoundException {
        return routeService.removeStationFromRoute(routeId, stationId, userDetails.getUsername());
    }


    @GetMapping("/getAllRoutes")
    public DataResponseMessage<List<RouteNameDTO>> getAllRoutes() {
        return routeService.getAllRoutes();
    }

    @GetMapping("/{id}")
    public DataResponseMessage<RouteDTO> getRouteById(@PathVariable Long id) throws RouteNotFoundException {
        return routeService.getRouteById(id);
    }

    @GetMapping("/search-by-name")
    public DataResponseMessage<List<RouteNameDTO>> searchRoutesByName(@RequestParam String name) {
        return routeService.searchRoutesByName(name);
    }

    @GetMapping("/search-by-station")
    public DataResponseMessage<List<RouteNameDTO>> searchRoutesByStationId(@RequestParam Long stationId)
            throws StationNotFoundException {
        return routeService.findRoutesByStationId(stationId);
    }

    @PostMapping("/add-favorite")
    public ResponseMessage addFavorite(@AuthenticationPrincipal UserDetails userDetails,
                                       @RequestParam Long routeId) throws UserNotFoundException, RouteNotActiveException, RouteNotFoundException, RouteAlreadyFavoriteException {
        return routeService.addFavorite(userDetails.getUsername(), routeId);
    }

    @DeleteMapping("/remove-favorite")
    public ResponseMessage removeFavorite(@AuthenticationPrincipal UserDetails userDetails,
                                          @RequestParam Long routeId) throws UserNotFoundException, RouteNotActiveException, RouteNotFoundException {
        return routeService.removeFavorite(userDetails.getUsername(), routeId);
    }

    @GetMapping("/favorite")
    public DataResponseMessage<List<RouteNameDTO>> favoriteRoutes(@AuthenticationPrincipal UserDetails userDetails) throws UserNotFoundException {
        return routeService.favotiteRoutes(userDetails.getUsername());
    }

    @PostMapping("/suggest")
    public DataResponseMessage<RouteSuggestionResponse> suggestRoute(@RequestBody RouteSuggestionRequest request) {
      return routeService.suggestRoute(request);

    }

}


