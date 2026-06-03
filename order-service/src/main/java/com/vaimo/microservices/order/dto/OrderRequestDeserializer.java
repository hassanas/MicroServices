package com.vaimo.microservices.order.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.math.BigDecimal;

public class OrderRequestDeserializer extends JsonDeserializer<OrderRequest> {

    @Override
    public OrderRequest deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
        ObjectNode node = jp.getCodec().readTree(jp);

        // Handle productId: convert number to string if needed
        String productId = null;
        JsonNode productIdNode = node.get("productId");
        if (productIdNode != null) {
            if (productIdNode.isTextual()) {
                productId = productIdNode.asText();
            } else if (productIdNode.isNumber()) {
                // If it's a number, convert to string
                productId = productIdNode.asText();
            }
        }

        Long id = null;
        JsonNode idNode = node.get("id");
        if (idNode != null && !idNode.isNull()) {
            id = idNode.asLong();
        }

        String orderNumber = null;
        JsonNode orderNumberNode = node.get("orderNumber");
        if (orderNumberNode != null && !orderNumberNode.isNull()) {
            orderNumber = orderNumberNode.asText();
        }

        BigDecimal price = null;
        JsonNode priceNode = node.get("price");
        if (priceNode != null && !priceNode.isNull()) {
            price = priceNode.decimalValue();
        }

        String sku = null;
        JsonNode skuNode = node.get("sku");
        if (skuNode != null && !skuNode.isNull()) {
            sku = skuNode.asText();
        }

        Integer quantity = null;
        JsonNode quantityNode = node.get("quantity");
        if (quantityNode != null && !quantityNode.isNull()) {
            quantity = quantityNode.asInt();
        }

        String email = null;
        JsonNode emailNode = node.get("email");
        if (emailNode != null && !emailNode.isNull()) {
            email = emailNode.asText();
        }
        return new OrderRequest(id, orderNumber, productId, price, sku, quantity, email);
    }
}
