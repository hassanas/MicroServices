package com.vaimo.microservices.product.controller;


import com.vaimo.microservices.product.dto.ProductRequest;
import com.vaimo.microservices.product.dto.ProductResponse;
import com.vaimo.microservices.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/product")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Operations for creating and retrieving products")
public class ProductController {

    private final ProductService productService;


    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            operationId = "createProduct",
            summary = "Create a product",
            description = "Creates a new product and returns the created product payload"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Product created successfully",
                    content = @Content(schema = @Schema(implementation = ProductResponse.class),
                            examples = @ExampleObject(name = "Created product", value = """
                                    {
                                      "id": "66520a2e4593322f9548ea3f",
                                      "name": "IPhone 18 Pro",
                                      "description": "Flagship device with improved camera",
                                      "sku": "IP18PRO-256",
                                      "price": 5699.99
                                    }
                                    """))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
            @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalServerError")
    })
    public ProductResponse createProduct(
            @Valid @org.springframework.web.bind.annotation.RequestBody ProductRequest product) {
        return productService.createProduct(product);

    }

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            operationId = "getAllProducts",
            summary = "List all products",
            description = "Returns all products currently stored in the catalog"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Products returned successfully",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ProductResponse.class)),
                            examples = @ExampleObject(name = "Products list", value = """
                                    [
                                      {
                                        "id": "66520a2e4593322f9548ea3f",
                                        "name": "IPhone 18 Pro",
                                        "description": "Flagship device with improved camera",
                                        "sku": "IP18PRO-256",
                                        "price": 5699.99
                                      }
                                    ]
                                    """))),
            @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalServerError")
    })
    public List<ProductResponse> getAllProducts() {
        // add 5 sec delay to test circut breaker
        /*try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }*/
        return productService.getAllProducts();
    }

}
