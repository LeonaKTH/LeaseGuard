package com.drone.rental.command;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.ObjectProvider;
@Component
public class FaultPoints {
 private final ObjectProvider<CommandHooks> hooks;
 public FaultPoints(ObjectProvider<CommandHooks> hooks){this.hooks=hooks;}
 public void at(String point,CommandContext context){try{hooks.getIfAvailable(()->CommandHooks.NONE).at(point,context);}catch(RuntimeException e){throw e;}catch(Exception e){throw new CommandFailure(e);}}
 public static class CommandFailure extends RuntimeException {public CommandFailure(Throwable cause){super("Command interrupted",cause);}}
}
