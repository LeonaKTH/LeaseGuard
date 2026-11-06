package com.drone.rental;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.sql.*;
import java.util.*;
class MigrationG2IT {
 @Test void migrateFreshTestAndExistingG1CatalogsWithoutChangingOriginalRows() throws Exception {
  var evidence=new ArrayList<Map<String,Object>>();
  for(String schema:List.of("leaseguard_test_g2_fresh","leaseguard_dev","leaseguard_test")){
   String account=schema.equals("leaseguard_test_g2_fresh")?"leaseguard_g2_fresh":schema.equals("leaseguard_dev")?"leaseguard_migrate_dev":System.getenv("LEASEGUARD_MIGRATION_USER");
   String password=System.getenv(schema.equals("leaseguard_test_g2_fresh")?"LEASEGUARD_FRESH_PASSWORD":schema.equals("leaseguard_dev")?"LEASEGUARD_DEV_MIGRATION_PASSWORD":"LEASEGUARD_MIGRATION_PASSWORD");
   String url="jdbc:mysql://127.0.0.1:13308/"+schema+"?useSSL=false&allowPublicKeyRetrieval=true";
   try(Connection c=DriverManager.getConnection(url,account,password)){
    Map<String,List<String>> oldColumns=new TreeMap<>();Map<String,List<String>> before=new TreeMap<>();Map<String,String> oldIds=new TreeMap<>();
    boolean hadSystem;
    try(var st=c.createStatement();var r=st.executeQuery("SELECT COUNT(*) FROM user WHERE username='__leaseguard_timeout__'")){r.next();hadSystem=r.getLong(1)==1;}
    try(var rs=c.getMetaData().getTables(schema,null,"%",new String[]{"TABLE"})){while(rs.next()){
     String t=rs.getString("TABLE_NAME");if(t.equals("flyway_schema_history"))continue;List<String> cols=new ArrayList<>();
     try(var cr=c.getMetaData().getColumns(schema,null,t,"%")){while(cr.next())cols.add(cr.getString("COLUMN_NAME"));}
     oldColumns.put(t,cols);before.put(t,rows(c,t,cols));
     List<String> ids=new ArrayList<>();try(var st=c.createStatement();var r=st.executeQuery("SELECT id FROM `"+t+"`")){while(r.next())ids.add(Long.toString(r.getLong(1)));}oldIds.put(t,ids.isEmpty()?"NULL":String.join(",",ids));
    }}
    var flyway=Flyway.configure().dataSource(url,account,password).cleanDisabled(true).baselineOnMigrate(false).locations("classpath:db/migration").load();flyway.migrate();flyway.validate();
    assertEquals(List.of("1","2","3","4","5","6"),Arrays.stream(flyway.info().applied()).map(i->i.getVersion().toString()).toList());
    for(var entry:oldColumns.entrySet()){
     assertEquals(before.get(entry.getKey()),rows(c,entry.getKey(),entry.getValue(),oldIds.get(entry.getKey())),schema+" original columns/rows changed in "+entry.getKey());
     int added=entry.getKey().equals("user") && !hadSystem?1:0;
     assertEquals(before.get(entry.getKey()).size()+added,rows(c,entry.getKey(),entry.getValue()).size(),schema+" unexpected row additions/deletions in "+entry.getKey());
    }
    assertEquals(1,rows(c,"user",List.of("id"),"SELECTOR").size());
    evidence.add(Map.of("schema",schema,"original_tables_checked",oldColumns.size(),"original_rows_checked",before.values().stream().mapToInt(List::size).sum(),"only_expected_system_actor_added",!hadSystem,"original_fields_identical",true,"migrations",Arrays.stream(flyway.info().applied()).map(i->Map.of("version",i.getVersion().toString(),"checksum",i.getChecksum())).toList()));
    java.nio.file.Files.writeString(java.nio.file.Path.of(System.getenv("LEASEGUARD_EVIDENCE_DIR"),"v5-existing-migration.json"),new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(evidence));
   }
  }
 }
 static List<String> rows(Connection c,String table,List<String> cols)throws Exception{
  return rows(c,table,cols,null);
 }
 static List<String> rows(Connection c,String table,List<String> cols,String ids)throws Exception{
  String select=cols.stream().map(s->"`"+s+"`").collect(java.util.stream.Collectors.joining(","));List<String> out=new ArrayList<>();
  try(var s=c.createStatement();var rs=s.executeQuery("SELECT "+select+" FROM `"+table+"`"+(ids==null?"":ids.equals("SELECTOR")?" WHERE principal_kind='SYSTEM' AND status=0 AND role=0":" WHERE id IN("+ids+")")+" ORDER BY id")){while(rs.next()){List<String> r=new ArrayList<>();for(int i=1;i<=cols.size();i++)r.add(rs.getString(i));out.add(r.toString());}}
  return out;
 }
}
