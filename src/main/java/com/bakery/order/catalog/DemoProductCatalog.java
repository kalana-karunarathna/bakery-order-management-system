package com.bakery.order.catalog;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * DEMO product catalog for the order page: a small, immutable, in-code list (no database collection).
 *
 * <p>It exists only so customers can pick a real product and the server can set the price, until the
 * Product Management module provides a catalogue service. It does not touch the {@code com.bakery.product}
 * package. To remove it: delete this package and the catalog lookup in {@code OrderController}; to replace
 * it: implement the same {@link #find}/{@link #all} contract on top of Product Management.</p>
 *
 * <p>The price shown here is the price charged: {@code OrderController} looks the product up by ID and
 * ignores any name or price sent by the browser.</p>
 */
@Component
public class DemoProductCatalog {

    /** One menu item. Prices are in LKR, per item/pack as described. */
    public record DemoProduct(String id, String name, String category, String description, BigDecimal price,
                              String image) {
    }

    private static final List<DemoProduct> PRODUCTS = List.of(
            new DemoProduct("P101", "Cherry Chocolate Layer Cake", "Cakes",
                    "Three layers of dark chocolate sponge with cherry compote and ganache. 1 kg, serves 8-10.",
                    new BigDecimal("4850.00"), "/images/bakery/product-layer-cake.webp"),
            new DemoProduct("P102", "Chocolate Cupcakes (6)", "Cupcakes",
                    "Moist cocoa cupcakes with silky chocolate buttercream. Box of six.",
                    new BigDecimal("1650.00"), "/images/bakery/product-cupcakes.webp"),
            new DemoProduct("P103", "Country Sourdough Loaf", "Bread",
                    "Slow-fermented for 24 hours with a crackling crust and open crumb. 800 g.",
                    new BigDecimal("1250.00"), "/images/bakery/product-sourdough.webp"),
            new DemoProduct("P104", "Artisan Bread Basket", "Bread",
                    "A mixed basket of baguette, seeded rye and soft milk rolls for the family table.",
                    new BigDecimal("2400.00"), "/images/bakery/product-bread-basket.webp"),
            new DemoProduct("P105", "Butter Croissant", "Pastries",
                    "Flaky, golden and made with real butter, laminated by hand each morning.",
                    new BigDecimal("480.00"), "/images/bakery/product-croissant.webp"),
            new DemoProduct("P106", "Pain au Chocolat", "Pastries",
                    "Buttery croissant dough wrapped around two bars of dark chocolate.",
                    new BigDecimal("550.00"), "/images/bakery/product-pastries.webp"),
            new DemoProduct("P107", "Glazed Cinnamon Roll", "Pastries",
                    "Soft swirl of cinnamon sugar finished with a vanilla glaze.",
                    new BigDecimal("420.00"), "/images/bakery/product-cinnamon-rolls.webp"),
            new DemoProduct("P108", "Salted Chocolate Chip Cookies (6)", "Cookies",
                    "Chewy brown-butter cookies with dark chocolate and a pinch of sea salt. Pack of six.",
                    new BigDecimal("950.00"), "/images/bakery/product-cookies.webp"));

    public List<DemoProduct> all() {
        return PRODUCTS;
    }

    /** The product with this exact ID (case-insensitive, trimmed), if it is on the menu. */
    public Optional<DemoProduct> find(String id) {
        if (id == null) return Optional.empty();
        String key = id.strip();
        return PRODUCTS.stream().filter(p -> p.id().equalsIgnoreCase(key)).findFirst();
    }
}
