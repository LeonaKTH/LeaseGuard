package com.drone.rental;
import org.flywaydb.core.Flyway;
import java.sql.*;
import java.util.*;
/** Explicit setup of NEW verifier catalogs; refuses any existing table. No clean or DROP. */
public class PortableDatabaseSetup {
 public static void main(String[] args)throws Exception{
  if(!"YES".equals(System.getenv("LEASEGUARD_SETUP_NEW_CATALOGS")))throw new IllegalStateException("Explicit new-catalog setup required");
  for(String schema:List.of("leaseguard_dev","leaseguard_test","leaseguard_test_g2_fresh","leaseguard_test_g4_fresh")){
   String account=schema.equals("leaseguard_dev")?"leaseguard_migrate_dev":schema.equals("leaseguard_test")?"leaseguard_migrate_test":schema.equals("leaseguard_test_g2_fresh")?"leaseguard_g2_fresh":"leaseguard_g4_fresh";
   String password=System.getenv(schema.equals("leaseguard_dev")?"LEASEGUARD_DEV_MIGRATION_PASSWORD":schema.equals("leaseguard_test")?"LEASEGUARD_MIGRATION_PASSWORD":schema.equals("leaseguard_test_g2_fresh")?"LEASEGUARD_FRESH_PASSWORD":"LEASEGUARD_G4_FRESH_PASSWORD");
   String url="jdbc:mysql://127.0.0.1:13308/"+schema+"?useSSL=false&allowPublicKeyRetrieval=true";
   try(var c=DriverManager.getConnection(url,account,password);var s=c.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()")){r.next();if(r.getInt(1)!=0)throw new IllegalStateException("Refuse existing tables in "+schema);}
   if(!schema.equals("leaseguard_test_g4_fresh"))Flyway.configure().dataSource(url,account,password).cleanDisabled(true).baselineOnMigrate(false).target("1").load().migrate();
  }
 }
}
