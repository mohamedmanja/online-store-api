package com.harmony.store.products;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    // Public listing — active only (SQLRestriction already filters deleted)
    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.category WHERE p.isActive = true " +
           "AND (:search IS NULL OR LOWER(p.name) LIKE LOWER(CAST(CONCAT('%',:search,'%') AS string)) " +
           "OR LOWER(p.description) LIKE LOWER(CAST(CONCAT('%',:search,'%') AS string))) " +
           "AND (:slug IS NULL OR p.category.slug = :slug)"
          )



    Page<Product> findPublic(@Param("search") String search,
                             @Param("slug") String slug,
                             Pageable pageable);

    // Admin listing — includes inactive (deleted still excluded by SQLRestriction)
    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.category " +
           "WHERE (:search IS NULL OR LOWER(p.name) LIKE LOWER(CAST(CONCAT('%',:search,'%') AS string)) " +
           "OR LOWER(p.description) LIKE LOWER(CAST(CONCAT('%',:search,'%') AS string))) " +
           "AND (:slug IS NULL OR p.category.slug = :slug)")
    Page<Product> findAdmin(@Param("search") String search,
                            @Param("slug") String slug,
                            Pageable pageable);

    @Modifying
    @Query("UPDATE Product p SET p.stock = p.stock - :qty WHERE p.id = :id")
    void decrementStock(@Param("id") UUID id, @Param("qty") int qty);
}
