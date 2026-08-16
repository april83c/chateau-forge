package cc.minota.chateau.client;

import cc.minota.chateau.Chateau;
import cc.minota.chateau.ChateauConfig;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.common.MinecraftForge;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public final class ChateauClient {
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    private ChateauClient() {
    }

    public static void init() {
        ChateauConfig.load();
        Chateau.LOGGER.info("Chateau initialized! Server: {}, ID: {}", ChateauConfig.server, ChateauConfig.id);

        MinecraftForge.EVENT_BUS.addListener(ChateauClient::onRegisterClientCommands);
        MinecraftForge.EVENT_BUS.addListener(ChateauClient::onClientChat);
    }

    private static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("ct")
                        .then(Commands.literal("server")
                                .then(Commands.argument("url", StringArgumentType.greedyString())
                                        .executes(context -> {
                                            ChateauConfig.server = StringArgumentType.getString(context, "url");
                                            ChateauConfig.save();
                                            feedback(context.getSource(), "§aServer set to: " + ChateauConfig.server);
                                            return 1;
                                        })
                                )
                        )
                        .then(Commands.literal("id")
                                .then(Commands.argument("id", StringArgumentType.string())
                                        .executes(context -> {
                                            ChateauConfig.id = StringArgumentType.getString(context, "id");
                                            ChateauConfig.save();
                                            feedback(context.getSource(), "§aID set to: " + ChateauConfig.id);
                                            return 1;
                                        })
                                )
                        )
                        .then(Commands.literal("toggle")
                                .executes(context -> {
                                    ChateauConfig.enabled = !ChateauConfig.enabled;
                                    ChateauConfig.save();
                                    String status = ChateauConfig.enabled ? "§aenabled" : "§cdisabled";
                                    feedback(context.getSource(), "Chateau " + status);
                                    return 1;
                                })
                        )
                        .then(Commands.literal("dontsend")
                                .executes(context -> {
                                    ChateauConfig.dontSend = !ChateauConfig.dontSend;
                                    ChateauConfig.save();
                                    String status = ChateauConfig.dontSend
                                            ? "§aON (messages won't be sent to chat)"
                                            : "§cOFF (messages will be sent to chat)";
                                    feedback(context.getSource(), "Don't Send mode: " + status);
                                    return 1;
                                })
                        )
                        .then(Commands.literal("status")
                                .executes(context -> {
                                    String status = ChateauConfig.enabled ? "§aenabled" : "§cdisabled";
                                    String sendStatus = ChateauConfig.dontSend
                                            ? "§cOFF (not sending to chat)"
                                            : "§aON (sending to chat)";
                                    feedback(context.getSource(), "§6Chateau Status:");
                                    feedback(context.getSource(), "  Status: " + status);
                                    feedback(context.getSource(), "  Don't Send: " + sendStatus);
                                    feedback(context.getSource(), "  Server: §b" + ChateauConfig.server);
                                    feedback(context.getSource(), "  ID: §b" + ChateauConfig.id);
                                    return 1;
                                })
                        )
        );
    }

    private static void onClientChat(ClientChatEvent event) {
        if (!ChateauConfig.enabled) {
            return; // Allow the message to be sent if mod is disabled
        }

        String message = event.getMessage();
        // Forge sends commands down a separate path (ClientCommandHandler) so they shouldn't reach
        // us at all, but guard anyway rather than risk swallowing one.
        if (message.startsWith("/")) {
            return;
        }

        boolean hasExclamation = message.startsWith("!");
        String actualMessage = hasExclamation ? message.substring(1) : message;

        // Send to webhook
        sendWebhookAsync(actualMessage);

        // Determine if we should send to chat
        boolean shouldSendToChat;
        if (ChateauConfig.dontSend) {
            // Don't send mode ON: only send to chat if message starts with !
            shouldSendToChat = hasExclamation;
        } else {
            // Don't send mode OFF: always send to chat (unless it has !)
            shouldSendToChat = !hasExclamation;
        }

        if (!shouldSendToChat) {
            event.setCanceled(true);
        } else if (hasExclamation) {
            // The ! is a prefix for us, not something to say out loud. An empty result
            // (the message was just "!") is dropped by Forge before it reaches the server.
            event.setMessage(actualMessage);
        }
    }

    private static void sendWebhookAsync(String message) {
        String url = ChateauConfig.server + "/input/keys/" + ChateauConfig.id;

        try {
            String jsonPayload = "{\"sentence\":\"" + escape(message) + "\"}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
                    .build();

            HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> Chateau.LOGGER.info("Sent to {} - Status: {}", url, response.statusCode()))
                    .exceptionally(throwable -> {
                        Chateau.LOGGER.error("Failed to send webhook: {}", throwable.getMessage());
                        return null;
                    });
        } catch (Exception e) {
            Chateau.LOGGER.error("Failed to send webhook: {}", e.getMessage());
        }
    }

    private static void feedback(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal(message), false);
    }

    private static String escape(String value) {
        StringBuilder builder = new StringBuilder(value.length());

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> builder.append("\\\\");
                case '"' -> builder.append("\\\"");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (c < 0x20) {
                        builder.append(String.format("\\u%04x", (int) c));
                    } else {
                        builder.append(c);
                    }
                }
            }
        }

        return builder.toString();
    }
}
