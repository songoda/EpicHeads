package com.songoda.epicheads.listeners;

import com.songoda.core.utils.ItemUtils;
import com.songoda.epicheads.EpicHeads;
import com.songoda.epicheads.head.Category;
import com.songoda.epicheads.head.Head;
import com.songoda.epicheads.head.HeadManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Optional;

public class LoginListeners implements Listener {

    private final EpicHeads plugin;

    public LoginListeners(EpicHeads plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void loginEvent(PlayerLoginEvent event) {
        HeadManager headManager = plugin.getHeadManager();

        Player player = event.getPlayer();

        String encodedStr = ItemUtils.getSkullTexture(player);

        if (encodedStr == null) return;

        String url = ItemUtils.getDecodedTexture(encodedStr);

        String tagStr = plugin.getLocale().getMessage("general.word.playerheads").getMessage();

        Optional<Category> tagOptional = headManager.getCategories()
                .stream().filter(t -> t.getName().equalsIgnoreCase(tagStr)).findFirst();

        Category tag = tagOptional.orElseGet(() -> new Category(tagStr));

        if (!tagOptional.isPresent())
            headManager.addCategory(tag);

        Optional<Head> optional = headManager.getLocalHeads().stream()
                .filter(h -> h.getName().equalsIgnoreCase(event.getPlayer().getName())).findFirst();

        int id = headManager.getNextLocalId();

        if (optional.isPresent()) {
            Head head = optional.get();
            head.setURL(url);
            plugin.getDataManager().updateLocalHead(head);
            return;
        }

        Head head = new Head(id, player.getName(), url, tag, true, null, (byte) 0);
        headManager.addLocalHead(head);
        plugin.getDataManager().createLocalHead(head);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getDataManager().getPlayer(event.getPlayer(), ePlayer -> plugin.getPlayerManager().addPlayer(ePlayer));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getDataManager().updatePlayer(plugin.getPlayerManager().getPlayer(event.getPlayer()));
    }

}
