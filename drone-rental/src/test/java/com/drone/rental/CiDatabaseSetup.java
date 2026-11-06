package com.drone.rental;
import org.flywaydb.core.Flyway;
/** A NEW ephemeral GitHub Actions database only; prepares the accepted V1 starting point. */
public class CiDatabaseSetup {
 public static void main(String[] args){
  if(!"true".equals(System.getenv("GITHUB_ACTIONS")))throw new IllegalStateException("CI-only fixture");
  for(String schema:java.util.List.of("leaseguard_dev","leaseguard_test","leaseguard_test_g2_fresh")){
   String user=schema.equals("leaseguard_dev")?"leaseguard_migrate_dev":schema.equals("leaseguard_test")?"leaseguard_migrate_test":"leaseguard_g2_fresh";
   String pw=System.getenv(schema.equals("leaseguard_dev")?"LEASEGUARD_DEV_MIGRATION_PASSWORD":schema.equals("leaseguard_test")?"LEASEGUARD_MIGRATION_PASSWORD":"LEASEGUARD_FRESH_PASSWORD");
   var f=Flyway.configure().dataSource("jdbc:mysql://127.0.0.1:13308/"+schema+"?allowPublicKeyRetrieval=true&useSSL=false",user,pw).cleanDisabled(true).baselineOnMigrate(false).target("1").load();
   if(f.info().applied().length!=0)throw new IllegalStateException("CI setup refuses an existing schema");f.migrate();f.validate();
  }
 }
}
