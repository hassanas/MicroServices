package com.vaimo.microservices.order.repository;

import com.vaimo.microservices.order.modal.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {

}
