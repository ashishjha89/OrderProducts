package com.example.productservice.service;

import com.example.productservice.common.InternalServerException;
import com.example.productservice.dto.ProductRequest;
import com.example.productservice.dto.ProductResponse;
import com.example.productservice.dto.SavedProduct;
import com.example.productservice.model.Product;
import com.example.productservice.repository.ProductRepository;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.util.List;

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
            final var savedItem = productRepository.save(product);
            log.info("Product {} is saved", product.getId());
            return new SavedProduct(savedItem.getId());
        } catch (DataAccessException e) {
            log.error("Error when saving product:{}", e.getMessage());
            throw new InternalServerException();
        }
    }

    public List<ProductResponse> getAllProducts() throws InternalServerException {
        try {
            return productRepository.findAll().stream().map(this::mapToProductResponse).toList();
        } catch (DataAccessException e) {
            log.error("Error when getting all products:{}", e.getMessage());
            throw new InternalServerException();
        }
    }

    private ProductResponse mapToProductResponse(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .build();
    }
}
