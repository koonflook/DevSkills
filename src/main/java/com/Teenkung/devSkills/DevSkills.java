package com.Teenkung.devSkills;

import com.Teenkung.devSkills.api.DevSkillAPI;
import com.Teenkung.devSkills.api.DevSkillsProvider;
import com.Teenkung.devSkills.command.DevSkillCommand;
import com.Teenkung.devSkills.config.ConfigManager;
import com.Teenkung.devSkills.config.HudConfig;
import com.Teenkung.devSkills.config.MessageManager;
import com.Teenkung.devSkills.config.SoundManager;
import com.Teenkung.devSkills.config.UiConfig;
import com.Teenkung.devSkills.gui.MenuManager;
import com.Teenkung.devSkills.integration.ActionBarBridge;
import com.Teenkung.devSkills.integration.CumulusFormBridge;
import com.Teenkung.devSkills.integration.DevSkillsPlaceholderResolver;
import com.Teenkung.devSkills.integration.FloodgateBridge;
import com.Teenkung.devSkills.integration.MythicLibStatBridge;
import com.Teenkung.devSkills.integration.MMOCoreManaProvider;
import com.Teenkung.devSkills.integration.PaperDialogBridge;
import com.Teenkung.devSkills.integration.PlaceholderApiBridge;
import com.Teenkung.devSkills.integration.ProtocolLibActionBarBridge;
import com.Teenkung.devSkills.listener.BlockSkillListener;
import com.Teenkung.devSkills.listener.CombatSkillListener;
import com.Teenkung.devSkills.listener.ManaAbilityListener;
import com.Teenkung.devSkills.listener.PassiveAbilityListener;
import com.Teenkung.devSkills.listener.PlayerLifecycleListener;
import com.Teenkung.devSkills.listener.SpecialSkillListener;
import com.Teenkung.devSkills.service.DevSkillApiImpl;
import com.Teenkung.devSkills.service.HudService;
import com.Teenkung.devSkills.service.LevelerService;
import com.Teenkung.devSkills.service.ManaAbilityService;
import com.Teenkung.devSkills.service.PassiveAbilityService;
import com.Teenkung.devSkills.service.ProfileService;
import com.Teenkung.devSkills.service.RewardService;
import com.Teenkung.devSkills.service.SourceService;
import com.Teenkung.devSkills.service.TraitService;
import com.Teenkung.devSkills.service.XpService;
import com.Teenkung.devSkills.service.XpBoosterService;
import com.Teenkung.devSkills.storage.StorageProvider;
import com.Teenkung.devSkills.util.PlacedBlockTracker;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class DevSkills extends JavaPlugin {

    private ConfigManager configManager;
    private MessageManager messageManager;
    private SoundManager soundManager;
    private StorageProvider storageProvider;
    private ProfileService profileService;
    private SourceService sourceService;
    private XpBoosterService boosterService;
    private CompletableFuture<Void> boosterReady = CompletableFuture.completedFuture(null);
    private LevelerService levelerService;
    private TraitService traitService;
    private RewardService rewardService;
    private ManaAbilityService manaAbilityService;
    private PassiveAbilityService passiveAbilityService;
    private MythicLibStatBridge mythicLibStatBridge;
    private MMOCoreManaProvider manaProvider;
    private FloodgateBridge floodgateBridge;
    private CumulusFormBridge cumulusFormBridge;
    private MenuManager menuManager;
    private PlaceholderApiBridge placeholderApiBridge;
    private HudService hudService;
    private XpService xpService;
    private PlacedBlockTracker placedBlockTracker;
    private DevSkillApiImpl api;
    private BukkitTask boosterPruneTask;
    private final AtomicBoolean reloadInProgress = new AtomicBoolean();
    private CompletableFuture<Void> startupFuture = CompletableFuture.completedFuture(null);
    private volatile CompletableFuture<Void> runtimeAvailability = startupFuture;
    private volatile boolean stopping;

    @Override
    public void onEnable() {
        if (Bukkit.getPluginManager().getPlugin("MythicLib") == null
                || !Bukkit.getPluginManager().isPluginEnabled("MythicLib")) {
            getLogger().severe("An enabled, compatible MythicLib is required. DevSkills is disabling.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        if (Bukkit.getPluginManager().getPlugin("MMOCore") == null
                || !Bukkit.getPluginManager().isPluginEnabled("MMOCore")) {
            getLogger().severe("An enabled, compatible MMOCore is required. DevSkills is disabling.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        try {
            manaProvider = new MMOCoreManaProvider(this);
        } catch (RuntimeException exception) {
            getLogger().severe("Unable to initialize the MMOCore mana provider: " + exception.getMessage());
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        ConfigManager initialConfig = new ConfigManager(this);
        initialConfig.reload();
        configManager = initialConfig;
        StorageProvider initialStorage = initialConfig.createStorageProvider();
        XpBoosterService initialBoosters = new XpBoosterService(initialStorage, initialConfig.boosterConfig().maxEffectiveMultiplier());
        getLogger().info("Initializing DevSkills storage asynchronously.");
        startupFuture = CompletableFuture.runAsync(initialStorage::initialize)
                .thenCompose(ignored -> initialBoosters.start())
                .thenCompose(ignored -> {
                    if (stopping) {
                        return CompletableFuture.failedFuture(new IllegalStateException("DevSkills stopped during startup"));
                    }
                    return runOnMain(() -> {
                        if (stopping || !isEnabled()) {
                            throw new IllegalStateException("DevSkills stopped during startup");
                        }
                        boosterReady = CompletableFuture.completedFuture(null);
                        installRuntime(initialConfig, initialStorage, initialBoosters, false);
                        registerCommands();
                        registerListeners();
                        getLogger().info("DevSkills enabled.");
                    });
                });
        runtimeAvailability = startupFuture;
        api = new DevSkillApiImpl(this);
        DevSkillsProvider.set(api);
        Bukkit.getServicesManager().register(DevSkillAPI.class, api, this, ServicePriority.Normal);
        startupFuture.whenComplete((ignored, failure) -> {
            if (failure == null) {
                return;
            }
            try {
                initialBoosters.shutdown();
            } catch (RuntimeException shutdownFailure) {
                getLogger().warning("Unable to stop failed startup booster runtime: " + shutdownFailure.getMessage());
            }
            initialStorage.close();
            if (!stopping) {
                getLogger().severe("DevSkills startup failed: " + rootMessage(failure));
                Bukkit.getScheduler().runTask(this, () -> Bukkit.getPluginManager().disablePlugin(this));
            }
        });
    }

    @Override
    public void onDisable() {
        stopping = true;
        stopRuntime();
        if (api != null) {
            Bukkit.getServicesManager().unregister(DevSkillAPI.class, api);
            DevSkillsProvider.clear(api);
        }
        getLogger().info("DevSkills disabled.");
    }

    public ConfigManager configManager() {
        return configManager;
    }

    public ProfileService profileService() {
        return profileService;
    }

    public XpService xpService() {
        return xpService;
    }

    public SourceService sourceService() {
        return sourceService;
    }

    public XpBoosterService boosterService() {
        return boosterService;
    }

    public void loadProfile(Player player) {
        CompletableFuture<Void> ready = boosterReady;
        ready.whenComplete((ignored, failure) -> Bukkit.getScheduler().runTask(this, () -> {
            if (!isEnabled() || !player.isOnline()) {
                return;
            }
            if (failure != null) {
                getLogger().severe("Unable to initialize EXP boosters: " + failure.getMessage());
                Bukkit.getPluginManager().disablePlugin(this);
                return;
            }
            boosterService.recordKnownPlayer(player.getUniqueId(), player.getName()).exceptionally(identityFailure -> {
                getLogger().warning("Unable to record DevSkills identity for " + player.getName() + ": " + rootMessage(identityFailure));
                return null;
            });
            profileService.load(player);
        }));
    }

    public LevelerService levelerService() {
        return levelerService;
    }

    public TraitService traitService() {
        return traitService;
    }

    public MenuManager menuManager() {
        return menuManager;
    }

    public HudService hudService() {
        return hudService;
    }

    public CompletableFuture<Void> runtimeReady() {
        return runtimeAvailability;
    }

    public PlacedBlockTracker placedBlockTracker() {
        return placedBlockTracker;
    }

    public MythicLibStatBridge mythicLibStatBridge() {
        return mythicLibStatBridge;
    }

    public MMOCoreManaProvider manaProvider() {
        return manaProvider;
    }

    public ManaAbilityService manaAbilityService() {
        return manaAbilityService;
    }

    public PassiveAbilityService passiveAbilityService() {
        return passiveAbilityService;
    }

    public CompletableFuture<Void> reloadRuntime() {
        if (!reloadInProgress.compareAndSet(false, true)) {
            return CompletableFuture.failedFuture(new IllegalStateException("A DevSkills reload is already in progress"));
        }
        CompletableFuture<Void> availabilityGate = new CompletableFuture<>();
        runtimeAvailability = availabilityGate;

        ConfigManager replacementConfig;
        StorageProvider replacementStorage;
        XpBoosterService replacementBoosters;
        XpBoosterService previousBoosters = boosterService;
        try {
            replacementConfig = new ConfigManager(this);
            replacementConfig.reload();
            replacementStorage = replacementConfig.createStorageProvider();
            replacementBoosters = new XpBoosterService(
                    replacementStorage,
                    replacementConfig.boosterConfig().maxEffectiveMultiplier()
            );
        } catch (RuntimeException exception) {
            reloadInProgress.set(false);
            availabilityGate.complete(null);
            return CompletableFuture.failedFuture(exception);
        }

        CompletableFuture<Void> result = CompletableFuture.runAsync(replacementStorage::initialize)
                .thenCompose(ignored -> previousBoosters.pauseMutationsAndDrain())
                .thenCompose(ignored -> replacementBoosters.start())
                .thenCompose(ignored -> runOnMain(() -> {
                    if (!isEnabled()) {
                        throw new IllegalStateException("DevSkills disabled while reload was being prepared");
                    }
                    installRuntime(replacementConfig, replacementStorage, replacementBoosters, true);
                }));
        return result.whenComplete((ignored, failure) -> {
            reloadInProgress.set(false);
            if (failure != null) {
                previousBoosters.resumeMutations();
                try {
                    replacementBoosters.shutdown();
                } catch (RuntimeException shutdownFailure) {
                    getLogger().warning("Unable to stop failed replacement booster runtime: " + shutdownFailure.getMessage());
                }
                replacementStorage.close();
            }
            availabilityGate.complete(null);
        });
    }

    private void installRuntime(ConfigManager nextConfig, StorageProvider nextStorage, XpBoosterService nextBoosters, boolean replacement) {
        MessageManager nextMessages = nextConfig.messageManager();
        SoundManager nextSounds = new SoundManager(this, nextConfig);
        LevelerService nextLeveler = new LevelerService(nextConfig.skills());
        SourceService nextSources = new SourceService(nextConfig.sources());
        TraitService nextTraits = new TraitService(nextConfig.traits(), nextConfig.skills(), nextLeveler);
        MythicLibStatBridge nextMythicLib = new MythicLibStatBridge(this, nextConfig);
        ProfileService nextProfiles = new ProfileService(this, nextStorage, nextTraits, nextMythicLib);
        RewardService nextRewards = new RewardService(this, nextConfig, nextMessages, nextSounds);
        HudConfig nextHudConfig = HudConfig.load(this);
        UiConfig nextUiConfig = UiConfig.load(this);
        PlaceholderApiBridge nextPlaceholderApi = new PlaceholderApiBridge(
                this,
                new DevSkillsPlaceholderResolver(
                        nextConfig,
                        nextProfiles,
                        nextLeveler,
                        nextTraits,
                        nextBoosters::activeBoosters,
                        nextBoosters::effectiveMultiplier
                )
        );
        ActionBarBridge nextActionBars = ActionBarBridge.direct();
        if (Bukkit.getPluginManager().getPlugin("ProtocolLib") != null) {
            try {
                nextActionBars = new ProtocolLibActionBarBridge(this);
            } catch (LinkageError failure) {
                getLogger().warning("ProtocolLib is installed but incompatible; using cooperative action-bar arbitration: " + failure.getMessage());
            }
        }
        HudService nextHud = new HudService(
                this,
                nextHudConfig,
                nextProfiles,
                player -> nextMythicLib.stat(player, "DAMAGE_REDUCTION"),
                nextPlaceholderApi,
                nextActionBars,
                nextBoosters::activeBoosters,
                nextBoosters::effectiveMultiplier
        );
        FloodgateBridge nextFloodgate = new FloodgateBridge(this);
        CumulusFormBridge nextCumulus = null;
        if (nextFloodgate.available()) {
            try {
                nextCumulus = new CumulusFormBridge(
                        this,
                        nextFloodgate,
                        nextConfig,
                        nextLeveler,
                        nextTraits,
                        nextUiConfig,
                        nextPlaceholderApi
                );
            } catch (LinkageError failure) {
                getLogger().warning("Floodgate is installed but Cumulus forms are unavailable; using Java inventory fallback: " + failure.getMessage());
            }
        }
        MenuManager nextMenus = new MenuManager(
                this,
                nextConfig,
                nextMessages,
                nextSounds,
                nextLeveler,
                nextTraits,
                nextFloodgate,
                nextCumulus,
                nextUiConfig,
                nextPlaceholderApi,
                new PaperDialogBridge(this)
        );
        XpService nextXp = new XpService(
                this,
                nextConfig,
                nextProfiles,
                nextSources,
                nextBoosters,
                nextLeveler,
                nextTraits,
                nextRewards,
                nextMythicLib,
                nextMessages,
                nextSounds,
                nextHud
        );
        ManaAbilityService nextManaAbilities = new ManaAbilityService(this);
        PassiveAbilityService nextPassiveAbilities = new PassiveAbilityService(this);
        PlacedBlockTracker nextPlacedBlocks = new PlacedBlockTracker(this, nextConfig.settings().placedBlockCapPerChunk());

        for (String issue : nextSources.auditIssues()) {
            getLogger().warning("EXP source audit: " + issue);
        }

        BukkitTask nextPruneTask;
        try {
            Bukkit.getPluginManager().registerEvents(nextMenus, this);
            nextProfiles.start(nextConfig.settings().flushIntervalSeconds());
            nextHud.start();
            nextPruneTask = createBoosterPruneTask(nextConfig, nextBoosters);
        } catch (RuntimeException | LinkageError failure) {
            HandlerList.unregisterAll(nextMenus);
            nextHud.close();
            nextProfiles.shutdown();
            throw failure;
        }

        if (replacement) {
            stopRuntime();
        }

        configManager = nextConfig;
        messageManager = nextMessages;
        soundManager = nextSounds;
        storageProvider = nextStorage;
        levelerService = nextLeveler;
        sourceService = nextSources;
        boosterService = nextBoosters;
        boosterReady = nextBoosters.start();
        traitService = nextTraits;
        mythicLibStatBridge = nextMythicLib;
        profileService = nextProfiles;
        rewardService = nextRewards;
        manaAbilityService = nextManaAbilities;
        passiveAbilityService = nextPassiveAbilities;
        placeholderApiBridge = nextPlaceholderApi;
        hudService = nextHud;
        floodgateBridge = nextFloodgate;
        cumulusFormBridge = nextCumulus;
        menuManager = nextMenus;
        xpService = nextXp;
        placedBlockTracker = nextPlacedBlocks;
        boosterPruneTask = nextPruneTask;

        placeholderApiBridge.registerExpansion();
        for (Player player : Bukkit.getOnlinePlayers()) {
            loadProfile(player);
        }
    }

    private void stopRuntime() {
        if (boosterPruneTask != null) {
            boosterPruneTask.cancel();
            boosterPruneTask = null;
        }
        if (hudService != null) {
            try {
                hudService.close();
            } catch (RuntimeException failure) {
                getLogger().warning("Unable to stop the DevSkills HUD cleanly: " + failure.getMessage());
            }
            hudService = null;
        }
        if (placeholderApiBridge != null) {
            placeholderApiBridge.close();
            placeholderApiBridge = null;
        }
        if (menuManager != null) {
            HandlerList.unregisterAll(menuManager);
        }
        if (mythicLibStatBridge != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                mythicLibStatBridge.clear(player);
            }
        }
        if (manaAbilityService != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                manaAbilityService.clear(player);
            }
            manaAbilityService = null;
        }
        passiveAbilityService = null;
        if (profileService != null) {
            try {
                profileService.shutdown();
            } catch (RuntimeException failure) {
                getLogger().warning("Unable to stop profile persistence cleanly: " + failure.getMessage());
            }
            profileService = null;
        }
        if (boosterService != null) {
            try {
                boosterService.shutdown();
            } catch (RuntimeException failure) {
                getLogger().warning("Unable to stop booster persistence cleanly: " + failure.getMessage());
            }
            boosterService = null;
        }
        if (storageProvider != null) {
            try {
                storageProvider.close();
            } catch (RuntimeException failure) {
                getLogger().warning("Unable to close DevSkills storage cleanly: " + failure.getMessage());
            }
            storageProvider = null;
        }
    }

    private BukkitTask createBoosterPruneTask(ConfigManager runtimeConfig, XpBoosterService runtimeBoosters) {
        long interval = 20L * runtimeConfig.boosterConfig().cleanupIntervalSeconds();
        return Bukkit.getScheduler().runTaskTimer(this, () -> runtimeBoosters.pruneExpired()
                .exceptionally(failure -> {
                    getLogger().warning("Unable to prune expired EXP boosters: " + failure.getMessage());
                    return 0;
                }), interval, interval);
    }

    private CompletableFuture<Void> runOnMain(Runnable runnable) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(this, () -> {
            try {
                runnable.run();
                future.complete(null);
            } catch (Throwable failure) {
                future.completeExceptionally(failure);
            }
        });
        return future;
    }

    private String rootMessage(Throwable failure) {
        Throwable cause = failure;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return message == null ? cause.getClass().getSimpleName() : message;
    }

    private void registerCommands() {
        DevSkillCommand command = new DevSkillCommand(this);
        registerPluginCommand("devskill", command);
        registerPluginCommand("skill", command);
        registerPluginCommand("skills", command);
        forceCommandOwnership("skill");
        forceCommandOwnership("skills");
    }

    private void registerPluginCommand(String name, DevSkillCommand executor) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().warning("Command /" + name + " was not registered from plugin.yml.");
            return;
        }
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    private void forceCommandOwnership(String name) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            return;
        }
        try {
            Field commandMapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            Object commandMap = commandMapField.get(Bukkit.getServer());
            if (!(commandMap instanceof SimpleCommandMap simpleCommandMap)) {
                return;
            }
            Field knownCommandsField = SimpleCommandMap.class.getDeclaredField("knownCommands");
            knownCommandsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Command> knownCommands = (Map<String, Command>) knownCommandsField.get(simpleCommandMap);
            knownCommands.put(name.toLowerCase(), command);
        } catch (ReflectiveOperationException | SecurityException exception) {
            getLogger().warning("Unable to force ownership of /" + name + ": " + exception.getMessage());
        }
    }

    private void registerListeners() {
        Bukkit.getPluginManager().registerEvents(new PlayerLifecycleListener(this), this);
        Bukkit.getPluginManager().registerEvents(new BlockSkillListener(this), this);
        Bukkit.getPluginManager().registerEvents(new CombatSkillListener(this), this);
        Bukkit.getPluginManager().registerEvents(new SpecialSkillListener(this), this);
        Bukkit.getPluginManager().registerEvents(new ManaAbilityListener(this), this);
        Bukkit.getPluginManager().registerEvents(new PassiveAbilityListener(this), this);
    }
}
