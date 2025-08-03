package akin.city_card.geoIpService;

import lombok.Data;

@Data
public class GeoLocationData {
    private String ip;
    private String city;
    private String region;
    private String region_code;
    private String country;
    private String country_name;
    private String latitude;
    private String longitude;
    private String timezone;
    private String org;
    private String postal;
}
