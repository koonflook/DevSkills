package com.Teenkung.devSkills.service;

import com.Teenkung.devSkills.domain.ability.ManaAbilityAction;
import com.Teenkung.devSkills.domain.ability.ManaAbilityConfig;
import com.Teenkung.devSkills.domain.ability.PassiveAbilityAction;
import com.Teenkung.devSkills.domain.ability.PassiveAbilityConfig;
import com.Teenkung.devSkills.domain.user.UserProfile;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class AbilityInfoService {

    private static final DecimalFormat NUMBER_FORMAT = new DecimalFormat("#,##0.#");

    private final Map<String, ManaAbilityConfig> manaAbilities;
    private final Map<String, PassiveAbilityConfig> passiveAbilities;
    private final LevelerService levelerService;

    public AbilityInfoService(Map<String, ManaAbilityConfig> manaAbilities, Map<String, PassiveAbilityConfig> passiveAbilities, LevelerService levelerService) {
        this.manaAbilities = manaAbilities;
        this.passiveAbilities = passiveAbilities;
        this.levelerService = levelerService;
    }

    public List<AbilityInfo> unlocked(UserProfile profile, String locale) {
        List<AbilityInfo> entries = new ArrayList<>();
        for (AbilityDisplayEntry entry : allAbilities(profile, "th")) {
            if (!entry.unlocked()) {
                continue;
            }
            entries.add(new AbilityInfo(
                    entry.skillId(),
                    entry.name(),
                    entry.type().displayName(true),
                    entry.description(),
                    entry.detail(),
                    entry.abilityLevel(),
                    entry.unlockLevel()
            ));
        }
        return List.copyOf(entries);
    }

    public List<AbilityDisplayEntry> abilitiesForSkill(String skillId, int skillLevel, String locale) {
        boolean thai = "th".equalsIgnoreCase(locale);
        List<AbilityDisplayEntry> entries = new ArrayList<>();
        for (PassiveAbilityConfig ability : passiveAbilities.values()) {
            if (ability.skillId().equals(skillId)) {
                entries.add(passiveEntry(ability, skillLevel, thai));
            }
        }
        for (ManaAbilityConfig ability : manaAbilities.values()) {
            if (ability.skillId().equals(skillId)) {
                entries.add(manaEntry(ability, skillLevel, thai));
            }
        }
        entries.sort(displayOrder());
        return List.copyOf(entries);
    }

    public List<AbilityDisplayEntry> allAbilities(UserProfile profile, String locale) {
        boolean thai = "th".equalsIgnoreCase(locale);
        List<AbilityDisplayEntry> entries = new ArrayList<>();
        for (PassiveAbilityConfig ability : passiveAbilities.values()) {
            entries.add(passiveEntry(ability, skillLevel(profile, ability.skillId()), thai));
        }
        for (ManaAbilityConfig ability : manaAbilities.values()) {
            entries.add(manaEntry(ability, skillLevel(profile, ability.skillId()), thai));
        }
        entries.sort(displayOrder());
        return List.copyOf(entries);
    }

    public List<String> unlockNames(String skillId, int level, String locale) {
        boolean thai = "th".equalsIgnoreCase(locale);
        List<String> names = new ArrayList<>();
        for (ManaAbilityConfig ability : manaAbilities.values()) {
            if (ability.skillId().equals(skillId) && ability.unlockLevel() == level) {
                names.add(manaName(ability.action(), thai));
            }
        }
        for (PassiveAbilityConfig ability : passiveAbilities.values()) {
            if (ability.skillId().equals(skillId) && ability.unlockLevel() == level) {
                names.add(ability.displayName(locale));
            }
        }
        return List.copyOf(names);
    }

    private Comparator<AbilityDisplayEntry> displayOrder() {
        return Comparator.comparing(AbilityDisplayEntry::skillId)
                .thenComparing(AbilityDisplayEntry::type)
                .thenComparingInt(AbilityDisplayEntry::unlockLevel)
                .thenComparing(AbilityDisplayEntry::name);
    }

    private AbilityDisplayEntry manaEntry(ManaAbilityConfig ability, int skillLevel, boolean thai) {
        int abilityLevel = ability.abilityLevel(skillLevel);
        return new AbilityDisplayEntry(
                ability.skillId(),
                ability.id(),
                AbilityType.MANA,
                manaName(ability.action(), thai),
                manaDescription(ability.action(), thai),
                manaDetail(ability, skillLevel, thai),
                ability.unlockLevel(),
                abilityLevel,
                abilityLevel > 0,
                false
        );
    }

    private AbilityDisplayEntry passiveEntry(PassiveAbilityConfig ability, int skillLevel, boolean thai) {
        int abilityLevel = ability.abilityLevel(skillLevel);
        return new AbilityDisplayEntry(
                ability.skillId(),
                ability.id(),
                AbilityType.PASSIVE,
                ability.displayName(thai ? "th" : "en"),
                passiveDescription(ability.action(), thai),
                passiveDetail(ability.action(), ability.value(skillLevel), thai),
                ability.unlockLevel(),
                abilityLevel,
                abilityLevel > 0,
                abilityLevel > 0 && ability.maxAbilityLevel() > 0 && abilityLevel >= ability.maxAbilityLevel()
        );
    }

    private int skillLevel(UserProfile profile, String skillId) {
        return levelerService.level(skillId, profile.xp(skillId));
    }

    private String manaName(ManaAbilityAction action, boolean thai) {
        return switch (action) {
            case REPLENISH -> thai ? "ฟื้นฟูพืชผล" : "Replenish";
            case TREECAPITATOR -> thai ? "โค่นต้นไม้" : "Treecapitator";
            case SPEED_MINE -> thai ? "เร่งความเร็วการขุด" : "Speed Mine";
            case SHARP_HOOK -> thai ? "เบ็ดพิฆาต" : "Sharp Hook";
            case TERRAFORM -> thai ? "ปรับสภาพพื้นดิน" : "Terraform";
            case CHARGED_SHOT -> thai ? "ศรอัดพลัง" : "Charged Shot";
            case ABSORPTION -> thai ? "เกราะป้องกัน" : "Damage Guard";
            case LIGHTNING_BLADE -> thai ? "คมดาบสายฟ้า" : "Lightning Blade";
        };
    }

    private String manaDescription(ManaAbilityAction action, boolean thai) {
        return switch (action) {
            case REPLENISH -> thai ? "เก็บเกี่ยวพืชโตเต็มวัยแล้วปลูกกลับทันที" : "Replants mature crops after harvesting.";
            case TREECAPITATOR -> thai ? "โค่นท่อนไม้ที่เชื่อมต่อกันเป็นชุด" : "Fells connected tree logs in a batch.";
            case SPEED_MINE -> thai ? "มอบ Haste ชั่วคราวเพื่อขุดได้เร็วขึ้น" : "Grants temporary Haste for faster mining.";
            case SHARP_HOOK -> thai ? "สร้างความเสียหายเพิ่มเมื่อเกี่ยวสิ่งมีชีวิต" : "Deals extra damage when the hook catches a target.";
            case TERRAFORM -> thai ? "ขุดบล็อกพื้นดินชนิดเดียวกันที่เชื่อมต่อกัน" : "Breaks connected terrain blocks of the same kind.";
            case CHARGED_SHOT -> thai ? "ยิงศรที่สร้างความเสียหายเพิ่มหนึ่งครั้ง" : "Empowers the next projectile for bonus damage.";
            case ABSORPTION -> thai ? "ลดความเสียหายที่ได้รับชั่วคราว" : "Temporarily reduces incoming damage.";
            case LIGHTNING_BLADE -> thai ? "เรียกเอฟเฟกต์สายฟ้าเมื่อโจมตีด้วยดาบ" : "Shows a lightning strike effect on sword hits.";
        };
    }

    private String manaDetail(ManaAbilityConfig ability, int skillLevel, boolean thai) {
        String value = NUMBER_FORMAT.format(ability.value(skillLevel));
        String mana = NUMBER_FORMAT.format(ability.cost(skillLevel));
        String cooldown = NUMBER_FORMAT.format(ability.cooldownTicks(skillLevel) / 20.0D);
        String duration = NUMBER_FORMAT.format(ability.durationTicks() / 20.0D);
        return thai
                ? "พลัง " + value + " | มานา " + mana + " | คูลดาวน์ " + cooldown + " วินาที | ระยะเวลา " + duration + " วินาที"
                : "Power " + value + " | Mana " + mana + " | Cooldown " + cooldown + "s | Duration " + duration + "s";
    }

    private String passiveDescription(PassiveAbilityAction action, boolean thai) {
        return switch (action) {
            case CROP_BONUS_DROP -> thai ? "มีโอกาสได้รับผลผลิตเพิ่ม 1 ชิ้น" : "Can grant one extra crop drop.";
            case CROP_XP -> thai ? "มีโอกาสได้รับ EXP วานิลลาเพิ่มจากการเก็บเกี่ยว" : "Can grant extra vanilla EXP while harvesting.";
            case CROP_GROWTH -> thai ? "ช่วยเร่งการเติบโตของพืชใกล้ตัว" : "Can advance nearby crop growth.";
            case WOOD_BONUS_DROP, FORAGING_BONUS_DROP -> thai ? "มีโอกาสได้รับไม้เพิ่ม 1 ชิ้น" : "Can grant one extra wood drop.";
            case AXE_DAMAGE -> thai ? "เพิ่มความเสียหายเมื่อถือขวาน" : "Slightly increases axe damage.";
            case LOW_HEALTH_GUARD -> thai ? "มีโอกาสลดความเสียหายเมื่อเลือดต่ำ" : "Can reduce damage while low on health.";
            case LEAF_BONUS_DROP -> thai ? "มีโอกาสได้รับของดรอปจากใบไม้เพิ่ม" : "Can grant one extra leaf drop.";
            case MINING_BONUS_DROP -> thai ? "มีโอกาสได้รับแร่หรือของขุดเพิ่ม 1 ชิ้น" : "Can grant one extra mining drop.";
            case MINING_XP -> thai ? "มีโอกาสได้รับ EXP วานิลลาเพิ่มจากการขุด" : "Can grant extra vanilla EXP while mining.";
            case PICKAXE_HASTE -> thai ? "มีโอกาสได้ Haste ชั่วคราวเมื่อขุด" : "Can grant temporary Haste while mining.";
            case MINING_HUNGER -> thai ? "มีโอกาสฟื้นค่าความหิวเล็กน้อยเมื่อขุด" : "Can restore a little hunger while mining.";
            case MINING_GUARD -> thai ? "มีโอกาสลดความเสียหายที่ได้รับ" : "Can reduce incoming damage.";
            case FISH_BONUS_DROP, FISH_TREASURE -> thai ? "มีโอกาสได้รับของที่ตกได้เพิ่ม 1 ชิ้น" : "Can grant one extra fishing drop.";
            case FISH_XP -> thai ? "มีโอกาสได้รับ EXP วานิลลาเพิ่มจากการตกปลา" : "Can grant extra vanilla EXP while fishing.";
            case FISH_PULL -> thai ? "มีโอกาสดึงเป้าหมายที่เกี่ยวเข้ามา" : "Can pull hooked targets closer.";
            case EXCAVATION_BONUS_DROP -> thai ? "มีโอกาสได้รับของขุดพื้นดินเพิ่ม 1 ชิ้น" : "Can grant one extra excavation drop.";
            case EXCAVATION_XP -> thai ? "มีโอกาสได้รับ EXP วานิลลาเพิ่มจากการขุดพื้นดิน" : "Can grant extra vanilla EXP while excavating.";
            case SHOVEL_HASTE -> thai ? "มีโอกาสได้ Haste ชั่วคราวเมื่อใช้พลั่ว" : "Can grant temporary Haste while excavating.";
            case BOW_RETURN -> thai ? "มีโอกาสเก็บลูกศรกลับเมื่อยิงโดน" : "Can return one arrow after a hit.";
            case BOW_DAMAGE -> thai ? "เพิ่มความเสียหายจากธนูเล็กน้อย" : "Slightly increases bow damage.";
            case BOW_STUN -> thai ? "มีโอกาสทำให้เป้าหมายช้าลง" : "Can briefly slow the target.";
            case DEFENSE_GUARD, MOB_GUARD -> thai ? "มีโอกาสลดความเสียหายที่ได้รับ" : "Can reduce incoming damage.";
            case DEBUFF_RESIST -> thai ? "มีโอกาสป้องกันสถานะผิดปกติ" : "Can block harmful potion effects.";
            case MELEE_DAMAGE -> thai ? "เพิ่มความเสียหายระยะประชิดเล็กน้อย" : "Slightly increases melee damage.";
            case PARRY -> thai ? "มีโอกาสลดความเสียหายจากการโจมตี" : "Can reduce incoming attack damage.";
            case FIRST_HIT -> thai ? "เพิ่มความเสียหายเมื่อโจมตีเป้าหมายที่ยังแข็งแรง" : "Increases damage against near-full-health targets.";
            case BLEED -> thai ? "มีโอกาสทำให้เป้าหมายเลือดไหล" : "Can inflict a short bleed.";
            case HUNGER_SAVE -> thai ? "มีโอกาสไม่เสียค่าความหิว" : "Can prevent hunger loss.";
            case SPRINT_SPEED -> thai ? "มีโอกาสได้ความเร็วชั่วคราวขณะวิ่ง" : "Can grant temporary Speed while sprinting.";
            case GOLDEN_HEAL -> thai ? "มีโอกาสได้ Regeneration เพิ่มจากแอปเปิลทอง" : "Can add Regeneration after eating a golden apple.";
            case HEAL_BONUS -> thai ? "มีโอกาสเพิ่มปริมาณการฟื้นเลือด" : "Can slightly increase healing received.";
            case CONSUME_HEAL -> thai ? "มีโอกาสฟื้นเลือดเล็กน้อยเมื่อกินอาหาร" : "Can restore a little health when eating.";
            case POTION_DRINK -> thai ? "มีโอกาสฟื้นเลือดเล็กน้อยเมื่อดื่มยา" : "Can restore a little health when drinking potions.";
            case SPLASH_POWER -> thai ? "มีโอกาสเพิ่มความแรงของยาปา" : "Can slightly increase splash potion intensity.";
            case LINGERING_POWER -> thai ? "มีโอกาสเพิ่มรัศมียาพื้นที่" : "Can slightly increase lingering potion radius.";
            case POTION_DURATION -> thai ? "มีโอกาสได้รับ Regeneration เพิ่มเมื่อดื่มยา" : "Can add Regeneration after drinking potions.";
            case BREW_BONUS -> thai ? "มีโอกาสได้รับยาที่ปรุงเพิ่ม 1 ขวด" : "Can grant one extra brewed potion.";
            case ENCHANT_DISCOUNT -> thai ? "มีโอกาสลดค่าเลเวลการร่ายมนตร์" : "Can reduce enchantment level cost.";
            case ENCHANT_XP -> thai ? "มีโอกาสได้รับ EXP วานิลลาเพิ่มจากการร่ายมนตร์" : "Can grant extra vanilla EXP while enchanting.";
            case ENCHANT_DAMAGE -> thai ? "เพิ่มความเสียหายของอาวุธที่มี Enchant" : "Slightly increases enchanted weapon damage.";
            case ENCHANT_REFUND -> thai ? "มีโอกาสคืนเลเวลหลังร่ายมนตร์" : "Can refund one level after enchanting.";
        };
    }

    private String passiveDetail(PassiveAbilityAction action, double value, boolean thai) {
        double chance = Math.min(20.0D, value);
        double percent = value / 10.0D;
        if (!thai) {
            return "Activation chance " + NUMBER_FORMAT.format(chance) + "%";
        }
        return switch (action) {
            case CROP_BONUS_DROP, WOOD_BONUS_DROP, FORAGING_BONUS_DROP, LEAF_BONUS_DROP, MINING_BONUS_DROP,
                    FISH_BONUS_DROP, FISH_TREASURE, EXCAVATION_BONUS_DROP, BREW_BONUS -> chanceDetail(chance, "ได้รับของเพิ่ม 1 ชิ้น");
            case CROP_XP, MINING_XP, FISH_XP, EXCAVATION_XP, ENCHANT_XP -> chanceDetail(chance, "ได้รับ EXP วานิลลา +1");
            case CROP_GROWTH -> chanceDetail(chance, "เร่งพืชใกล้ตัว 1 ระยะการเติบโต");
            case AXE_DAMAGE, BOW_DAMAGE, MELEE_DAMAGE, FIRST_HIT, ENCHANT_DAMAGE -> "โบนัสความเสียหาย " + NUMBER_FORMAT.format(percent) + "%";
            case LOW_HEALTH_GUARD, MINING_GUARD, DEFENSE_GUARD, MOB_GUARD, PARRY -> chanceDetail(chance,
                    "ลดความเสียหาย " + NUMBER_FORMAT.format(Math.min(2.0D, percent)) + "%");
            case PICKAXE_HASTE, SHOVEL_HASTE -> chanceDetail(chance, "ได้รับ Haste " + hasteLevel(value) + " เป็นเวลา 2 วินาที");
            case MINING_HUNGER -> chanceDetail(chance, "ฟื้นค่าความหิว +1");
            case FISH_PULL -> chanceDetail(chance, "ดึงเป้าหมายเข้าหาตัว");
            case BOW_RETURN -> chanceDetail(chance, "เก็บลูกศรกลับ +1");
            case BOW_STUN -> chanceDetail(chance, "ทำให้เป้าหมายติด Slowness I 1 วินาที");
            case DEBUFF_RESIST -> chanceDetail(chance, "ป้องกันสถานะผิดปกติ");
            case BLEED -> chanceDetail(chance, "ทำให้เป้าหมายติด Wither I 2 วินาที");
            case HUNGER_SAVE -> chanceDetail(chance, "ไม่เสียค่าความหิว");
            case SPRINT_SPEED -> chanceDetail(chance, "ได้รับ Speed I เป็นเวลา 2 วินาที");
            case GOLDEN_HEAL -> chanceDetail(chance, "ได้รับ Regeneration I เป็นเวลา 3 วินาที");
            case HEAL_BONUS -> chanceDetail(chance, "เพิ่มการฟื้นเลือด " + NUMBER_FORMAT.format(percent) + "%");
            case CONSUME_HEAL, POTION_DRINK -> chanceDetail(chance, "ฟื้นพลังชีวิต +0.5");
            case SPLASH_POWER -> chanceDetail(chance, "เพิ่มความแรงยาปา +0.05");
            case LINGERING_POWER -> chanceDetail(chance, "เพิ่มรัศมียาพื้นที่ +0.25");
            case POTION_DURATION -> chanceDetail(chance, "ได้รับ Regeneration I เป็นเวลา 2 วินาที");
            case ENCHANT_DISCOUNT -> chanceDetail(chance, "ลดค่าเลเวลร่ายมนตร์ " + enchantDiscount(value));
            case ENCHANT_REFUND -> chanceDetail(chance, "คืนเลเวลร่ายมนตร์ +1");
        };
    }

    private String chanceDetail(double chance, String effect) {
        return "โอกาสทำงาน " + NUMBER_FORMAT.format(chance) + "% | " + effect;
    }

    private String hasteLevel(double value) {
        int level = Math.clamp((int) Math.floor(value / 10.0D), 1, 2);
        return level == 1 ? "I" : "II";
    }

    private String enchantDiscount(double value) {
        return NUMBER_FORMAT.format(Math.max(1, (int) Math.floor(value / 10.0D)));
    }

    public enum AbilityType {
        PASSIVE,
        MANA;

        public String displayName(boolean thai) {
            if (thai) {
                return this == PASSIVE ? "สกิลติดตัว" : "สกิลมานา";
            }
            return this == PASSIVE ? "Passive Ability" : "Mana Ability";
        }
    }

    public record AbilityDisplayEntry(
            String skillId,
            String abilityId,
            AbilityType type,
            String name,
            String description,
            String detail,
            int unlockLevel,
            int abilityLevel,
            boolean unlocked,
            boolean maxed
    ) {
    }

    public record AbilityInfo(
            String skillId,
            String name,
            String type,
            String description,
            String detail,
            int abilityLevel,
            int unlockLevel
    ) {
    }
}
