package com.just.critter.adopt.repository;

import com.just.critter.adopt.service.ExpiringAdoption;
import com.just.critter.domain.model.AdoptionExpiryRow;
import com.just.critter.mapper.AdoptionExpiryMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class MyBatisAdoptionExpiryRepository implements AdoptionExpiryRepository {

    private final AdoptionExpiryMapper mapper;

    public MyBatisAdoptionExpiryRepository(AdoptionExpiryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<ExpiringAdoption> findExpiring(LocalDate endDate) {
        return mapper.findExpiring(endDate).stream().map(this::toRecord).toList();
    }

    @Override
    public List<ExpiringAdoption> findExpired(LocalDate today) {
        return mapper.findExpired(today).stream().map(this::toRecord).toList();
    }

    @Override
    public int expire(long adoptionId, LocalDateTime now) {
        return mapper.expire(adoptionId, now);
    }

    @Override
    public int releaseAnimal(long animalId, LocalDateTime now) {
        return mapper.releaseAnimal(animalId, now);
    }

    private ExpiringAdoption toRecord(AdoptionExpiryRow row) {
        return new ExpiringAdoption(
                row.getId() == null ? 0 : row.getId(),
                row.getAdoptionNo(),
                row.getUserId() == null ? 0 : row.getUserId(),
                row.getAnimalId() == null ? 0 : row.getAnimalId(),
                row.getAnimalCode(),
                row.getEndDate());
    }
}
