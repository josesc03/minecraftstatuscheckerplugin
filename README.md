# Minecraft Server Status Discord Plugin

This is a simple yet powerful Bukkit/Spigot/Paper plugin that sends your Minecraft server's status to a Discord channel via a webhook. It periodically updates a single message to show whether your server is online or offline, along with other useful information like player count, server IP, and version.

## Features

- **Real-time Status**: Automatically updates the server status (Online/Offline) in your Discord channel.
- **Highly Customizable**: Use `config.yml` and `embed.yml` to completely change the appearance and content of the Discord message.
- **Efficient Updates**: Choose between two update modes:
  - `replace`: Deletes the old message and sends a new one (default).
  - `edit`: Edits the existing message, providing a cleaner channel history.
- **Graceful Shutdown**: Automatically sends an "Offline" status when the server stops.
- **Custom Bot Appearance**: Set a custom username and avatar for the status messages in Discord.
- **Dynamic Information**: Use placeholders to display server IP, version, and online player count.

## Installation

1.  Download the latest `.jar` file from the [Releases](https://github.com/josesc03/minecraftstatuscheckerplugin/releases) page.
2.  Place the `minecraftstatuscheckerplugin-1.0.0.jar` file into your server's `plugins` folder.
3.  Restart your server. This will generate the plugin's configuration folder (`/plugins/MinecraftStatusPlugin/`) containing `config.yml`, `embed.yml`, and `data.yml`.

## Configuration

### 1. Get a Discord Webhook URL

1.  In your Discord server, go to **Server Settings** -> **Integrations**.
2.  Click on **Webhooks** -> **New Webhook**.
3.  Customize the name (e.g., "Server Status") and choose the channel where messages will be sent.
4.  Click **Copy Webhook URL**.

### 2. Configure `config.yml`

Open `plugins/MinecraftStatusPlugin/config.yml` and edit the following settings:

```yaml
# URL of the Discord Webhook where status messages will be sent.
# THIS IS REQUIRED.
webhook-url: "YOUR_WEBHOOK_URL_HERE"

# Interval in seconds for sending status updates.
update-interval: 60

# The update mode for the Discord message.
# Available options:
#   "replace" - Deletes the previous message and sends a new one (default).
#   "edit"    - Edits the existing message instead of creating a new one.
update-mode: "replace"

# IP or domain of the server to be displayed in the embed.
server-ip: "mc.your-domain.com"

# (Optional) Username for the Discord bot. If left empty, the webhook's default name will be used.
bot-username: "Server Status"

# (Optional) Avatar URL for the Discord bot. If left empty, the webhook's default avatar will be used.
avatar-url: ""
```

### 3. Configure `embed.yml`

Open `plugins/MinecraftStatusPlugin/embed.yml` to customize the look of the Discord embed message.

#### Placeholders
You can use these placeholders in any text field (`title`, `description`, `footer`, `fields`):
- `{serverIp}`: Displays the server IP from `config.yml`.
- `{serverVersion}`: Displays the server's Minecraft version (e.g., 1.20.4).
- `{onlinePlayers}`: Displays the current number of online players.
- `{maxPlayers}`: Displays the maximum number of player slots.

#### Example `embed.yml`:
```yaml
# Configuration for the Discord Embed.
# You can use placeholders like {serverIp}, {serverVersion}, {onlinePlayers}, {maxPlayers}.

# Embed title.
title: "MINECRAFT SERVER STATUS"

# Description that changes based on the server's state.
description:
  online: "🟢 **SERVER ONLINE** 🟢"
  offline: "🔴 **SERVER OFFLINE** 🔴"

# Embed color (in decimal format).
# Green: 65280, Red: 16711680
color:
  online: 65280
  offline: 16711680

# Embed footer.
footer: "My Server Name - Minecraft Status"

# Embed fields.
# Each field has a 'name', 'value', and 'inline' (true/false).
fields:
  - name: "Server Address"
    value: "{serverIp}"
    inline: true
  - name: "Version"
    value: "{serverVersion}"
    inline: true
  - name: "Players"
    value: "{onlinePlayers}/{maxPlayers}"
    inline: true
```

## Building from Source (for Developers)

If you want to modify the code and build the plugin yourself:

1.  Clone the repository: `git clone https://github.com/your-repo/minecraft-status-checker.git`
2.  Navigate to the project directory: `cd minecraft-status-checker`
3.  Build the project using Gradle: `./gradlew build` (or `gradlew.bat build` on Windows).
4.  The compiled `.jar` file will be located in the `build/libs/` directory.

---

Feel free to open an issue if you find a bug or have a feature request!
