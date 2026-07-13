package com.jousting;

import org.bukkit.plugin.java.JavaPlugin;

public class JoustingPlugin extends JavaPlugin {
    private JoustingConfig config;
    private MomentumBarManager barManager;
    private CooldownManager cooldowns;
    private LanceItems lances;
    private MomentumTask momentumTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        config = new JoustingConfig(this);

        barManager = new MomentumBarManager();
        cooldowns = new CooldownManager();
        lances = new LanceItems(this);

        // Per-tick momentum poll (drives the momentum bar reliably for mounted players).
        // Created before the listener, which reaches it through getMomentumTask().
        momentumTask = new MomentumTask(this, config, barManager);
        momentumTask.runTaskTimer(this, 1L, 1L);

        JoustingListener listener = new JoustingListener(this, config, barManager, cooldowns, lances);
        getServer().getPluginManager().registerEvents(listener, this);

        getCommand("jousting").setExecutor(new JoustingCommand(config, lances));

        getLogger().info("JoustingPlugin enabled!");
    }

    @Override
    public void onDisable() {
        if (momentumTask != null) momentumTask.cancel();
        if (barManager != null) barManager.clearAll();
        if (cooldowns != null) cooldowns.clearAll();
        MomentumTracker.clearAll();
        getLogger().info("JoustingPlugin disabled!");
    }

    public JoustingConfig getJoustingConfig() {
        return config;
    }

    public MomentumTask getMomentumTask() {
        return momentumTask;
    }
}
