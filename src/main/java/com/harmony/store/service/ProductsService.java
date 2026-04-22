package com.harmony.store.service;

import com.harmony.store.dto.*;
import com.harmony.store.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.harmony.store.dto.CreateCategoryDto;
import com.harmony.store.dto.CreateProductDto;
import com.harmony.store.dto.QueryProductDto;
import com.harmony.store.dto.UpdateProductDto;
import com.harmony.store.model.Category;
import com.harmony.store.model.Product;
import com.harmony.store.repository.CategoryRepository;
import com.harmony.store.repository.ProductRepository;

@Service
@RequiredArgsConstructor
public class ProductsService {

    private final ProductRepository productRepo;
    private final CategoryRepository categoryRepo;
    private final StorageService storageService;

    // ── Public listing ────────────────────────────────────────────────────────

    public Map<String, Object> findAll(QueryProductDto query) {
        PageRequest pageable = PageRequest.of(query.getPage() - 1, query.getLimit(),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Product> page = productRepo.findPublic(
                query.getSearch(), query.getCategorySlug(), pageable);
        return buildPageResult(page, query.getPage(), query.getLimit());
    }

    public Product findOne(UUID id) {
        return productRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Product " + id + " not found"));
    }

    // ── Admin listing ─────────────────────────────────────────────────────────

    public Map<String, Object> findAllAdmin(QueryProductDto query) {
        PageRequest pageable = PageRequest.of(query.getPage() - 1, query.getLimit(),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Product> page = productRepo.findAdmin(
                query.getSearch(), query.getCategorySlug(), pageable);
        return buildPageResult(page, query.getPage(), query.getLimit());
    }

    // ── Create ────────────────────────────────────────────────────────────────

    @Transactional
    public Product create(CreateProductDto dto, MultipartFile image) {
        Product product = Product.builder()
                .name(dto.getName())
                .description(dto.getDescription())
                .price(dto.getPrice())
                .stock(dto.getStock())
                .isActive(dto.getIsActive() != null ? dto.getIsActive() : true)
                .build();

        if (dto.getCategoryId() != null) {
            Category category = categoryRepo.findById(dto.getCategoryId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Category " + dto.getCategoryId() + " not found"));
            product.setCategory(category);
        }

        Product saved = productRepo.save(product);

        if (image != null && !image.isEmpty()) {
            String imageUrl = storageService.uploadProductImage(saved.getId().toString(), image);
            saved.setImageUrl(imageUrl);
            saved = productRepo.save(saved);
        }

        return saved;
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @Transactional
    public Product update(UUID id, UpdateProductDto dto, MultipartFile image) {
        Product product = findOne(id);

        if (dto.getName()        != null) product.setName(dto.getName());
        if (dto.getDescription() != null) product.setDescription(dto.getDescription());
        if (dto.getPrice()       != null) product.setPrice(dto.getPrice());
        if (dto.getStock()       != null) product.setStock(dto.getStock());
        if (dto.getIsActive()    != null) product.setActive(dto.getIsActive());

        if (dto.getCategoryId() != null) {
            Category category = categoryRepo.findById(dto.getCategoryId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Category " + dto.getCategoryId() + " not found"));
            product.setCategory(category);
        }

        if (image != null && !image.isEmpty()) {
            if (product.getImageUrl() != null) {
                storageService.deleteProductImage(product.getImageUrl());
            }
            String imageUrl = storageService.uploadProductImage(id.toString(), image);
            product.setImageUrl(imageUrl);
        }

        return productRepo.save(product);
    }

    // ── Delete (soft) ─────────────────────────────────────────────────────────

    @Transactional
    public void remove(UUID id) {
        findOne(id);
        productRepo.deleteById(id); // triggers @SQLDelete
    }

    // ── Categories ────────────────────────────────────────────────────────────

    public List<Category> findAllCategories() {
        return categoryRepo.findAll(Sort.by("name"));
    }

    public Category createCategory(CreateCategoryDto dto) {
        Category category = Category.builder()
                .name(dto.getName())
                .slug(dto.getSlug())
                .build();
        return categoryRepo.save(category);
    }

    // ── Stock ─────────────────────────────────────────────────────────────────

    @Transactional
    public void decrementStock(UUID id, int qty) {
        productRepo.decrementStock(id, qty);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Map<String, Object> buildPageResult(Page<Product> page, int pageNum, int limit) {
        long total = page.getTotalElements();
        return Map.of(
                "data", page.getContent(),
                "meta", Map.of(
                        "total", total,
                        "page", pageNum,
                        "limit", limit,
                        "pages", (int) Math.ceil((double) total / limit)
                )
        );
    }
}
