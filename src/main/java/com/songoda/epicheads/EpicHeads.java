package com.songoda.epicheads;

import com.songoda.epicheads.command.CommandManager;
import com.songoda.epicheads.economy.Economy;
import com.songoda.epicheads.economy.ItemEconomy;
import com.songoda.epicheads.economy.PlayerPointsEconomy;
import com.songoda.epicheads.economy.VaultEconomy;
import com.songoda.epicheads.head.Category;
import com.songoda.epicheads.head.Head;
import com.songoda.epicheads.head.HeadManager;
import com.songoda.epicheads.listeners.DeathListeners;
import com.songoda.epicheads.listeners.ItemListeners;
import com.songoda.epicheads.listeners.LoginListeners;
import com.songoda.epicheads.players.EPlayer;
import com.songoda.epicheads.players.PlayerManager;
import com.songoda.epicheads.utils.Methods;
import com.songoda.epicheads.utils.Metrics;
import com.songoda.epicheads.utils.ServerVersion;
import com.songoda.epicheads.utils.gui.AbstractGUI;
import com.songoda.epicheads.utils.settings.Setting;
import com.songoda.epicheads.utils.settings.SettingsManager;
import com.songoda.epicheads.utils.storage.Storage;
import com.songoda.epicheads.utils.storage.StorageRow;
import com.songoda.epicheads.utils.storage.types.StorageYaml;
import com.songoda.epicheads.utils.updateModules.LocaleModule;
import com.songoda.update.Plugin;
import com.songoda.update.SongodaUpdate;
import org.apache.commons.lang.ArrayUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class EpicHeads extends JavaPlugin {
    private static CommandSender console = Bukkit.getConsoleSender();
    private static EpicHeads INSTANCE;
    private References references;

    private ServerVersion serverVersion = ServerVersion.fromPackageName(Bukkit.getServer().getClass().getPackage().getName());

    private HeadManager headManager;
    private PlayerManager playerManager;
    private SettingsManager settingsManager;
    private CommandManager commandManager;

    private Locale locale;
    private Storage storage;
    private Economy economy;

    public static EpicHeads getInstance() {
        return INSTANCE;
    }

    @Override
    public void onEnable() {
        INSTANCE = this;

        console.sendMessage(Methods.formatText("&a============================="));
        console.sendMessage(Methods.formatText("&7EpicHeads " + this.getDescription().getVersion() + " by &5Songoda <3!"));
        console.sendMessage(Methods.formatText("&7Action: &aEnabling&7..."));

        this.settingsManager = new SettingsManager(this);
        this.settingsManager.setupConfig();

        // Setup language
        String langMode = Setting.LANGUGE_MODE.getString();
        Locale.init(this);
        Locale.saveDefaultLocale("en_US");
        this.locale = Locale.getLocale(getConfig().getString("System.Language Mode", langMode));

        //Running Songoda Updater
        Plugin plugin = new Plugin(this, 26);
        plugin.addModule(new LocaleModule());
        SongodaUpdate.load(plugin);

        this.references = new References();
        this.storage = new StorageYaml(this);

        // Setup Managers
        this.headManager = new HeadManager();
        this.playerManager = new PlayerManager();
        this.commandManager = new CommandManager(this);

        // Register Listeners
        AbstractGUI.initializeListeners(this);
        Bukkit.getPluginManager().registerEvents(new DeathListeners(this), this);
        Bukkit.getPluginManager().registerEvents(new ItemListeners(this), this);
        Bukkit.getPluginManager().registerEvents(new LoginListeners(this), this);

        // Setup Economy
        if (Setting.VAULT_ECONOMY.getBoolean()
                && getServer().getPluginManager().getPlugin("Vault") != null)
            this.economy = new VaultEconomy(this);
        else if (Setting.PLAYER_POINTS_ECONOMY.getBoolean()
                && getServer().getPluginManager().getPlugin("PlayerPoints") != null)
            this.economy = new PlayerPointsEconomy(this);
        else if (Setting.ITEM_ECONOMY.getBoolean())
            this.economy = new ItemEconomy(this);

        // Download Heads
        downloadHeads();

        // Load Heads
        loadHeads();

        // Load Favorites
        loadData();

        int timeout = Setting.AUTOSAVE.getInt() * 60 * 20;
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, this::saveToFile, timeout, timeout);

        // Start Metrics
        new Metrics(this);

        console.sendMessage(Methods.formatText("&a============================="));
    }

    @Override
    public void onDisable() {
        this.storage.closeConnection();
        this.saveToFile();
        console.sendMessage(Methods.formatText("&a============================="));
        console.sendMessage(Methods.formatText("&7EpicHeads " + this.getDescription().getVersion() + " by &5Songoda <3!"));
        console.sendMessage(Methods.formatText("&7Action: &cDisabling&7..."));
        console.sendMessage(Methods.formatText("&a============================="));
    }

    private void saveToFile() {
        storage.doSave();
    }

    private void loadData() {
        // Adding in favorites.
        if (storage.containsGroup("players")) {
            for (StorageRow row : storage.getRowsByGroup("players")) {
                if (row.get("uuid").asObject() == null)
                    continue;

                EPlayer player = new EPlayer(
                        UUID.fromString(row.get("uuid").asString()),
                        (List<String>)row.get("favorites").asObject());

                this.playerManager.addPlayer(player);
            }
        }

        // Save data initially so that if the person reloads again fast they don't lose all their data.
        this.saveToFile();
    }


    private void downloadHeads() {
        try {
            InputStream is = new URL("http://www.head-db.com/dump").openStream();
            BufferedReader rd = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
            String jsonText = readAll(rd);
            JSONParser parser = new JSONParser();
            JSONArray json = (JSONArray) parser.parse(jsonText);

            try (FileWriter file = new FileWriter(getDataFolder() + "/heads.json")) {
                file.write(json.toJSONString());
            }
        } catch (Exception e) {
            System.out.println("Failed to download heads.");
        }
    }

    private boolean loadHeads() {
        try {
            this.headManager.clear();
            this.headManager.addCategory(new Category("Latest Pack", true));

            JSONParser parser = new JSONParser();
            JSONArray jsonArray = (JSONArray) parser.parse(new FileReader(getDataFolder() + "/heads.json"));

            for (Object o : jsonArray) {
                JSONObject jsonObject = (JSONObject) o;

                String categoryStr = (String) jsonObject.get("tags");
                Optional<Category> tagOptional = headManager.getCategories().stream().filter(t -> t.getName().equalsIgnoreCase(categoryStr)).findFirst();

                Category category = tagOptional.orElseGet(() -> new Category(categoryStr));

                int id = Integer.parseInt((String) jsonObject.get("id"));

                if (Setting.DISABLED_HEADS.getIntegerList().contains(id)) continue;

                Head head = new Head(id,
                        (String) jsonObject.get("name"),
                        (String) jsonObject.get("url"),
                        category,
                        (String) jsonObject.get("pack"),
                        Byte.parseByte((String) jsonObject.get("staff_picked")));

                if (head.getName() == null || head.getName().equals("null")
                        || head.getPack() != null && head.getPack().equals("null")) continue;

                if (!tagOptional.isPresent())
                    headManager.addCategory(category);
                headManager.addHead(head);
            }

            if (storage.containsGroup("local")) {
                for (StorageRow row : storage.getRowsByGroup("local")) {
                    String tagStr = row.get("category").asString();

                    Optional<Category> tagOptional = headManager.getCategories().stream()
                            .filter(t -> t.getName().equalsIgnoreCase(tagStr)).findFirst();

                    Category category = tagOptional.orElseGet(() -> new Category(tagStr));

                    Head head = new Head(row.get("id").asInt(),
                            row.get("name").asString(),
                            row.get("url").asString(),
                            category,
                            null,
                            (byte) 0);

                    if (!tagOptional.isPresent())
                        headManager.addCategory(category);
                    headManager.addLocalHead(head);
                }
            }

            System.out.println("loaded " + headManager.getHeads().size() + " Heads.");

        } catch (IOException e) {
            System.out.println("Heads file not found. Plugin disabling.");
            return false;
        } catch (ParseException e) {
            System.out.println("Failed to parse heads file. Plugin disabling.");
            return false;
        }
        return true;
    }

    private static String readAll(Reader rd) throws IOException {
        StringBuilder sb = new StringBuilder();
        int cp;
        while ((cp = rd.read()) != -1) {
            sb.append((char) cp);
        }
        return sb.toString();
    }


    public ServerVersion getServerVersion() {
        return serverVersion;
    }

    public boolean isServerVersion(ServerVersion version) {
        return serverVersion == version;
    }
    public boolean isServerVersion(ServerVersion... versions) {
        return ArrayUtils.contains(versions, serverVersion);
    }

    public boolean isServerVersionAtLeast(ServerVersion version) {
        return serverVersion.ordinal() >= version.ordinal();
    }

    public void reload() {
        saveToFile();
        locale.reloadMessages();
        references = new References();
        settingsManager.reloadConfig();
        saveToFile();
        downloadHeads();
        loadHeads();
    }

    public Locale getLocale() {
        return locale;
    }

    public Economy getEconomy() {
        return economy;
    }

    public References getReferences() {
        return references;
    }

    public HeadManager getHeadManager() {
        return headManager;
    }

    public PlayerManager getPlayerManager() {
        return playerManager;
    }

    public CommandManager getCommandManager() {
        return commandManager;
    }

    public SettingsManager getSettingsManager() {
        return settingsManager;
    }
}
