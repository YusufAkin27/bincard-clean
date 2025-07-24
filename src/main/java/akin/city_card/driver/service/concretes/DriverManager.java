package akin.city_card.driver.service.concretes;

import akin.city_card.driver.core.request.CreateDriverRequest;
import akin.city_card.driver.core.request.UpdateDriverRequest;
import akin.city_card.driver.core.response.DriverDocumentDto;
import akin.city_card.driver.core.response.DriverDto;
import akin.city_card.driver.core.response.DriverPenaltyDto;
import akin.city_card.driver.core.response.DriverPerformanceDto;
import akin.city_card.driver.service.absracts.DriverService;
import akin.city_card.news.core.response.PageDTO;
import akin.city_card.response.DataResponseMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class DriverManager implements DriverService {
    @Override
    public DataResponseMessage<DriverPerformanceDto> getDriverPerformance(Long id, String username) {
        return null;
    }

    @Override
    public DataResponseMessage<DriverDto> createDriver(CreateDriverRequest request, String username) {
        return null;
    }

    @Override
    public DataResponseMessage<DriverDto> updateDriver(Long id, UpdateDriverRequest dto, String username) {
        return null;
    }

    @Override
    public DataResponseMessage<Void> deleteDriver(Long id, String username) {
        return null;
    }

    @Override
    public DataResponseMessage<DriverDto> getDriverById(Long id, String username) {
        return null;
    }

    @Override
    public DataResponseMessage<PageDTO<DriverDto>> getAllDrivers(int page, int size, String username) {
        return null;
    }

    @Override
    public DataResponseMessage<PageDTO<DriverDocumentDto>> getDriverDocuments(Long id, Pageable pageable, String username) {
        return null;
    }

    @Override
    public DataResponseMessage<DriverDocumentDto> addDriverDocument(Long id, DriverDocumentDto dto, String username) {
        return null;
    }

    @Override
    public DataResponseMessage<DriverDocumentDto> updateDriverDocument(Long docId, DriverDocumentDto dto, String username) {
        return null;
    }

    @Override
    public DataResponseMessage<Void> deleteDriverDocument(Long docId, String username) {
        return null;
    }

    @Override
    public DataResponseMessage<PageDTO<DriverPenaltyDto>> getDriverPenalties(Long id, Pageable pageable, String username) {
        return null;
    }

    @Override
    public DataResponseMessage<DriverPenaltyDto> addDriverPenalty(Long id, DriverPenaltyDto dto, String username) {
        return null;
    }

    @Override
    public DataResponseMessage<DriverPenaltyDto> updateDriverPenalty(Long penaltyId, DriverPenaltyDto dto, String username) {
        return null;
    }

    @Override
    public DataResponseMessage<Void> deleteDriverPenalty(Long penaltyId, String username) {
        return null;
    }
}
