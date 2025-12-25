package com.orderproduct.productservice.service;

import java.util.List;
import java.util.Objects;

import org.springframework.dao.DataAccessException;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import com.orderproduct.productservice.common.InternalServerException;
import com.orderproduct.productservice.dto.ProductRequest;
import com.orderproduct.productservice.dto.ProductResponse;
import com.orderproduct.productservice.dto.SavedProduct;
import com.orderproduct.productservice.entity.Product;
import com.orderproduct.productservice.repository.ProductRepository;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public SavedProduct createProduct(@NonNull ProductRequest productRequest) throws InternalServerException {
        final var product = Product.builder()
                .name(productRequest.getName())
                .description(productRequest.getDescription())
                .price(productRequest.getPrice())
                .build();
        try {
            final var savedItem = productRepository.save(Objects.requireNonNull(product));
            log.info("Product {} is saved", product.getId());
            return new SavedProduct(savedItem.getId());
        } catch (DataAccessException e) {
            log.error("Error when saving product:{}", e.getMessage());
            throw new InternalServerException();
        }
    }

    public List<ProductResponse> getAllProducts() throws InternalServerException {
        try {
            return productRepository.findAll().stream().map(this::toProductResponse).toList();
        } catch (DataAccessException e) {
            log.error("Error when getting all products:{}", e.getMessage());
            throw new InternalServerException();
        }
    }

    private ProductResponse toProductResponse(@NonNull Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .build();
    }
}
