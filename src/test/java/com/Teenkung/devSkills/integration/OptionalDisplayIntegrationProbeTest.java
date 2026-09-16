package com.Teenkung.devSkills.integration;

import io.papermc.paper.dialog.Dialog;
import java.lang.reflect.Method;
import java.util.function.Consumer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

final class OptionalDisplayIntegrationProbeTest {

    @Test
    void paperDialogFactoryIsPresent() throws ReflectiveOperationException {
        Method method = Dialog.class.getMethod("create", Consumer.class);
        Assertions.assertEquals(Dialog.class, method.getReturnType());
    }
}
