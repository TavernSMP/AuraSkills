package dev.aurelium.auraskills.common.leaderboard;

import dev.aurelium.auraskills.api.skill.Skill;
import dev.aurelium.auraskills.common.AuraSkillsPlugin;
import dev.aurelium.auraskills.common.scheduler.TaskRunnable;
import dev.aurelium.auraskills.common.user.User;
import dev.aurelium.auraskills.common.user.UserState;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.data.DataType;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.PermissionNode;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class LeaderboardManager {

    private final AuraSkillsPlugin plugin;
    private final LeaderboardExclusion leaderboardExclusion;
    private final Map<Skill, List<SkillValue>> skillLeaderboards;

    private List<SkillValue> powerLeaderboard;
    private List<SkillValue> averageLeaderboard;
    private volatile boolean sorting = false;

    public LeaderboardManager(AuraSkillsPlugin plugin, LeaderboardExclusion leaderboardExclusion) {
        this.plugin = plugin;
        this.leaderboardExclusion = leaderboardExclusion;
        this.skillLeaderboards = new HashMap<>();
        this.powerLeaderboard = new ArrayList<>();
        this.averageLeaderboard = new ArrayList<>();
        // Load excluded players
        this.leaderboardExclusion.loadFromFile();
    }

    public void startLeaderboardUpdater() {
        plugin.getScheduler().timerAsync(new TaskRunnable() {
            @Override
            public void run() {
                updateLeaderboardsSync();
            }
        }, 5 * 60, 5 * 60, TimeUnit.SECONDS);
    }

    public void updateLeaderboards() {
        updateLeaderboardsSync();
    }

    private void updateLeaderboardsSync() {
        if (sorting) return;
        try {
            setSorting(true);
            // Initialize lists
            Map<Skill, List<SkillValue>> skillLeaderboards = new HashMap<>();
            for (Skill skill : plugin.getSkillRegistry().getValues()) {
                skillLeaderboards.put(skill, new ArrayList<>());
            }
            List<SkillValue> powerLeaderboard = new ArrayList<>();
            List<SkillValue> averageLeaderboard = new ArrayList<>();
            // Add players already in memory
            addLoadedPlayersToLeaderboards(skillLeaderboards, powerLeaderboard, averageLeaderboard);
            // Add offline players
            addOfflinePlayers(skillLeaderboards, powerLeaderboard, averageLeaderboard);
            // Sort leaderboards and set as current
            sortLeaderboards(skillLeaderboards, powerLeaderboard, averageLeaderboard);

            setSorting(false);
        } catch (Exception e) {
            plugin.logger().warn("Error updating leaderboards: " + e.getMessage());
            e.printStackTrace();
            setSorting(false);
        }
    }

    public List<SkillValue> getLeaderboard(Skill skill) {
        return skillLeaderboards.computeIfAbsent(skill, s -> new ArrayList<>());
    }

    public void setLeaderboard(Skill skill, List<SkillValue> leaderboard) {
        this.skillLeaderboards.put(skill, leaderboard);
    }

    public List<SkillValue> getLeaderboard(Skill skill, int page, int numPerPage) {
        List<SkillValue> leaderboard = skillLeaderboards.computeIfAbsent(skill, k -> new ArrayList<>());
        int from = (Math.max(page, 1) - 1) * numPerPage;
        int to = from + numPerPage;
        return leaderboard.subList(Math.min(from, leaderboard.size()), Math.min(to, leaderboard.size()));
    }

    @Nullable
    public SkillValue getSkillValue(Skill skill, int place) {
        List<SkillValue> values = getLeaderboard(skill, place, 1);
        if (!values.isEmpty()) {
            return values.get(0);
        }
        return null;
    }

    public List<SkillValue> getPowerLeaderboard() {
        return powerLeaderboard;
    }

    public List<SkillValue> getPowerLeaderboard(int page, int numPerPage) {
        int from = (Math.max(page, 1) - 1) * numPerPage;
        int to = from + numPerPage;
        return powerLeaderboard.subList(Math.min(from, powerLeaderboard.size()), Math.min(to, powerLeaderboard.size()));
    }

    public void setPowerLeaderboard(List<SkillValue> leaderboard) {
        this.powerLeaderboard = leaderboard;
    }

    public List<SkillValue> getAverageLeaderboard() {
        return averageLeaderboard;
    }

    public List<SkillValue> getAverageLeaderboard(int page, int numPerPage) {
        int from = (Math.max(page, 1) - 1) * numPerPage;
        int to = from + numPerPage;
        return averageLeaderboard.subList(Math.min(from, averageLeaderboard.size()), Math.min(to, averageLeaderboard.size()));
    }

    public void setAverageLeaderboard(List<SkillValue> leaderboard) {
        this.averageLeaderboard = leaderboard;
    }

    public int getSkillRank(Skill skill, UUID id) {
        List<SkillValue> leaderboard = skillLeaderboards.get(skill);
        if (leaderboard == null) return 0;

        for (int i = 0; i < leaderboard.size(); i++) {
            SkillValue skillValue = leaderboard.get(i);
            if (skillValue.id().equals(id)) {
                return i + 1;
            }
        }
        return 0;
    }

    public int getPowerRank(UUID id) {
        for (int i = 0; i < powerLeaderboard.size(); i++) {
            SkillValue skillValue = powerLeaderboard.get(i);
            if (skillValue.id().equals(id)) {
                return i + 1;
            }
        }
        return 0;
    }

    public int getAverageRank(UUID id) {
        for (int i = 0; i < averageLeaderboard.size(); i++) {
            SkillValue skillValue = averageLeaderboard.get(i);
            if (skillValue.id().equals(id)) {
                return i + 1;
            }
        }
        return 0;
    }

    public boolean isNotSorting() {
        return !sorting;
    }

    public void setSorting(boolean sorting) {
        this.sorting = sorting;
    }

    public LeaderboardExclusion getLeaderboardExclusion() {
        return leaderboardExclusion;
    }

    private void addLoadedPlayersToLeaderboards(Map<Skill, List<SkillValue>> skillLb, List<SkillValue> powerLb, List<SkillValue> averageLb) {
        for (User user : plugin.getUserManager().getUserMap().values()) {

            // aglerr: Check if the user has permission '*'
            if (LuckPermsProvider.get().getUserManager().isLoaded(user.getUuid())) {
                net.luckperms.api.model.user.User lpUser = LuckPermsProvider.get().getUserManager().getUser(user.getUuid());
                if (lpUser.getCachedData().getPermissionData().checkPermission("*").asBoolean()) {
                    continue;
                }
            } else {
                net.luckperms.api.model.user.User lpUser = LuckPermsProvider.get().getUserManager().loadUser(user.getUuid()).join();
                if (lpUser.getCachedData().getPermissionData().checkPermission("*").asBoolean()) {
                    continue;
                }
            }

            UUID id = user.getUuid();

            if (leaderboardExclusion.isExcludedPlayer(id)) {
                continue;
            }

            int powerLevel = 0;
            double powerXp = 0;
            int numEnabled = 0;
            for (Skill skill : plugin.getSkillManager().getSkillValues()) {
                int level = user.getSkillLevel(skill);
                double xp = user.getSkillXp(skill);
                // Add to lists
                SkillValue skillLevel = new SkillValue(id, level, xp);
                skillLb.get(skill).add(skillLevel);

                if (skill.isEnabled()) {
                    powerLevel += level;
                    powerXp += xp;
                    numEnabled++;
                }
            }

            // aglerr: Skip player if the power level is exactly 14400
            if (powerLevel == 14400) {
                continue;
            }
            // Add to power and average leaderboards
            powerLb.add(new SkillValue(id, powerLevel, powerXp));

            double averageLevel = (double) powerLevel / numEnabled;
            averageLb.add(new SkillValue(id, 0, averageLevel));
        }
    }

    private void addOfflinePlayers(Map<Skill, List<SkillValue>> skillLb, List<SkillValue> powerLb, List<SkillValue> averageLb) throws Exception {
        List<UserState> offlineStates = plugin.getStorageProvider().loadStates(true, true);
        for (UserState state : offlineStates) {
            if (leaderboardExclusion.isExcludedPlayer(state.uuid())) {
                continue;
            }

            int powerLevel = 0;
            double powerXp = 0.0;
            int numEnabled = 0;

            // aglerr: Check if the user has permission '*'
            if (this.hasAdminPermission(state.uuid())) {
                continue;
            }

            for (Skill skill : state.skillLevels().keySet()) {
                int level = state.skillLevels().get(skill);
                double xp = state.skillXp().get(skill);

                // Add to skill leaderboard
                SkillValue skillValue = new SkillValue(state.uuid(), level, xp);
                skillLb.computeIfAbsent(skill, k -> new ArrayList<>()).add(skillValue);

                if (skill.isEnabled()) {
                    powerLevel += level;
                    powerXp += xp;
                    numEnabled++;
                }
            }

            // aglerr: Skip player if the power level is exactly 14400
            if (powerLevel == 14400) {
                continue;
            }
            // Add to power and average leaderboards
            powerLb.add(new SkillValue(state.uuid(), powerLevel, powerXp));

            double averageLevel = (double) powerLevel / numEnabled;
            averageLb.add(new SkillValue(state.uuid(), 0, averageLevel));
        }
    }

    private boolean hasAdminPermission(UUID uuid) {
        // aglerr: Check if the user has permission '*'
        net.luckperms.api.model.user.User user = null;
        if (LuckPermsProvider.get().getUserManager().isLoaded(uuid)) {
            user = LuckPermsProvider.get().getUserManager().getUser(uuid);
        } else {
            user = LuckPermsProvider.get().getUserManager().loadUser(uuid).join();

        }
        return user.getCachedData().getPermissionData().checkPermission("*").asBoolean();
    }

    private void sortLeaderboards(Map<Skill, List<SkillValue>> skillLb, List<SkillValue> powerLb, List<SkillValue> averageLb) {
        LeaderboardSorter sorter = new LeaderboardSorter();
        for (Skill skill : plugin.getSkillManager().getSkillValues()) {
            skillLb.computeIfAbsent(skill, k -> new ArrayList<>()).sort(sorter);
        }
        powerLb.sort(sorter);
        AverageSorter averageSorter = new AverageSorter();
        averageLb.sort(averageSorter);

        // Add skill leaderboards to map
        for (Skill skill : plugin.getSkillManager().getSkillValues()) {
            setLeaderboard(skill, skillLb.get(skill));
        }
        setPowerLeaderboard(powerLb);
        setAverageLeaderboard(averageLb);
    }

}
