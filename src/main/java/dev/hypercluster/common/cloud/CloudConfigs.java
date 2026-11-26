package dev.hypercluster.common.cloud;

/**
 * 0.4 closed-source cloud feature switches. Static (system-property backed) like
 * {@code DatacenterConfigs} — a plain field keeps GameTests deterministic (tests can flip the
 * field directly).
 *
 * <ul>
 * <li>{@link #legacyModelRetireDay} — the game day (since world creation,
 * {@code dayTime / 24000}) on which the legacy cloud model goes offline. Set
 * {@code -Dhypercluster.legacyRetireDay=3} to change the default.</li>
 * <li>{@link #leavingSoonDays} — how many game days before retirement a model enters the
 * {@code Leaving Soon} window (countdown shown, still usable).</li>
 * <li>{@link #snackPackBonus} is deliberately not here — it stays a 0.3 datacenter switch.</li>
 * </ul>
 */
public final class CloudConfigs {

    /**
     * Game day on which {@code hypercluster:legacy_llama} goes offline. The default keeps the
     * demo model alive for a while; an admin can tune it via system property.
     */
    public static int legacyModelRetireDay = Integer
            .parseInt(System.getProperty("hypercluster.legacyRetireDay", "3"));

    /** Days before retirement that a model shows the {@code Leaving Soon} countdown. */
    public static int leavingSoonDays = Integer
            .parseInt(System.getProperty("hypercluster.leavingSoonDays", "1"));

    private CloudConfigs() {}
}
