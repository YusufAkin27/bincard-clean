package akin.city_card.driver.service.absracts;

import akin.city_card.driver.core.request.CreateDriverRequest;
import akin.city_card.driver.core.request.UpdateDriverRequest;
import akin.city_card.driver.core.response.DriverDocumentDto;
import akin.city_card.driver.core.response.DriverDto;
import akin.city_card.driver.core.response.DriverPenaltyDto;
import akin.city_card.driver.core.response.DriverPerformanceDto;
import akin.city_card.news.core.response.PageDTO;
import akin.city_card.response.DataResponseMessage;
import org.springframework.data.domain.Pageable;

public interface DriverService {
    DataResponseMessage<DriverPerformanceDto> getDriverPerformance(Long id, String username);

    DataResponseMessage<DriverDto> createDriver(CreateDriverRequest request, String username);

    DataResponseMessage<DriverDto> updateDriver(Long id, UpdateDriverRequest dto, String username);

    DataResponseMessage<Void> deleteDriver(Long id, String username);

    DataResponseMessage<DriverDto> getDriverById(Long id, String username);

    DataResponseMessage<PageDTO<DriverDto>> getAllDrivers(int page, int size, String username);

    DataResponseMessage<PageDTO<DriverDocumentDto>> getDriverDocuments(Long id, Pageable pageable, String username);

    DataResponseMessage<DriverDocumentDto> addDriverDocument(Long id, DriverDocumentDto dto, String username);

    DataResponseMessage<DriverDocumentDto> updateDriverDocument(Long docId, DriverDocumentDto dto, String username);

    DataResponseMessage<Void> deleteDriverDocument(Long docId, String username);

    DataResponseMessage<PageDTO<DriverPenaltyDto>> getDriverPenalties(Long id, Pageable pageable, String username);

    DataResponseMessage<DriverPenaltyDto> addDriverPenalty(Long id, DriverPenaltyDto dto, String username);

    DataResponseMessage<DriverPenaltyDto> updateDriverPenalty(Long penaltyId, DriverPenaltyDto dto, String username);

    DataResponseMessage<Void> deleteDriverPenalty(Long penaltyId, String username);
}
