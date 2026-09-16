package com.Teenkung.devSkills.integration;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class FloodgateBridge {

    private final JavaPlugin plugin;
    private final boolean available;

    public FloodgateBridge(JavaPlugin plugin) {
        this.plugin = plugin;
        this.available = Bukkit.getPluginManager().getPlugin("floodgate") != null;
    }

    public boolean available() {
        return available;
    }

    public boolean isBedrock(UUID uuid) {
        if (!available) {
            return false;
        }
        try {
            Object api = api();
            Method method = api.getClass().getMethod("isFloodgatePlayer", UUID.class);
            return Boolean.TRUE.equals(method.invoke(api, uuid));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            return false;
        }
    }

    public boolean sendForm(UUID uuid, Object form) {
        if (!available || form == null) {
            return false;
        }
        try {
            Object api = api();
            Method method = findSendFormMethod(api, form);
            if (method == null) {
                return false;
            }
            Object result = method.invoke(api, uuid, form);
            return result instanceof Boolean sent && sent;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            if (plugin.getConfig().getBoolean("settings.debug", false)) {
                plugin.getLogger().warning("Floodgate form send failed: " + exception.getMessage());
            }
            return false;
        }
    }

    private Object api() throws ReflectiveOperationException {
        Class<?> apiType = Class.forName("org.geysermc.floodgate.api.FloodgateApi", true, plugin.getClass().getClassLoader());
        return apiType.getMethod("getInstance").invoke(null);
    }

    private Method findSendFormMethod(Object api, Object form) {
        Method[] methods = api.getClass().getMethods();
        for (Method method : methods) {
            if (!"sendForm".equals(method.getName()) || method.getParameterCount() != 2) {
                continue;
            }
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (UUID.class.equals(parameterTypes[0]) && parameterTypes[1].isAssignableFrom(form.getClass())) {
                return method;
            }
        }
        return null;
    }
}
