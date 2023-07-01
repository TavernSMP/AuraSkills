package dev.aurelium.auraskills.common.trait;

import dev.aurelium.auraskills.api.trait.Trait;
import dev.aurelium.auraskills.api.trait.TraitProvider;
import dev.aurelium.auraskills.api.trait.Traits;
import dev.aurelium.auraskills.common.AuraSkillsPlugin;
import dev.aurelium.auraskills.common.registry.Registry;

public class TraitRegistry extends Registry<Trait> {

    public TraitRegistry(AuraSkillsPlugin plugin) {
        super(plugin, Trait.class);
    }

    @Override
    public void registerDefaults() {
        for (Trait trait : Traits.values()) {
            injectProvider(trait, TraitProvider.class, plugin.getTraitManager());
            this.register(trait.getId(), trait);
        }
    }
}
