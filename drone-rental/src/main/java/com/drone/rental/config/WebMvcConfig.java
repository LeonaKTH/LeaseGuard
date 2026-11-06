package com.drone.rental.config;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.servlet.config.annotation.*;
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
 @Value("${file.upload-path:uploads/}") private String uploadPath;
 @Override public void addResourceHandlers(ResourceHandlerRegistry registry){
  registry.addResourceHandler("/uploads/**").addResourceLocations(java.nio.file.Path.of(uploadPath).toAbsolutePath().toUri().toString());
 }
}
