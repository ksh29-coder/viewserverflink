package com.viewserver.viewserver.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web configuration for CORS, static resources, and client-side routing
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:3000", "http://localhost:8080")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
    
    /**
     * Configure resource handlers for static content
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .setCachePeriod(3600);
    }
    
    /**
     * Forward specific routes to index.html for React Router client-side routing
     */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // Forward specific known routes to index.html
        registry.addViewController("/account-overview")
                .setViewName("forward:/index.html");
        registry.addViewController("/aggregation")
                .setViewName("forward:/index.html");
        registry.addViewController("/portfolio")
                .setViewName("forward:/index.html");
    }
} 