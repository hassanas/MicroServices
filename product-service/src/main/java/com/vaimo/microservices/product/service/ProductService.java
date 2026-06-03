package com.vaimo.microservices.product.service;

import com.vaimo.microservices.product.dto.ProductRequest;
import com.vaimo.microservices.product.dto.ProductResponse;
import com.vaimo.microservices.product.model.Product;
import com.vaimo.microservices.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {
    private final ProductRepository productRepository;

    // create product
    public ProductResponse createProduct(ProductRequest productRequest) {
        Product product = Product.builder()
                .name(productRequest.name())
                .description(productRequest.description())
                .sku(productRequest.sku())
                .price(productRequest.price())
                .build();
        productRepository.save(product);
        log.info("Product {} has been created", product);
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getSku(),
                product.getPrice()
        );
    }


    public List<ProductResponse> getAllProducts() {
        return productRepository.findAll()
                .stream()
                .map(product -> new ProductResponse(
                        product.getId(),
                        product.getName(),
                        product.getDescription(),
                        product.getSku(),
                        product.getPrice()
                )).toList();
    }
}
