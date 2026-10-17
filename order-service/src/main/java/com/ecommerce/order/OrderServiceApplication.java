package com.ecommerce.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableFeignClients(
    clients = {
      com.ecommerce.order.clients.CatalogClient.class,
      com.ecommerce.order.clients.CatalogReadClient.class,
      com.ecommerce.order.clients.IdentityClient.class,
      com.ecommerce.order.clients.IdentityStatusClient.class
    })
public class OrderServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(OrderServiceApplication.class, args);
  }
}
