package akin.city_card.initializer;

import akin.city_card.route.model.*;
import akin.city_card.route.repository.RouteRepository;
import akin.city_card.route.repository.RouteStationNodeRepository;
import akin.city_card.station.model.Station;
import akin.city_card.station.repository.StationRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Component
@RequiredArgsConstructor
@Order(3)
public class RouteDataInitializer implements ApplicationRunner {

    private final StationRepository stationRepository;
    private final RouteRepository routeRepository;
    private final RouteStationNodeRepository routeStationNodeRepository;
    private final Random random = new Random();

    @Override
    public void run(ApplicationArguments args) {
        if (routeRepository.count() > 0) return;

        List<Station> allStations = stationRepository.findAll();
        if (allStations.size() < 20) {
            System.out.println("🚫 Yeterli sayıda durak yok. Rota oluşturulmadı.");
            return;
        }

        for (int i = 1; i <= 30; i++) {
            List<Station> routeStations = selectNearbyStations(allStations, 20);
            Route route = new Route();
            route.setName("Rota-" + getRandomRouteName());
            route.setCreatedAt(LocalDateTime.now());
            route.setUpdatedAt(LocalDateTime.now());
            route.setActive(true);
            route.setDeleted(false);
            route.setStartStation(routeStations.get(0));
            route.setEndStation(routeStations.get(routeStations.size() - 1));
            routeRepository.save(route);

            List<RouteStationNode> nodes = new ArrayList<>();
            for (int j = 0; j < routeStations.size() - 1; j++) {
                Station from = routeStations.get(j);
                Station to = routeStations.get(j + 1);

                RouteStationNode node = new RouteStationNode();
                node.setRoute(route);
                node.setFromStation(from);
                node.setToStation(to);
                node.setSequenceOrder(j + 1);
                nodes.add(node);
            }

            routeStationNodeRepository.saveAll(nodes);
        }

        System.out.println("✅ 30 rota başarıyla oluşturuldu.");
    }

    private List<Station> selectNearbyStations(List<Station> allStations, int count) {
        Station start = allStations.get(random.nextInt(allStations.size()));
        List<Station> result = new ArrayList<>();
        result.add(start);

        Set<Station> used = new HashSet<>();
        used.add(start);

        while (result.size() < count) {
            Station last = result.get(result.size() - 1);

            Station next = allStations.stream()
                    .filter(s -> !used.contains(s))
                    .sorted(Comparator.comparingDouble(s -> distance(last, s)))
                    .findFirst()
                    .orElse(null);

            if (next == null) break;

            result.add(next);
            used.add(next);
        }

        return result;
    }

    private double distance(Station a, Station b) {
        double latDiff = a.getLocation().getLatitude() - b.getLocation().getLatitude();
        double lonDiff = a.getLocation().getLongitude() - b.getLocation().getLongitude();
        return Math.sqrt(latDiff * latDiff + lonDiff * lonDiff);
    }

    private String getRandomRouteName() {
        String[] names = {"Akasya", "Nilüfer", "Yavuz", "Meşe", "Zeytin", "Pınar", "Kartal", "Serin", "Çamlık", "Güneş"};
        return names[random.nextInt(names.length)] + "-" + (100 + random.nextInt(900));
    }

    private RouteSchedule generateRandomSchedule() {
        List<TimeSlot> all = Arrays.asList(TimeSlot.values());
        Collections.shuffle(all);
        List<TimeSlot> weekday = all.subList(0, 8); // rastgele 8 zaman dilimi
        Collections.shuffle(all);
        List<TimeSlot> weekend = all.subList(0, 5); // rastgele 5 zaman dilimi

        return new RouteSchedule(weekday, weekend);
    }
}
