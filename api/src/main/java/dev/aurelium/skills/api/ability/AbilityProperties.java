package dev.aurelium.skills.api.ability;

import dev.aurelium.skills.api.skill.Skill;
import org.jetbrains.annotations.NotNull;

public interface AbilityProperties {

    @NotNull
    Ability getAbility();

    Skill getSkill();

}
