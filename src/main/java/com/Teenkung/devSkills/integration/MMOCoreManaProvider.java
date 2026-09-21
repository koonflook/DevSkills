package com.Teenkung.devSkills.integration;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class MMOCoreManaProvider implements ManaProvider {

    private static final String PLAYER_DATA_CLASS = "net.Indyuce.mmocore.api.player.PlayerData";

    private final JavaPlugin plugin;
    private final Method playerDataGet;
    private final Method getMana;
    private final Method setMana;

    public MMOCoreManaProvider(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        Plugin mmocore = Bukkit.getPluginManager().getPlugin("MMOCore");
        if (mmocore == null) {
            throw new IllegalStateException("MMOCore is not installed");
        }
        try {
            Class<?> playerData = Class.forName(PLAYER_DATA_CLASS, true, mmocore.getClass().getClassLoader());
            playerDataGet = findPlayerDataGetter(playerData);
            getMana = playerData.getMethod("getMana");
            setMana = findNumericSetter(playerData, "setMana");
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("MMOCore PlayerData mana API is unavailable", exception);
        }
    }

    @Override
    public double mana(Player player) {
        Objects.requireNonNull(player, "player");
        Object data = playerData(player);
        if (data == null) {
            return 0.0D;
        }
        try {
            return number(getMana.invoke(data), "getMana");
        } catch (IllegalAccessException | InvocationTargetException exception) {
            throw failure("read mana", exception);
        }
    }

    @Override
    public boolean consume(Player player, double amount) {
        requireMainThread();
        if (!validAmount(amount)) {
            return false;
        }
        double current = mana(player);
        if (!Double.isFinite(current) || current < amount) {
            return false;
        }
        set(player, current - amount);
        return true;
    }

    @Override
    public void give(Player player, double amount) {
        requireMainThread();
        if (!validAmount(amount)) {
            return;
        }
        set(player, mana(player) + amount);
    }

    private Object playerData(Player player) {
        try {
            return playerDataGet.invoke(null, player);
        } catch (IllegalAccessException | InvocationTargetException exception) {
            throw failure("resolve PlayerData", exception);
        }
    }

    private void set(Player player, double value) {
        Object data = playerData(player);
        if (data == null) {
            return;
        }
        try {
            setMana.invoke(data, value);
        } catch (IllegalAccessException | InvocationTargetException exception) {
            throw failure("write mana", exception);
        }
    }

    private void requireMainThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("MMOCore mana must be changed on the server thread");
        }
    }

    private boolean validAmount(double amount) {
        return Double.isFinite(amount) && amount > 0.0D;
    }

    private double number(Object value, String operation) {
        if (!(value instanceof Number number)) {
            throw new IllegalStateException("MMOCore " + operation + " returned a non-numeric value");
        }
        return number.doubleValue();
    }

    private Method findPlayerDataGetter(Class<?> playerData) {
        for (Method method : playerData.getMethods()) {
            if (!method.getName().equals("get")
                    || !Modifier.isStatic(method.getModifiers())
                    || method.getParameterCount() != 1
                    || !method.getParameterTypes()[0].isAssignableFrom(Player.class)) {
                continue;
            }
            return method;
        }
        throw new IllegalStateException("MMOCore PlayerData.get(Player) API is unavailable");
    }

    private Method findNumericSetter(Class<?> playerData, String name) throws NoSuchMethodException {
        for (Method method : playerData.getMethods()) {
            if (method.getName().equals(name)
                    && method.getParameterCount() == 1
                    && (method.getParameterTypes()[0] == double.class
                    || method.getParameterTypes()[0] == Double.class)) {
                return method;
            }
        }
        throw new NoSuchMethodException(name + "(double)");
    }

    private IllegalStateException failure(String operation, Exception exception) {
        Throwable cause = exception instanceof InvocationTargetException invocation
                && invocation.getCause() != null ? invocation.getCause() : exception;
        plugin.getLogger().warning("MMOCore mana provider failed to " + operation + ": " + cause.getMessage());
        return new IllegalStateException("MMOCore mana provider failed to " + operation, cause);
    }
}
