package com.drone.rental.command;
/** No production implementation or HTTP switch. Tests supply a bean for controlled fault injection. */
public interface CommandHooks {
 void at(String point,CommandContext context) throws Exception;
 CommandHooks NONE=(point,context)->{};
}
