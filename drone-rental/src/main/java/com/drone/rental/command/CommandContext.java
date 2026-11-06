package com.drone.rental.command;
/** Request-local metadata, never credentials; installed only while a command transaction executes. */
public final class CommandContext {
 private static final ThreadLocal<CommandContext> LOCAL=new ThreadLocal<>();
 public long id,actor;public Long resource,device;public String operation,key;public int sequence,attempt;
 public static CommandContext current(){return LOCAL.get();}
 public static CommandContext required(){var c=current();if(c==null)throw new IllegalStateException("Business command transaction required");return c;}
 static void install(CommandContext c){LOCAL.set(c);}static void clear(){LOCAL.remove();}
}
