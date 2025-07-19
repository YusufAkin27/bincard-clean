package akin.city_card.station.core.request;

import akin.city_card.station.model.StationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateStationRequest {

    @NotBlank
    private String name;

    @NotNull
    private Double latitude;

    @NotNull
    private Double longitude;

    @NotNull
    private StationType type;

    @NotBlank
    private String city;

    @NotBlank
    private String district;

    @NotBlank
    private String street;

    private String postalCode;
}
