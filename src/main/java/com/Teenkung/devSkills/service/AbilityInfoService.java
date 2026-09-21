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
        boolean thai = true;
        for (ManaAbilityConfig ability : manaAbilities.values()) {
            int skillLevel = skillLevel(profile, ability.skillId());
            int abilityLevel = ability.abilityLevel(skillLevel);
            if (abilityLevel <= 0) {
                continue;
            }
            entries.add(new AbilityInfo(
                    ability.skillId(),
                    manaName(ability.action(), thai),
                    thai ? "สกิลมานา" : "Mana Ability",
                    manaDescription(ability.action(), thai),
                    manaDetail(ability, skillLevel, thai),
                    abilityLevel,
                    ability.unlockLevel()
            ));
        }
        for (PassiveAbilityConfig ability : passiveAbilities.values()) {
            int skillLevel = skillLevel(profile, ability.skillId());
            int abilityLevel = ability.abilityLevel(skillLevel);
            if (abilityLevel <= 0) {
                continue;
            }
            entries.add(new AbilityInfo(
                    ability.skillId(),
                    ability.displayName("th"),
                    thai ? "สกิลติดตัว" : "Passive Ability",
                    passiveDescription(ability.action(), thai),
                    passiveDetail(ability.action(), ability.value(skillLevel), thai),
                    abilityLevel,
                    ability.unlockLevel()
            ));
        }
        entries.sort(Comparator.comparing(AbilityInfo::skillId).thenComparingInt(AbilityInfo::unlockLevel).thenComparing(AbilityInfo::name));
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
        return thai
                ? "พลัง " + value + " | มานา " + mana + " | คูลดาวน์ " + cooldown + " วินาที"
                : "Power " + value + " | Mana " + mana + " | Cooldown " + cooldown + "s";
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
        double percent = value / 10.0D;
        return switch (action) {
            case AXE_DAMAGE, BOW_DAMAGE, MELEE_DAMAGE, FIRST_HIT, ENCHANT_DAMAGE -> thai
                    ? "โบนัสความเสียหาย " + NUMBER_FORMAT.format(percent) + "%"
                    : "Damage bonus " + NUMBER_FORMAT.format(percent) + "%";
            case LOW_HEALTH_GUARD, MINING_GUARD, DEFENSE_GUARD, MOB_GUARD, PARRY, HEAL_BONUS -> thai
                    ? "เมื่อทำงาน มีผล " + NUMBER_FORMAT.format(percent) + "%"
                    : "Effect strength " + NUMBER_FORMAT.format(percent) + "%";
            default -> thai
                    ? "โอกาสทำงาน " + NUMBER_FORMAT.format(value) + "%"
                    : "Activation chance " + NUMBER_FORMAT.format(value) + "%";
        };
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
