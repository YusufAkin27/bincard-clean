package akin.city_card.bus.model;

import akin.city_card.buscard.model.CardType;
import akin.city_card.driver.model.Driver;
import akin.city_card.route.model.Route;
import akin.city_card.security.entity.SecurityUser;
import akin.city_card.station.model.Station;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Bus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String numberPlate;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id")
    private Route route;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    private Driver driver;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private double fare;

    @Column(nullable = false)
    private boolean deleted = false;

    @Enumerated(EnumType.STRING)
    private BusStatus status = BusStatus.CALISIYOR;

    private int capacity;

    private int currentPassengerCount;
    private LocalDateTime deleteTime;

    // Son bilinen konum bilgisi
    private double currentLatitude;
    private double currentLongitude;
    private LocalDateTime lastLocationUpdate;

    private Double lastKnownSpeed; // KM/saat

    @ManyToOne(fetch = FetchType.LAZY)
    private Station lastSeenStation;

    private String lastSeenStationName;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    private SecurityUser createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    private SecurityUser updatedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    private SecurityUser deletedBy;

    @OneToMany(mappedBy = "bus", cascade = CascadeType.ALL)
    private List<BusRide> rides;

    @OneToMany(mappedBy = "bus", cascade = CascadeType.ALL)
    private List<BusLocation> locationHistory;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public double calculateFare(CardType cardType) {
        switch (cardType) {
            case ÖĞRENCİ: return fare * 0.5;
            case ÖĞRETMEN: return fare * 0.75;
            case YAŞLI: return fare * 0.6;
            case ENGELLİ: return fare * 0.4;
            case ÇOCUK: return fare * 0.3;
            case TURİST: return fare * 1.2;
            case ABONMAN: return 0.0;
            case TAM:
            default: return fare;
        }
    }
}
