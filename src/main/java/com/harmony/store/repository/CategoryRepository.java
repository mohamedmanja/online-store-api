package com.harmony.store.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import com.harmony.store.model.Category;

public interface CategoryRepository extends JpaRepository<Category, UUID> {
}
