package com.josaca.status;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public final class MinecraftStatusPlugin extends JavaPlugin {

    private BukkitTask updateTask;
    private HttpClient httpClient;
    private String webhookUrl;
    private String lastMessageId;
    private String updateMode;

    private File dataFile;
    private FileConfiguration dataConfig;
    
    private File embedFile;
    private FileConfiguration embedConfig;

    @Override
    public void onEnable() {
        // Initialize HttpClient with its own executor to avoid blocking server threads
        httpClient = HttpClient.newBuilder()
                .executor(Executors.newSingleThreadExecutor())
                .build();

        // Load configurations
        saveDefaultConfig();
        saveDefaultEmbedConfig();
        loadDataFile();

        webhookUrl = getConfig().getString("webhook-url", "");
        if (webhookUrl.isEmpty() || webhookUrl.equals("YOUR_WEBHOOK_URL_HERE")) {
            getLogger().severe("Discord Webhook URL is not configured in config.yml!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        updateMode = getConfig().getString("update-mode", "replace").toLowerCase();
        lastMessageId = dataConfig.getString("last-message-id");

        // Schedule asynchronous task
        long updateInterval = getConfig().getLong("update-interval", 60) * 20; // Convert seconds to ticks
        updateTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> sendStatusUpdate(true), 0L, updateInterval);

        getLogger().info("MinecraftStatusPlugin has been enabled.");
    }

    @Override
    public void onDisable() {
        // Cancel the asynchronous task if it's active
        if (updateTask != null && !updateTask.isCancelled()) {
            updateTask.cancel();
        }

        // Send OFFLINE status synchronously to ensure it gets sent
        getLogger().info("Sending OFFLINE status to Discord...");
        sendStatusUpdate(false).join(); // .join() to wait for completion

        // Save the last message ID
        saveDataFile();

        getLogger().info("MinecraftStatusPlugin has been disabled.");
    }

    private CompletableFuture<Void> sendStatusUpdate(boolean isOnline) {
        // If mode is "edit" and we have a message ID, edit it.
        if ("edit".equals(updateMode) && lastMessageId != null && !lastMessageId.isEmpty()) {
            return editLastMessage(isOnline);
        }

        // Otherwise, use the replace method (delete and create).
        // This will also be used the first time a message is sent.
        return deleteLastMessage().thenCompose(v -> {
            String payload = buildEmbedPayload(isOnline);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl + "?wait=true")) // wait=true to get the message ID
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() >= 200 && response.statusCode() < 300) {
                            String responseBody = response.body();
                            JsonObject jsonResponse = com.google.gson.JsonParser.parseString(responseBody).getAsJsonObject();
                            if (jsonResponse.has("id")) {
                                lastMessageId = jsonResponse.get("id").getAsString();
                                dataConfig.set("last-message-id", lastMessageId);
                                saveDataFile(); // Save immediately
                            }
                        } else {
                            getLogger().warning("Error sending message to Discord. Code: " + response.statusCode());
                            getLogger().warning("Response: " + response.body());
                            // If the message could not be created, clear the ID so a new one is created next time.
                            lastMessageId = null;
                            dataConfig.set("last-message-id", null);
                            saveDataFile();
                        }
                    }).exceptionally(ex -> {
                        getLogger().severe("Exception while sending request to Discord: " + ex.getMessage());
                        return null;
                    });
        });
    }

    private CompletableFuture<Void> editLastMessage(boolean isOnline) {
        String payload = buildEmbedPayload(isOnline);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(webhookUrl + "/messages/" + lastMessageId))
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(payload))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        getLogger().warning("Error editing message on Discord. Code: " + response.statusCode());
                        getLogger().warning("Response: " + response.body());

                        // If the error is 404 (Not Found), the message was deleted on Discord.
                        // Clear the ID so a new one is created next time.
                        if (response.statusCode() == 404) {
                            getLogger().info("Previous message not found. A new one will be created in the next cycle.");
                            lastMessageId = null;
                            dataConfig.set("last-message-id", null);
                            saveDataFile();
                        }
                    }
                }).exceptionally(ex -> {
                    getLogger().severe("Exception while editing message on Discord: " + ex.getMessage());
                    return null;
                });
    }

    private CompletableFuture<Void> deleteLastMessage() {
        if (lastMessageId == null || lastMessageId.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(webhookUrl + "/messages/" + lastMessageId))
                .DELETE()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    // 204 No Content (success), 404 Not Found (already deleted)
                    if (response.statusCode() != 204 && response.statusCode() != 404) {
                        getLogger().warning("Could not delete the previous message. Code: " + response.statusCode());
                    }
                }).exceptionally(ex -> {
                    getLogger().severe("Exception while deleting previous message: " + ex.getMessage());
                    return null;
                });
    }

    private String buildEmbedPayload(boolean isOnline) {
        JsonObject payload = new JsonObject();

        // Add username and avatar if configured
        String botUsername = getConfig().getString("bot-username", "");
        String avatarUrl = getConfig().getString("avatar-url", "");
        if (!botUsername.isEmpty()) {
            payload.addProperty("username", botUsername);
        }
        if (!avatarUrl.isEmpty()) {
            payload.addProperty("avatar_url", avatarUrl);
        }

        // Build the embed
        JsonObject embed = new JsonObject();
        embed.addProperty("title", embedConfig.getString("title", "MINECRAFT SERVER STATUS"));
        
        String description = isOnline 
            ? embedConfig.getString("description.online", "🟢 **SERVER ONLINE** 🟢")
            : embedConfig.getString("description.offline", "🔴 **SERVER OFFLINE** 🔴");
        embed.addProperty("description", description);

        int color = isOnline
            ? embedConfig.getInt("color.online", 65280)
            : embedConfig.getInt("color.offline", 16711680);
        embed.addProperty("color", color);

        embed.addProperty("timestamp", Instant.now().toString());
        
        JsonObject footer = new JsonObject();
        footer.addProperty("text", embedConfig.getString("footer", "Magic Art Hat - Minecraft Status"));
        embed.add("footer", footer);

        JsonArray fields = new JsonArray();
        List<Map<?, ?>> fieldList = embedConfig.getMapList("fields");
        for (Map<?, ?> fieldMap : fieldList) {
            JsonObject field = new JsonObject();
            String name = replacePlaceholders(fieldMap.get("name").toString(), isOnline);
            String value = replacePlaceholders(fieldMap.get("value").toString(), isOnline);
            
            field.addProperty("name", name);
            field.addProperty("value", value);
            field.addProperty("inline", (Boolean) fieldMap.get("inline"));
            fields.add(field);
        }
        embed.add("fields", fields);

        JsonArray embeds = new JsonArray();
        embeds.add(embed);
        payload.add("embeds", embeds);

        return payload.toString();
    }

    private String replacePlaceholders(String text, boolean isOnline) {
        if (text == null) return "";
        
        String onlinePlayers = isOnline ? String.valueOf(Bukkit.getOnlinePlayers().size()) : "0";
        String maxPlayers = isOnline ? String.valueOf(Bukkit.getMaxPlayers()) : "0";

        return text
                .replace("{serverIp}", getConfig().getString("server-ip", "N/A"))
                .replace("{serverVersion}", Bukkit.getBukkitVersion().split("-")[0])
                .replace("{onlinePlayers}", onlinePlayers)
                .replace("{maxPlayers}", maxPlayers);
    }

    private void saveDefaultEmbedConfig() {
        if (embedFile == null) {
            embedFile = new File(getDataFolder(), "embed.yml");
        }
        if (!embedFile.exists()) {
            saveResource("embed.yml", false);
        }
        embedConfig = YamlConfiguration.loadConfiguration(embedFile);
    }

    private void loadDataFile() {
        dataFile = new File(getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                getLogger().severe("Could not create data.yml file!");
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void saveDataFile() {
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            getLogger().severe("Could not save data.yml file!");
        }
    }
}
