package com.harmony.store.service;

import com.harmony.store.dto.CreateCategoryDto;
import com.harmony.store.dto.CreateProductDto;
import com.harmony.store.dto.UpdateProductDto;
import com.harmony.store.model.Category;
import com.harmony.store.model.Product;
import com.harmony.store.repository.CategoryRepository;
import com.harmony.store.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductsServiceTest {

    @Mock ProductRepository  productRepo;
    @Mock CategoryRepository categoryRepo;
    @Mock StorageService     storageService;

    @InjectMocks ProductsService productsService;

    private Product buildProduct(UUID id) {
        return Product.builder()
                .id(id)
                .name("Widget")
                .price(BigDecimal.valueOf(29.99))
                .stock(50)
                .isActive(true)
                .build();
    }

    private Category buildCategory(UUID id) {
        return Category.builder().id(id).name("Electronics").slug("electronics").build();
    }

    // ── findOne ───────────────────────────────────────────────────────────────

    @Test
    void findOne_exists_returnsProduct() {
        UUID id = UUID.randomUUID();
        Product product = buildProduct(id);
        when(productRepo.findById(id)).thenReturn(Optional.of(product));

        assertThat(productsService.findOne(id)).isEqualTo(product);
    }

    @Test
    void findOne_notFound_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(productRepo.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productsService.findOne(id))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ── findAll ───────────────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void findAll_returnsPagedResult() {
        Product product = buildProduct(UUID.randomUUID());
        Page<Product> page = new PageImpl<>(List.of(product));

        when(productRepo.findPublic(isNull(), isNull(), any(Pageable.class))).thenReturn(page);

        com.harmony.store.dto.QueryProductDto query = new com.harmony.store.dto.QueryProductDto();
        query.setPage(1);
        query.setLimit(10);

        Map<String, Object> result = productsService.findAll(query);

        assertThat(result).containsKey("data");
        assertThat(result).containsKey("meta");
        assertThat((List<?>) result.get("data")).hasSize(1);

        Map<?, ?> meta = (Map<?, ?>) result.get("meta");
        assertThat(meta.get("total")).isEqualTo(1L);
        assertThat(meta.get("page")).isEqualTo(1);
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    void create_withoutImage_savesProduct() {
        UUID id = UUID.randomUUID();
        Product saved = buildProduct(id);

        CreateProductDto dto = new CreateProductDto();
        dto.setName("Widget");
        dto.setPrice(BigDecimal.valueOf(29.99));
        dto.setStock(50);

        when(productRepo.save(any(Product.class))).thenReturn(saved);

        Product result = productsService.create(dto, null);

        assertThat(result).isEqualTo(saved);
        verifyNoInteractions(storageService);
    }

    @Test
    void create_withImage_uploadsAndSetsImageUrl() throws Exception {
        UUID id = UUID.randomUUID();
        Product saved = buildProduct(id);
        Product withImage = buildProduct(id);
        withImage.setImageUrl("https://minio/img.jpg");

        CreateProductDto dto = new CreateProductDto();
        dto.setName("Widget");
        dto.setPrice(BigDecimal.valueOf(29.99));
        dto.setStock(50);

        MockMultipartFile image = new MockMultipartFile(
                "image", "img.jpg", "image/jpeg", new byte[]{1, 2, 3});

        when(productRepo.save(any(Product.class))).thenReturn(saved).thenReturn(withImage);
        when(storageService.uploadProductImage(eq(id.toString()), eq(image)))
                .thenReturn("https://minio/img.jpg");

        Product result = productsService.create(dto, image);

        assertThat(result.getImageUrl()).isEqualTo("https://minio/img.jpg");
        verify(storageService).uploadProductImage(id.toString(), image);
        verify(productRepo, times(2)).save(any(Product.class));
    }

    @Test
    void create_withCategory_linksCategory() {
        UUID productId  = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        Category category = buildCategory(categoryId);
        Product saved = buildProduct(productId);
        saved.setCategory(category);

        CreateProductDto dto = new CreateProductDto();
        dto.setName("Widget");
        dto.setPrice(BigDecimal.valueOf(29.99));
        dto.setStock(10);
        dto.setCategoryId(categoryId);

        when(categoryRepo.findById(categoryId)).thenReturn(Optional.of(category));
        when(productRepo.save(any(Product.class))).thenReturn(saved);

        Product result = productsService.create(dto, null);

        assertThat(result.getCategory()).isEqualTo(category);
    }

    @Test
    void create_withUnknownCategory_throwsNotFound() {
        UUID categoryId = UUID.randomUUID();

        CreateProductDto dto = new CreateProductDto();
        dto.setName("Widget");
        dto.setPrice(BigDecimal.valueOf(9.99));
        dto.setStock(1);
        dto.setCategoryId(categoryId);

        when(categoryRepo.findById(categoryId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productsService.create(dto, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));

        verify(productRepo, never()).save(any());
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Test
    void update_nameAndPrice_updatesFields() {
        UUID id = UUID.randomUUID();
        Product product = buildProduct(id);

        when(productRepo.findById(id)).thenReturn(Optional.of(product));
        when(productRepo.save(product)).thenReturn(product);

        UpdateProductDto dto = new UpdateProductDto();
        dto.setName("Updated Name");
        dto.setPrice(BigDecimal.valueOf(49.99));

        Product result = productsService.update(id, dto, null);

        assertThat(result.getName()).isEqualTo("Updated Name");
        assertThat(result.getPrice()).isEqualByComparingTo("49.99");
    }

    @Test
    void update_withNewImage_replacesOldImage() {
        UUID id = UUID.randomUUID();
        Product product = buildProduct(id);
        product.setImageUrl("https://minio/old.jpg");

        when(productRepo.findById(id)).thenReturn(Optional.of(product));
        when(productRepo.save(product)).thenReturn(product);
        when(storageService.uploadProductImage(eq(id.toString()), any()))
                .thenReturn("https://minio/new.jpg");

        MockMultipartFile newImage = new MockMultipartFile(
                "image", "new.jpg", "image/jpeg", new byte[]{4, 5, 6});

        UpdateProductDto dto = new UpdateProductDto();
        productsService.update(id, dto, newImage);

        verify(storageService).deleteProductImage("https://minio/old.jpg");
        verify(storageService).uploadProductImage(id.toString(), newImage);
        assertThat(product.getImageUrl()).isEqualTo("https://minio/new.jpg");
    }

    @Test
    void update_productNotFound_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(productRepo.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productsService.update(id, new UpdateProductDto(), null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ── remove ────────────────────────────────────────────────────────────────

    @Test
    void remove_existingProduct_deletesById() {
        UUID id = UUID.randomUUID();
        Product product = buildProduct(id);
        when(productRepo.findById(id)).thenReturn(Optional.of(product));

        productsService.remove(id);

        verify(productRepo).deleteById(id);
    }

    @Test
    void remove_notFound_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(productRepo.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productsService.remove(id))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));

        verify(productRepo, never()).deleteById(any());
    }

    // ── createCategory ────────────────────────────────────────────────────────

    @Test
    void createCategory_savesAndReturnsCategory() {
        UUID id = UUID.randomUUID();
        Category saved = buildCategory(id);

        CreateCategoryDto dto = new CreateCategoryDto();
        dto.setName("Electronics");
        dto.setSlug("electronics");

        when(categoryRepo.save(any(Category.class))).thenReturn(saved);

        Category result = productsService.createCategory(dto);

        assertThat(result).isEqualTo(saved);
        verify(categoryRepo).save(argThat(c ->
                "Electronics".equals(c.getName()) && "electronics".equals(c.getSlug())));
    }
}
