package com.Teenkung.devSkills.integration;

import io.lumine.mythic.lib.api.player.MMOPlayerData;
import io.lumine.mythic.lib.api.stat.StatInstance;
import io.lumine.mythic.lib.api.stat.StatMap;
import io.lumine.mythic.lib.api.stat.modifier.StatModifier;
import io.lumine.mythic.lib.player.modifier.ModifierType;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.function.Predicate;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

final class MythicLibProbeTest {

    @Test
    void requiredSignaturesArePresent() throws ReflectiveOperationException {
        StatModifier modifier = new StatModifier("DevSkills:probe", "ATTACK_DAMAGE", 1.0D, ModifierType.FLAT);
        Assertions.assertEquals("ATTACK_DAMAGE", modifier.getStat());

        Method getByUuid = MMOPlayerData.class.getMethod("get", UUID.class);
        Method getStatMap = MMOPlayerData.class.getMethod("getStatMap");
        Method getInstance = StatMap.class.getMethod("getInstance", String.class);
        Method update = StatMap.class.getMethod("update", String.class);
        Method removeIf = StatInstance.class.getMethod("removeIf", Predicate.class);
        Method registerModifier = StatInstance.class.getMethod("registerModifier", StatModifier.class);
        Method instanceUpdate = StatInstance.class.getMethod("update");

        Assertions.assertEquals(MMOPlayerData.class, getByUuid.getReturnType());
        Assertions.assertEquals(StatMap.class, getStatMap.getReturnType());
        Assertions.assertEquals(StatInstance.class, getInstance.getReturnType());
        Assertions.assertEquals(Void.TYPE, update.getReturnType());
        Assertions.assertEquals(Void.TYPE, removeIf.getReturnType());
        Assertions.assertEquals(Void.TYPE, registerModifier.getReturnType());
        Assertions.assertEquals(Void.TYPE, instanceUpdate.getReturnType());
    }
}
