package akin.city_card.station.core.request;

import akin.city_card.station.model.StationType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateStationRequest {

    @NotNull
    private Long id;

    private String name;
    private Double latitude;
    private Double longitude;
    private StationType type;

    private String city;
    private String district;
    private String street;
    private String postalCode;

    private Boolean active;
}
