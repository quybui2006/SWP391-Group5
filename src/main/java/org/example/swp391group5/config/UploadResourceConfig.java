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

    public UploadResourceConfig(@Value("${app.upload.product-dir:uploads/products}") String uploadDirectory) {
        this.uploadLocation = Path.of(uploadDirectory).toAbsolutePath().normalize().toUri().toString();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/products/**")
                .addResourceLocations(uploadLocation.endsWith("/") ? uploadLocation : uploadLocation + "/");
    }
}
