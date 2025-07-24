package akin.city_card.route.service.concretes;

import akin.city_card.bus.exceptions.RouteNotFoundException;
import akin.city_card.news.exceptions.UnauthorizedAreaException;
import akin.city_card.response.DataResponseMessage;
import akin.city_card.response.ResponseMessage;
import akin.city_card.route.core.converter.RouteConverter;
import akin.city_card.route.core.request.CreateRouteNodeRequest;
import akin.city_card.route.core.request.CreateRouteRequest;
import akin.city_card.route.core.response.RouteDTO;
import akin.city_card.route.core.response.RouteNameDTO;
import akin.city_card.route.model.Route;
import akin.city_card.route.model.RouteSchedule;
import akin.city_card.route.model.RouteStationNode;
import akin.city_card.route.repository.RouteRepository;
import akin.city_card.route.service.abstracts.RouteService;
import akin.city_card.security.entity.Role;
import akin.city_card.security.entity.SecurityUser;
import akin.city_card.security.repository.SecurityUserRepository;
import akin.city_card.station.exceptions.StationNotFoundException;
import akin.city_card.station.model.Station;
import akin.city_card.station.repository.StationRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RouteManager implements RouteService {
    private final RouteRepository routeRepository;
    private final RouteConverter routeConverter;
    private final SecurityUserRepository securityUserRepository;
    private final StationRepository stationRepository;

    @Override
    public DataResponseMessage<List<RouteNameDTO>> searchRoutesByName(String name) {
        List<Route> routes = routeRepository.searchByKeyword(name);
        List<RouteNameDTO> dtos = routes.stream()
                .filter(route ->route.isActive())
                .filter(route -> !route.isDeleted())
                .filter(route -> !route.getStationNodes().isEmpty())
                .map(routeConverter::toRouteNameDTO)
                .toList();

        return new DataResponseMessage<>("Arama sonuçları", true, dtos);
    }

    @Override
    public DataResponseMessage<List<RouteNameDTO>> findRoutesByStationId(Long stationId) throws StationNotFoundException {
        Station station = stationRepository.findById(stationId)
                .orElseThrow(StationNotFoundException::new);

        List<Route> routes = routeRepository.findRoutesByStation(stationId);

        return new DataResponseMessage<>("Duraktan geçen rotalar", true, routes.stream().map(routeConverter::toRouteNameDTO).toList());
    }


    @Override
    public ResponseMessage createRoute(String username, CreateRouteRequest request)
            throws UnauthorizedAreaException, StationNotFoundException {

        // 1. Kullanıcı doğrulama
        SecurityUser securityUser = securityUserRepository.findByUserNumber(username)
                .orElseThrow(UnauthorizedAreaException::new);

        boolean isAdmin = securityUser.getRoles().contains(Role.ADMIN) ||
                securityUser.getRoles().contains(Role.SUPERADMIN);
        if (!isAdmin) {
            throw new UnauthorizedAreaException();
        }

        // 2. Başlangıç ve bitiş duraklarının kontrolü
        Station startStation = stationRepository.findById(request.getStartStationId())
                .orElseThrow(StationNotFoundException::new);
        Station endStation = stationRepository.findById(request.getEndStationId())
                .orElseThrow(StationNotFoundException::new);

        // 3. Route nesnesi oluşturuluyor
        Route route = new Route();
        route.setName(request.getRouteName());
        route.setStartStation(startStation);
        route.setEndStation(endStation);
        route.setCreatedAt(LocalDateTime.now());
        route.setUpdatedAt(LocalDateTime.now());
        route.setActive(true);
        route.setDeleted(false);
        route.setCreatedBy(securityUser);
        route.setUpdatedBy(securityUser);

        // 4. RouteSchedule set ediliyor
        RouteSchedule schedule = new RouteSchedule();
        schedule.setWeekdayHours(request.getWeekdayHours());
        schedule.setWeekendHours(request.getWeekendHours());
        route.setSchedule(schedule);

        // 5. RouteStationNode listesi hazırlanıyor
        List<RouteStationNode> nodeList = new ArrayList<>();
        int order = 0;

        for (CreateRouteNodeRequest nodeRequest : request.getRouteNodes()) {
            Station fromStation = stationRepository.findById(nodeRequest.getFromStationId())
                    .orElseThrow(StationNotFoundException::new);
            Station toStation = stationRepository.findById(nodeRequest.getToStationId())
                    .orElseThrow(StationNotFoundException::new);

            RouteStationNode node = new RouteStationNode();
            node.setRoute(route);
            node.setFromStation(fromStation);
            node.setToStation(toStation);
            node.setSequenceOrder(order++);

            nodeList.add(node);
        }

        route.setStationNodes(nodeList);

        routeRepository.save(route);

        return new ResponseMessage("Rota başarıyla oluşturuldu.", true);


    }




    @Override
    @Transactional
    public ResponseMessage deleteRoute(String username, Long id) throws UnauthorizedAreaException, RouteNotFoundException {
        Optional<SecurityUser> securityUser = securityUserRepository.findByUserNumber(username);

        if (!securityUser.get().getRoles().contains(Role.ADMIN) &&
                !securityUser.get().getRoles().contains(Role.SUPERADMIN)) {
            throw new UnauthorizedAreaException();
        }
        Route route = routeRepository.findById(id).orElseThrow(RouteNotFoundException::new);
        route.setDeletedAt(LocalDateTime.now());
        route.setDeleted(true);
        route.setActive(false);
        route.setDeletedBy(securityUser.get());

        return new ResponseMessage("Rota kaldırıldı", true);
    }


    @Override
    public DataResponseMessage<RouteDTO> getRouteById(Long id) throws RouteNotFoundException {
        return new DataResponseMessage<>("rota ", true, routeConverter.toRouteDTO(routeRepository.findById(id).orElseThrow(RouteNotFoundException::new)));
    }

    @Override
    public DataResponseMessage<List<RouteNameDTO>> getAllRoutes() {
        List<RouteNameDTO> activeRoutes = routeRepository.findAll().stream()
                .filter(route -> route.isActive() && !route.isDeleted())
                .filter(route -> route.getStationNodes() != null && !route.getStationNodes().isEmpty())
                .map(routeConverter::toRouteNameDTO)
                .toList();

        return new DataResponseMessage<>("Aktif rotalar başarıyla listelendi.", true, activeRoutes);
    }



    @Override
    public DataResponseMessage<RouteDTO> addStationToRoute(Long routeId, Long afterStationId, Long newStationId, String username) throws StationNotFoundException, RouteNotFoundException {
        Optional<SecurityUser> user = securityUserRepository.findByUserNumber(username);
        Route route = routeRepository.findById(routeId)
                .orElseThrow(RouteNotFoundException::new);
        Station afterStation = stationRepository.findById(afterStationId)
                .orElseThrow(StationNotFoundException::new);
        Station newStation = stationRepository.findById(newStationId)
                .orElseThrow(StationNotFoundException::new);

        // 1. A → B şeklinde bağlantıyı bul
        RouteStationNode oldEdge = route.getStationNodes().stream()
                .filter(node -> node.getFromStation().equals(afterStation))
                .findFirst()
                .orElseThrow(StationNotFoundException::new);

        Station toStation = oldEdge.getToStation();

        // 2. Eski bağlantıyı sil
        route.getStationNodes().remove(oldEdge);

        // 3. Yeni node’ları oluştur
        RouteStationNode node1 = new RouteStationNode();
        node1.setRoute(route);
        node1.setFromStation(afterStation);
        node1.setToStation(newStation);
        node1.setSequenceOrder(oldEdge.getSequenceOrder());

        RouteStationNode node2 = new RouteStationNode();
        node2.setRoute(route);
        node2.setFromStation(newStation);
        node2.setToStation(toStation);
        node2.setSequenceOrder(oldEdge.getSequenceOrder() + 1);

        route.getStationNodes().add(node1);
        route.getStationNodes().add(node2);

        route.setUpdatedAt(LocalDateTime.now());
        route.setUpdatedBy(user.get());

        routeRepository.save(route);

        return new DataResponseMessage<>("Station added successfully", true, routeConverter.toRouteDTO(route));
    }


    @Override
    public DataResponseMessage<RouteDTO> removeStationFromRoute(Long routeId, Long stationId, String username) throws RouteNotFoundException, StationNotFoundException {
        Optional<SecurityUser> user = securityUserRepository.findByUserNumber(username);
        Route route = routeRepository.findById(routeId)
                .orElseThrow(RouteNotFoundException::new);
        Station stationToRemove = stationRepository.findById(stationId)
                .orElseThrow(StationNotFoundException::new);

        // 1. Bu durağa gelen bağlantı (A → stationToRemove)
        Optional<RouteStationNode> incomingOpt = route.getStationNodes().stream()
                .filter(n -> n.getToStation().equals(stationToRemove))
                .findFirst();

        // 2. Bu duraktan çıkan bağlantı (stationToRemove → C)
        Optional<RouteStationNode> outgoingOpt = route.getStationNodes().stream()
                .filter(n -> n.getFromStation().equals(stationToRemove))
                .findFirst();

        if (incomingOpt.isPresent() && outgoingOpt.isPresent()) {
            RouteStationNode incoming = incomingOpt.get();
            RouteStationNode outgoing = outgoingOpt.get();

            // yeni bağlantı: A → C
            RouteStationNode newNode = new RouteStationNode();
            newNode.setRoute(route);
            newNode.setFromStation(incoming.getFromStation());
            newNode.setToStation(outgoing.getToStation());
            newNode.setSequenceOrder(incoming.getSequenceOrder());

            route.getStationNodes().remove(incoming);
            route.getStationNodes().remove(outgoing);
            route.getStationNodes().add(newNode);
        } else {
            route.getStationNodes().removeIf(n ->
                    n.getFromStation().equals(stationToRemove) ||
                            n.getToStation().equals(stationToRemove));
        }

        route.setUpdatedAt(LocalDateTime.now());
        route.setUpdatedBy(user.get());

        routeRepository.save(route);

        return new DataResponseMessage<>("Station removed and nodes updated", true, routeConverter.toRouteDTO(route));
    }
}
