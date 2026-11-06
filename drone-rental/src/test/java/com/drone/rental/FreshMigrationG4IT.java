package com.drone.rental;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.flywaydb.core.Flyway;
import java.sql.*;
import java.util.*;
class FreshMigrationG4IT {
 @Test void newCatalogMigratesAllFiveVersionsAndNeverAssignsByName()throws Exception{
  String url="jdbc:mysql://127.0.0.1:13308/leaseguard_test_g4_fresh?useSSL=false&allowPublicKeyRetrieval=true";
  var flyway=Flyway.configure().dataSource(url,"leaseguard_g4_fresh",Objects.requireNonNull(System.getenv("LEASEGUARD_G4_FRESH_PASSWORD"))).cleanDisabled(true).baselineOnMigrate(false).locations("classpath:db/migration").load();
  flyway.migrate();flyway.validate();assertEquals(List.of("1","2","3","4","5","6"),Arrays.stream(flyway.info().applied()).map(m->m.getVersion().toString()).toList());
  try(var c=flyway.getConfiguration().getDataSource().getConnection();var st=c.createStatement()){
   try(var r=st.executeQuery("SELECT COUNT(*) FROM maintenance_ticket WHERE assignee_id IS NOT NULL")){r.next();assertEquals(0,r.getInt(1));}
   try(var r=st.executeQuery("SELECT COUNT(*) FROM information_schema.referential_constraints WHERE constraint_schema=DATABASE() AND constraint_name='fk_ticket_assignee'")){r.next();assertEquals(1,r.getInt(1));}
   try(var r=st.executeQuery("SELECT COUNT(*) FROM user WHERE principal_kind='SYSTEM' AND role=0 AND status=0")){r.next();assertEquals(1,r.getInt(1));}
  }
  java.nio.file.Files.writeString(java.nio.file.Path.of(System.getenv("LEASEGUARD_EVIDENCE_DIR"),"fresh-v5-result.json"),new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(Map.of("all_versions",Arrays.stream(flyway.info().applied()).map(m->Map.of("version",m.getVersion().toString(),"checksum",m.getChecksum())).toList(),"schema","leaseguard_test_g4_fresh","no_assignee_backfill",true,"root_business_account",false)));
 }
}
