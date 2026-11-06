package com.drone.rental.config;
import org.springframework.context.annotation.*;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
@Configuration public class SwaggerConfig {
 @Bean OpenAPI leaseGuardApi(){return new OpenAPI().info(new Info().title("LeaseGuard").version("G4"))
 .components(new io.swagger.v3.oas.models.Components().addSecuritySchemes("bearerAuth",new io.swagger.v3.oas.models.security.SecurityScheme().type(io.swagger.v3.oas.models.security.SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
 .addSecurityItem(new io.swagger.v3.oas.models.security.SecurityRequirement().addList("bearerAuth"));}
}
