package akin.city_card.initializer;

import akin.city_card.bus.model.Bus;
import akin.city_card.bus.model.BusStatus;
import akin.city_card.bus.repository.BusRepository;
import akin.city_card.driver.model.Driver;
import akin.city_card.driver.repository.DriverRepository;
import akin.city_card.route.model.Route;
import akin.city_card.route.repository.RouteRepository;
import akin.city_card.station.model.Station;
import akin.city_card.station.repository.StationRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

@Component
@RequiredArgsConstructor
@Order(4)
public class BusDataInitializer implements ApplicationRunner {

    private final BusRepository busRepository;
    private final DriverRepository driverRepository;
    private final RouteRepository routeRepository;
    private final StationRepository stationRepository;

    private final Random random = new Random();
    @Override
    public void run(ApplicationArguments args) {
        if (busRepository.count() > 0) return;

        List<Driver> drivers = driverRepository.findAll();
        List<Route> routes = routeRepository.findAll();
        List<Station> stations = stationRepository.findAll();

        if (drivers.size() < 100) {
            System.out.println("🚫 Yeterli sürücü bulunamadı. Otobüsler oluşturulmadı.");
            return;
        }

        for (int i = 0; i < 100; i++) {
            Driver driver = drivers.get(i);
            Route outboundRoute = routes.get(random.nextInt(routes.size()));
            Station startStation = stations.get(random.nextInt(stations.size()));

            Bus bus = new Bus();
            bus.setDriver(driver);
            bus.setRoute(outboundRoute);
            bus.setNumberPlate(generatePlate(i));
            bus.setFare(10.0 + random.nextDouble(5.0));
            bus.setCapacity(30 + random.nextInt(30));
            bus.setCurrentPassengerCount(0);
            bus.setStatus(BusStatus.CALISIYOR);
            bus.setLastSeenStation(startStation);
            bus.setLastSeenStationName(startStation.getName());
            bus.setCurrentLatitude(startStation.getLocation().getLatitude());
            bus.setCurrentLongitude(startStation.getLocation().getLongitude());
            bus.setLastLocationUpdate(LocalDateTime.now());
            bus.setCreatedAt(LocalDateTime.now());
            bus.setUpdatedAt(LocalDateTime.now());

            busRepository.save(bus);
        }

        System.out.println("✅ 100 otobüs başarıyla oluşturuldu.");
    }

    private String generatePlate(int index) {
        String[] cities = {"34", "06", "35", "16", "01", "44", "23", "61"};
        String cityCode = cities[random.nextInt(cities.length)];
        char letter1 = (char) ('A' + random.nextInt(26));
        char letter2 = (char) ('A' + random.nextInt(26));
        int digits = 100 + random.nextInt(900);

        return cityCode + " " + letter1 + letter2 + " " + digits;
    }
}
