package com.drone.rental.config;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
@Configuration
public class MigrationConfig {
 @Bean FlywayMigrationStrategy guardedMigration(){return flyway->{
  try(var c=flyway.getConfiguration().getDataSource().getConnection()){
   String catalog=c.getCatalog();
   if(!java.util.Set.of("leaseguard_dev","leaseguard_test").contains(catalog))throw new IllegalStateException("Migration only allowed in LeaseGuard dev/test");
   if(!c.getMetaData().getURL().startsWith("jdbc:mysql://127.0.0.1:13308/"))throw new IllegalStateException("Dedicated local MySQL required");
  }catch(java.sql.SQLException e){throw new IllegalStateException("Cannot validate migration target",e);}
  flyway.migrate();
 };}
}
