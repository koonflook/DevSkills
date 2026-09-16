package com.Teenkung.devSkills.integration;

import org.geysermc.cumulus.form.SimpleForm;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

final class CumulusProbeTest {

    @Test
    void simpleFormBuilderCompiles() {
        SimpleForm form = SimpleForm.builder()
                .title("DevSkills")
                .content("Skills")
                .button("Mining")
                .build();
        Assertions.assertEquals("DevSkills", form.title());
        Assertions.assertEquals(1, form.buttons().size());
    }
}
