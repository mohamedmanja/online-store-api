package com.harmony.store.products;

import com.harmony.store.products.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductsController {

    private final ProductsService svc;

    // ── Public ────────────────────────────────────────────────────────────────

    @GetMapping
    public Map<String, Object> findAll(@Valid QueryProductDto query) {
        return svc.findAll(query);
    }

    @GetMapping("/categories")
    public List<Category> findCategories() {
        return svc.findAllCategories();
    }

    @GetMapping("/{id}")
    public Product findOne(@PathVariable UUID id) {
        return svc.findOne(id);
    }

    // ── Admin ─────────────────────────────────────────────────────────────────

    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> findAllAdmin(@Valid QueryProductDto query) {
        return svc.findAllAdmin(query);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public Product create(@Valid @ModelAttribute CreateProductDto dto,
                           @RequestPart(value = "image", required = false) MultipartFile image) {
        return svc.create(dto, image);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public Product update(@PathVariable UUID id,
                           @Valid @ModelAttribute UpdateProductDto dto,
                           @RequestPart(value = "image", required = false) MultipartFile image) {
        return svc.update(id, dto, image);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void remove(@PathVariable UUID id) {
        svc.remove(id);
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public Category createCategory(@Valid @RequestBody CreateCategoryDto dto) {
        return svc.createCategory(dto);
    }
}
