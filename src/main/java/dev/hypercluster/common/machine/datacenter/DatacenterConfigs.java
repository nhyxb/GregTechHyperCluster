package dev.hypercluster.common.machine.datacenter;

/**
 * 0.3 datacenter feature switches. Static (system-property backed) for now — the goal asks for a
 * config switch for the green-snack easter egg, and a plain toggle keeps GameTests deterministic
 * (tests can flip the field directly).
 */
public final class DatacenterConfigs {

    /**
     * Green-snack-pack easter egg: a snack pack in the compute rack's slot trims the machine
     * room's total power draw by 5% (a small stability/maintenance-consumption bonus). Set
     * {@code -Dhypercluster.snackBonus=false} to disable.
     */
    public static boolean snackPackBonus = Boolean.parseBoolean(System.getProperty("hypercluster.snackBonus", "true"));

    private DatacenterConfigs() {}
}
