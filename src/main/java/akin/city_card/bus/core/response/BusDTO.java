package akin.city_card.bus.core.response;

import akin.city_card.bus.model.BusStatus;
import akin.city_card.station.model.Station;
import akin.city_card.user.core.response.Views;
import com.fasterxml.jackson.annotation.JsonView;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BusDTO {

    @JsonView(Views.Public.class)
    private Long id;

    @JsonView(Views.Public.class)
    private String numberPlate;

    @JsonView(Views.Public.class)
    private String routeName;

    @JsonView(Views.Public.class)
    private String driverName;

    @JsonView(Views.Public.class)
    private boolean active;

    @JsonView(Views.Public.class)
    private double fare;

    @JsonView(Views.User.class)
    private double currentLatitude;

    @JsonView(Views.User.class)
    private double currentLongitude;

    @JsonView(Views.User.class)
    private LocalDateTime lastLocationUpdate;

    @JsonView(Views.Admin.class)
    private BusStatus status;

    @JsonView(Views.Admin.class)
    private String lastSeenStationName;

    @JsonView(Views.SuperAdmin.class)
    private LocalDateTime createdAt;

    @JsonView(Views.SuperAdmin.class)
    private LocalDateTime updatedAt;

    @JsonView(Views.SuperAdmin.class)
    private String createdByUsername;

    @JsonView(Views.SuperAdmin.class)
    private String updatedByUsername;
}
