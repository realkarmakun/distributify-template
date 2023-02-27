package org.example;

import com.google.inject.Inject;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;
import ru.meproject.distributify.api.DistributedHashMap;
import ru.meproject.distributify.api.DistributedLongCounter;
import ru.meproject.distributify.api.DistributifyFactory;
import ru.meproject.distributify.api.jedis.JedisDistributifyFactory;
import ru.meproject.distributify.api.jedis.JedisDriverConfig;

import java.util.concurrent.TimeUnit;

@Plugin(id = "distributify-template",
        name = "DistributifyTemplate",
        version = "0.1.0-SNAPSHOT",
        url = "https://example.org",
        description = "Template for setting example and testing Distributify API",
        authors = {"someone else"},
        dependencies = {
            @Dependency(id = "distributify")
        }
        // Make it optional
        /*
        dependencies = {
        @Dependency(id = "distributify", optional = true)
        }
         */
)
public class DistributifyTemplate {

    private final ProxyServer server;
    private final Logger logger;

    private DistributifyFactory distributifyFactory;

    public DistributedLongCounter longCounter;
    public DistributedHashMap<String> hashMap;

    @Inject
    public DistributifyTemplate(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
        logger.info("Hello there! I'm using Distributify!");
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        // Initialize Factory
        distributifyFactory = new JedisDistributifyFactory("localhost", 6379, 100, "pwd", 10, 10);
        // Create structures
        //   Settings for structures
        var counterConfig = new JedisDriverConfig().keyPattern("distributifytest:counter");

        var hashMapConfig = new JedisDriverConfig()
                .keyPattern("distributifytest:hashmap")
                .expireSeconds(10L);

        // Make structures. Structures from same factory use same connection pool
        longCounter = distributifyFactory.longCounter(counterConfig);

        hashMap = distributifyFactory.hashMap(hashMapConfig,
                this::noSerialization,
                this::noSerialization,
                exception -> logger.error("Error occurred", exception));

        // Do stuff with structure
        longCounter.reset();

        // Debug Output
        server.getScheduler()
                .buildTask(this, () -> logger.info("LongCounter is now " + longCounter.sum()))
                .repeat(5, TimeUnit.SECONDS).schedule();
    }

    @Subscribe
    public void onPlayerChatEvent(PlayerChatEvent event) {
        longCounter.increment();
    }

    @Subscribe
    public void onLoginEvent(LoginEvent event) {
        if (!hashMap.containsKey(event.getPlayer().getUsername())) {
            hashMap.putExpiring(event.getPlayer().getUsername(), event.getPlayer().getRemoteAddress().toString(), 10);
            return;
        }
        event.setResult(ResultedEvent.ComponentResult.denied(Component.empty().content("You are already in the map! Wait 10 seconds!")));
    }

    @Subscribe
    public void onDisconnectEvent(DisconnectEvent event) {
        // We can remove entries from the map. But we won't since we use expiring keys
        // hashMap.remove(event.getPlayer().getUsername());
    }

    // This should be your own serialization/deserialization logic. For testing purposes we use plain strings as is
    private String noSerialization(String string) {
        return string;
    }
}
