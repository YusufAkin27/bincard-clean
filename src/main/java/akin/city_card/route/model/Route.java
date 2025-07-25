package akin.city_card.route.model;

import akin.city_card.bus.model.Bus;
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
public class Route {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean isActive;
    private boolean isDeleted;

    @ManyToOne
    @JoinColumn(name = "created_by_id")
    private SecurityUser createdBy;

    @ManyToOne
    @JoinColumn(name = "updated_by_id")
    private SecurityUser updatedBy;

    @ManyToOne
    @JoinColumn(name = "deleted_by_id")
    private SecurityUser deletedBy;

    private LocalDateTime deletedAt;

    @ManyToOne
    private Station startStation;

    @ManyToOne
    private Station endStation;

    @Embedded
    private RouteSchedule schedule;

    @OneToMany(mappedBy = "route", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RouteStationNode> stationNodes;

    // Outbound/Inbound ayrımı kaldırıldı
    @OneToMany(mappedBy = "route", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Bus> buses;
}
