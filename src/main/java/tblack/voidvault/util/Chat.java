package tblack.voidvault.util;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;

public final class Chat {
    private Chat() {
    }

    public static void send(CommandContext context, Message message) {
        context.sendMessage(message);
    }

    public static void send(CommandContext context, String message) {
        context.sendMessage(Message.raw(message));
    }

    public static Message ok(String message) {
        return Message.raw(message).color("#55FF9C");
    }

    public static Message error(String message) {
        return Message.raw(message).color("#FF6B81");
    }

    public static Message info(String message) {
        return Message.raw(message).color("#CFCFE6");
    }

    public static Message title(String message) {
        return Message.raw(message).color("#D48CFF");
    }
}
