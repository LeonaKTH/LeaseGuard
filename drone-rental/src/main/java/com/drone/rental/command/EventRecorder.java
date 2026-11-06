package com.drone.rental.command;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
@Service
public class EventRecorder {
 private final JdbcTemplate jdbc;
 public EventRecorder(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public void record(String type,long id,String event,Integer from,Integer to){
  var c=CommandContext.required();
  jdbc.update("INSERT INTO business_event(aggregate_type,aggregate_id,command_id,seq_no,event_type,from_status,to_status,actor_id) VALUES (?,?,?,?,?,?,?,?)",type,id,c.id,++c.sequence,event,from,to,c.actor);
 }
}
