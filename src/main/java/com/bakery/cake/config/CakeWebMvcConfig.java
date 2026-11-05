package com.bakery.cake.config;

import com.bakery.cake.service.CakeBookingService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC configuration for the Custom Cake Booking module.
 *
 * <p>Registers a resource handler so that uploaded design images can be
 * served via a URL like {@code /cake-images/uuid.jpg} without exposing
 * the real filesystem path to the client.
 *
 * <p>OOP: Constructor injection – we inject the service to get the upload
 * directory path, keeping it configured in one place (application.properties).
 */
@Configuration
public class CakeWebMvcConfig implements WebMvcConfigurer {

    private final CakeBookingService bookingService;

    /**
     * Constructor injection.
     *
     * @param bookingService provides the configured upload directory path
     */
    public CakeWebMvcConfig(CakeBookingService bookingService) {
        this.bookingService = bookingService;
    }

    /**
     * Maps the URL path {@code /cake-images/**} to the upload directory on disk.
     *
     * <p>Example: {@code /cake-images/a1b2c3.jpg} serves the file at
     * {@code uploads/cake-designs/a1b2c3.jpg} on the server filesystem.
     *
     * @param registry the Spring resource handler registry
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // "file:" prefix tells Spring to look on the local filesystem, not the classpath
        String uploadPath = bookingService.getUploadDir().toUri().toString();
        registry.addResourceHandler("/cake-images/**")
                .addResourceLocations(uploadPath);
    }
}

