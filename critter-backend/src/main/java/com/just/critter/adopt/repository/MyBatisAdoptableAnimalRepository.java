package com.just.critter.adopt.repository;

import com.just.critter.adopt.domain.AdoptableAnimal;
import com.just.critter.domain.model.Animal;
import com.just.critter.mapper.AdoptionAnimalMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class MyBatisAdoptableAnimalRepository implements AdoptableAnimalRepository {

    private final AdoptionAnimalMapper mapper;

    public MyBatisAdoptableAnimalRepository(AdoptionAnimalMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<AdoptableAnimal> lockByCode(String code) {
        Animal animal = mapper.lockByCode(code);
        if (animal == null) {
            return Optional.empty();
        }
        return Optional.of(new AdoptableAnimal(animal.getId(), animal.getCode(), animal.getStatus()));
    }

    @Override
    public int markAdopted(long animalId) {
        return mapper.markAdopted(animalId);
    }

    @Override
    public int markOnCampus(long animalId) {
        return mapper.markOnCampus(animalId);
    }
}
