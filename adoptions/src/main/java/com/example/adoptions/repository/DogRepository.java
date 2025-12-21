package com.example.adoptions.repository;

import com.example.adoptions.model.Dog;
import org.springframework.data.repository.ListCrudRepository;

public interface DogRepository extends ListCrudRepository<Dog, Integer> {
}
