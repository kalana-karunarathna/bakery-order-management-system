package com.bakery.user.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Navigation destinations that depend on other modules, readable in templates as
 * {@code ${@navigation.myOrdersUrl}}.
 *
 * <p>Order Management's "my orders" page ({@code /orders/my-orders}) lives on the
 * {@code feature/order-management} branch. Until that branch is merged, "My Orders" points to the
 * {@code /orders} page that exists here. After the merge, set
 * {@code bakery.nav.my-orders-url=/orders/my-orders} in application.properties.</p>
 */
@Component("navigation")
public class NavigationLinks {

    private final String myOrdersUrl;

    public NavigationLinks(@Value("${bakery.nav.my-orders-url:/orders}") String myOrdersUrl) {
        // Only allow an application-relative path, never an external URL.
        this.myOrdersUrl = myOrdersUrl.startsWith("/") && !myOrdersUrl.startsWith("//") ? myOrdersUrl : "/orders";
    }

    public String getMyOrdersUrl() {
        return myOrdersUrl;
    }
}
