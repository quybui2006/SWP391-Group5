package org.example.swp391group5.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

/** Exposes product files saved outside the classpath at their stored URL. */
@Configuration
public class UploadResourceConfig implements WebMvcConfigurer {

    private final String uploadLocation;
    private final String complaintUploadLocation;

    public UploadResourceConfig(
            @Value("${app.upload.product-dir:uploads/products}") String uploadDirectory,
            @Value("${app.upload.complaint-dir:uploads/complaints}") String complaintDirectory) {
        this.uploadLocation = Path.of(uploadDirectory).toAbsolutePath().normalize().toUri().toString();
        this.complaintUploadLocation = Path.of(complaintDirectory).toAbsolutePath().normalize().toUri().toString();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/products/**")
                .addResourceLocations(uploadLocation.endsWith("/") ? uploadLocation : uploadLocation + "/");
        registry.addResourceHandler("/uploads/complaints/**")
                .addResourceLocations(complaintUploadLocation.endsWith("/") ? complaintUploadLocation : complaintUploadLocation + "/");
    }
}
