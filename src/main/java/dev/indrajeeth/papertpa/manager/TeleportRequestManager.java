package dev.indrajeeth.papertpa.manager;

import dev.indrajeeth.papertpa.PaperTpa;
import dev.indrajeeth.papertpa.model.RatingSession;
import dev.indrajeeth.papertpa.model.TPARequest;
import dev.indrajeeth.papertpa.util.MessageUtil;
import dev.indrajeeth.papertpa.util.SoundUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class TeleportRequestManager {

    private final PaperTpa plugin;
    private final DatabaseManager database;

    private final Map<UUID, TPARequest> activeRequests = new ConcurrentHashMap<>();
    private final Map<UUID, PendingTeleport> pendingTeleports = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> requesterToTarget = new ConcurrentHashMap<>();
    private final Map<UUID, Long> immunePlayers = new ConcurrentHashMap<>();
    /** Guards against scheduling duplicate rating prompts for the same player. */
    private final Set<UUID> pendingRatingScheduled = ConcurrentHashMap.newKeySet();

    /** Extra grace period to retain expired cooldown entries before purging. */
    private static final long COOLDOWN_GRACE_PERIOD_MS = 60_000L;

        this.plugin   = plugin;
        this.database = database;
    }

    /**
     * Must be called on the main thread so that {@link Player#getLocation()} is safe.
     */
    public CompletableFuture<RequestResult> sendRequest(Player requester, Player target) {
        UUID requesterId = requester.getUniqueId();
        UUID targetId    = target.getUniqueId();

        Location requesterLoc = requester.getLocation().clone();

        TPARequest existing = activeRequests.get(requesterId);
        if (existing != null && existing.getTargetId().equals(targetId)) {
            return CompletableFuture.completedFuture(RequestResult.ALREADY_HAS_REQUEST);
        }

        if (!requester.hasPermission("papertpa.cooldown.bypass")) {
            long last = cooldowns.getOrDefault(requesterId, 0L);
            long cooldownMs = plugin.getConfigManager().getCooldown() * 1000L;
            if (System.currentTimeMillis() - last < cooldownMs) {
                return CompletableFuture.completedFuture(RequestResult.ON_COOLDOWN);
            }
        }

        return database.areRequestsEnabled(targetId).thenCompose(enabled -> {
            if (!enabled && !requester.hasPermission("papertpa.bypass")) {
                return CompletableFuture.completedFuture(RequestResult.REQUESTS_DISABLED);
            }

            long now = System.currentTimeMillis();
            TPARequest request = new TPARequest(requesterId, targetId, now, requesterLoc);
            activeRequests.put(requesterId, request);
            cooldowns.put(requesterId, now);
            database.updateLastRequestTime(requesterId, now);
            database.incrementStat(requesterId, "total_sent");
            database.incrementStat(targetId,    "total_received");

            return database.isAutoAcceptEnabled(targetId)
                .thenApply(autoAccept -> autoAccept ? RequestResult.AUTO_ACCEPTED : RequestResult.SUCCESS);
        });
    }

    public enum RequestResult {
        SUCCESS,
        AUTO_ACCEPTED,
        ALREADY_HAS_REQUEST,
        ON_COOLDOWN,
        REQUESTS_DISABLED
    }

    public CompletableFuture<Boolean> acceptRequest(Player accepter, UUID requesterId) {
        UUID accepterId = accepter.getUniqueId();
        TPARequest request = activeRequests.get(requesterId);
        if (request == null || !request.getTargetId().equals(accepterId)) {
            return CompletableFuture.completedFuture(false);
        }

        if (!activeRequests.remove(requesterId, request)) {
            return CompletableFuture.completedFuture(false);
        }

        Player requester = Bukkit.getPlayer(requesterId);
        if (requester == null || !requester.isOnline()) {
            return CompletableFuture.completedFuture(false);
        }

        database.incrementStat(accepterId, "total_accepted");

        requesterToTarget.put(requesterId, accepterId);

        final boolean captureLocation = plugin.getConfigManager().captureLocationOnAccept();
        final Location captured       = captureLocation ? accepter.getLocation().clone() : null;
        final UUID accepterIdFinal    = accepterId;

        Bukkit.getScheduler().runTask(plugin, () -> {
            Player req = Bukkit.getPlayer(requesterId);
            Player acc = Bukkit.getPlayer(accepterIdFinal);
            if (req == null || !req.isOnline()) {
                requesterToTarget.remove(requesterId); // leak fix: requester went offline
                return;
            }

            Location dest;
            if (captureLocation && captured != null
                    && captured.getWorld() != null
                    && Bukkit.getWorld(captured.getWorld().getUID()) != null) {
                dest = captured;
            } else if (acc != null && acc.isOnline()) {
                dest = acc.getLocation();
            } else {
                requesterToTarget.remove(requesterId); // leak fix: accepter went offline
                return;
            }
            teleportPlayer(req, dest);
        });

        return CompletableFuture.completedFuture(true);
    }

    public CompletableFuture<Boolean> denyRequest(Player denier, UUID requesterId) {
        TPARequest request = activeRequests.get(requesterId);
        if (request == null || !request.getTargetId().equals(denier.getUniqueId())) {
            return CompletableFuture.completedFuture(false);
        }
        if (!activeRequests.remove(requesterId, request)) {
            return CompletableFuture.completedFuture(false);
        }
        database.incrementStat(denier.getUniqueId(), "total_denied");
        return CompletableFuture.completedFuture(true);
    }

    public boolean cancelRequest(UUID requesterId) {
        return activeRequests.remove(requesterId) != null;
    }

    public void teleportPlayer(Player player, Location destination) {
        UUID playerId = player.getUniqueId();
        if (pendingTeleports.containsKey(playerId)) return;

        int delay = plugin.getConfigManager().getTeleportDelay();

        if (delay == 0 && plugin.getConfigManager().isTpIdleEnabled()
                && !player.hasPermission("papertpa.delay.bypass")) {
            int idleTime = plugin.getConfigManager().getTpIdleTime();
            if (idleTime > 0) delay = idleTime;
        }

        if (delay > 0 && !player.hasPermission("papertpa.delay.bypass")) {
            Location start = player.getLocation().clone();
            PendingTeleport pending = new PendingTeleport(player, destination, start, delay);
            pendingTeleports.put(playerId, pending);

            Map<String, String> ph = new HashMap<>();
            ph.put("time", String.valueOf(delay));
            MessageUtil.sendMessageWithPlaceholders(player,
                plugin.getConfigManager().getPrefix()
                + plugin.getConfigManager().getMessage("teleport.starting", ph));
            SoundUtil.play(player, "teleport-start");

            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                PendingTeleport tp = pendingTeleports.get(playerId);
                if (tp == null || !tp.isValid()) {
                    pendingTeleports.remove(playerId);
                    return;
                }
                tp.tick();
            }, 20L, 20L);
            pending.setTask(task);
        } else {
            performTeleport(player, destination);
        }
    }

    void performTeleport(Player player, Location destination) {
        if (destination == null) {
            requesterToTarget.remove(player.getUniqueId()); // leak fix
            return;
        }
        if (destination.getWorld() == null
                || Bukkit.getWorld(destination.getWorld().getUID()) == null) {
            requesterToTarget.remove(player.getUniqueId()); // leak fix
            return;
        }
        if (!player.isOnline()) {
            requesterToTarget.remove(player.getUniqueId()); // leak fix
            return;
        }
        if (!isSafeLocation(destination)) {
            requesterToTarget.remove(player.getUniqueId()); // leak fix
            MessageUtil.sendMessageWithPlaceholders(player,
                    plugin.getConfigManager().getPrefix()
                    + plugin.getConfigManager().getMessage("teleport.unsafe"));
            return;
        }

        player.teleportAsync(destination).thenAccept(success -> {
            if (success) {
                SoundUtil.play(player, "teleport-success");
                scheduleRatingPrompt(player.getUniqueId());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player p = Bukkit.getPlayer(player.getUniqueId());
                    if (p != null && p.isOnline()) applyImmunity(p);
                });
            } else {
                requesterToTarget.remove(player.getUniqueId()); // leak fix: async teleport failed
            }
        });
    }

    private static final Set<org.bukkit.Material> DANGEROUS_MATERIALS = Set.of(
            org.bukkit.Material.LAVA,
            org.bukkit.Material.FIRE,
            org.bukkit.Material.MAGMA_BLOCK,
            org.bukkit.Material.SOUL_FIRE,
            org.bukkit.Material.CAMPFIRE,
            org.bukkit.Material.SOUL_CAMPFIRE,
            org.bukkit.Material.SWEET_BERRY_BUSH,
            org.bukkit.Material.WITHER_ROSE
    );

    private boolean isSafeLocation(Location loc) {
        if (loc.getWorld() == null) return false;
        if (loc.getY() < loc.getWorld().getMinHeight()) return false;

        org.bukkit.block.Block feet   = loc.getBlock();
        org.bukkit.block.Block head   = loc.clone().add(0, 1, 0).getBlock();
        org.bukkit.block.Block ground = loc.clone().subtract(0, 1, 0).getBlock();

        boolean feetClear  = feet.getType().isAir()   || !feet.getType().isSolid();
        boolean headClear  = head.getType().isAir()   || !head.getType().isSolid();
        boolean hasGround  = ground.getType().isSolid();
        // Lava / fire / magma / campfire / etc. are deadly even when passable
        boolean notHazard  = !DANGEROUS_MATERIALS.contains(feet.getType())
                          && !DANGEROUS_MATERIALS.contains(head.getType())
                          && !DANGEROUS_MATERIALS.contains(ground.getType());

        return feetClear && headClear && hasGround && notHazard;
    }

    private void scheduleRatingPrompt(UUID playerId) {
        UUID targetId = requesterToTarget.remove(playerId);
        if (targetId == null) return;

        int delaySec = plugin.getConfigManager().getRatingDelay();
        if (delaySec <= 0) return;

        if (!pendingRatingScheduled.add(playerId)) return;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player rater = Bukkit.getPlayer(playerId);
            if (rater == null || !rater.isOnline()) {
                pendingRatingScheduled.remove(playerId);
                return;
            }

            database.isNotificationEnabled(playerId).thenAccept(notifEnabled -> {
                if (!notifEnabled) {
                    pendingRatingScheduled.remove(playerId);
                    return;
                }
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player r = Bukkit.getPlayer(playerId);
                    if (r == null || !r.isOnline()) {
                        pendingRatingScheduled.remove(playerId);
                        return;
                    }

                    String targetName = Optional.ofNullable(Bukkit.getPlayer(targetId))
                            .map(Player::getName)
                            .orElseGet(() -> Bukkit.getOfflinePlayer(targetId).getName() != null
                                    ? Bukkit.getOfflinePlayer(targetId).getName() : targetId.toString());

                    RatingSession session = new RatingSession(playerId, targetId, System.currentTimeMillis());
                    plugin.getGUIManager().addRatingSession(playerId, session);

                    String rateButtonText = plugin.getConfigManager().getMessage("rating.rate-button");
                    Component prompt = MessageUtil.toComponent(
                            plugin.getConfigManager().getPrefix()
                            + plugin.getConfigManager().getMessage("rating.prompt"));
                    Component clickable = Component.text()
                            .append(prompt)
                            .append(Component.text(" "))
                            .append(MessageUtil.toComponent(rateButtonText)
                                    .clickEvent(ClickEvent.runCommand("/tprate " + targetName))
                                    .hoverEvent(HoverEvent.showText(
                                            MessageUtil.toComponent(
                                                    plugin.getConfigManager().getMessage("rating.click-to-rate-hover")))))
                            .build();
                    pendingRatingScheduled.remove(playerId);
                    r.sendMessage(clickable);
                    SoundUtil.play(r, "rating-prompt");
                });
            });
        }, delaySec * 20L);
    }

    /**
     * Grants the player post-teleport immunity for the configured duration.
     * While immune, entity damage events are cancelled by {@link dev.indrajeeth.papertpa.listener.ImmunityListener}.
     */
    public void applyImmunity(Player player) {
        int immunitySeconds = plugin.getConfigManager().getTpImmunity();
        if (immunitySeconds <= 0) return;

        UUID playerId = player.getUniqueId();
        long expiry = System.currentTimeMillis() + (immunitySeconds * 1000L);
        immunePlayers.put(playerId, expiry);

        Map<String, String> ph = new HashMap<>();
        ph.put("time", String.valueOf(immunitySeconds));
        MessageUtil.sendMessageWithPlaceholders(player,
                plugin.getConfigManager().getPrefix()
                + plugin.getConfigManager().getMessage("immunity.applied", ph));

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Long stored = immunePlayers.get(playerId);
            if (stored != null && stored <= System.currentTimeMillis()) {
                immunePlayers.remove(playerId);
                Player p = Bukkit.getPlayer(playerId);
                if (p != null && p.isOnline()) {
                    MessageUtil.sendMessageWithPlaceholders(p,
                            plugin.getConfigManager().getPrefix()
                            + plugin.getConfigManager().getMessage("immunity.expired"));
                }
            }
        }, immunitySeconds * 20L);
    }

    /** Returns true if the player currently has post-teleport immunity. */
    public boolean isImmune(UUID playerId) {
        Long expiry = immunePlayers.get(playerId);
        if (expiry == null) return false;
        if (System.currentTimeMillis() > expiry) {
            immunePlayers.remove(playerId);
            return false;
        }
        return true;
    }

    /** Returns the TPARequest sent by requesterId, or null if none. */
    public TPARequest getRequest(UUID requesterId) {
        return activeRequests.get(requesterId);
    }

    public List<UUID> getPendingRequestsFor(UUID playerId) {
        List<UUID> list = new ArrayList<>();
        for (Map.Entry<UUID, TPARequest> e : activeRequests.entrySet()) {
            if (e.getValue().getTargetId().equals(playerId)) list.add(e.getKey());
        }
        return list;
    }

    public List<UUID> getSentRequestsBy(UUID playerId) {
        TPARequest req = activeRequests.get(playerId);
        return req != null ? List.of(req.getTargetId()) : Collections.emptyList();
    }

    public long getCooldown(UUID playerId) {
        return cooldowns.getOrDefault(playerId, 0L);
    }

    public Set<UUID> getPendingTeleports() {
        return new HashSet<>(pendingTeleports.keySet());
    }

    public void cleanupExpiredRequests() {
        long now       = System.currentTimeMillis();
        long timeoutMs = plugin.getConfigManager().getRequestTimeout() * 1000L;
        activeRequests.entrySet().removeIf(e -> {
            if (now - e.getValue().getRequestTime() > timeoutMs) {
                UUID requesterId = e.getKey();
                requesterToTarget.remove(requesterId); // leak fix
                Player requester = Bukkit.getPlayer(requesterId);
                if (requester != null && requester.isOnline()) {
                    MessageUtil.sendMessageWithPlaceholders(requester,
                            plugin.getConfigManager().getPrefix()
                            + plugin.getConfigManager().getMessage("requests.expired"));
                    SoundUtil.play(requester, "request-expired");
                }
                return true;
            }
            return false;
        });
        long cooldownMs = plugin.getConfigManager().getCooldown() * 1000L;
        cooldowns.entrySet().removeIf(e -> now - e.getValue() > cooldownMs + COOLDOWN_GRACE_PERIOD_MS);
    }

    public void cancelTeleport(UUID playerId) {
        PendingTeleport pt = pendingTeleports.remove(playerId);
        if (pt != null) pt.cancel();
        requesterToTarget.remove(playerId); // leak fix: warmup cancelled, no teleport = no rating
    }

    public void removePendingTeleport(UUID playerId) {
        pendingTeleports.remove(playerId);
    }

    void cleanupRequesterTarget(UUID playerId) {
        requesterToTarget.remove(playerId);
    }

    public static class PendingTeleport {
        private final Player player;
        private final Location destination;
        private final Location startLocation;
        private int remainingSeconds;
        private BukkitTask task;

        public PendingTeleport(Player player, Location destination,
                               Location startLocation, int delaySeconds) {
            this.player           = player;
            this.destination      = destination;
            this.startLocation    = startLocation;
            this.remainingSeconds = delaySeconds;
        }

        public void tick() {
            if (!player.isOnline()) { cancel(); return; }

            PaperTpa plugin = PaperTpa.getInstance();
            Location cur = player.getLocation();
            if (cur.getWorld() != startLocation.getWorld()
                    || cur.distance(startLocation) > 0.5) {
                MessageUtil.sendMessageWithPlaceholders(player,
                        plugin.getConfigManager().getPrefix()
                        + plugin.getConfigManager().getMessage("teleport.cancelled-moved"));
                cancel();
                return;
            }

            if (remainingSeconds > 0) {
                Map<String, String> ph = new HashMap<>();
                ph.put("time", String.valueOf(remainingSeconds));
                MessageUtil.sendMessageWithPlaceholders(player,
                        plugin.getConfigManager().getPrefix()
                        + plugin.getConfigManager().getMessage("teleport.countdown", ph));
                SoundUtil.play(player, "teleport-countdown");
            }

            remainingSeconds--;
            if (remainingSeconds <= 0) complete();
        }

        private void complete() {
            if (task != null) task.cancel();
            TeleportRequestManager mgr = PaperTpa.getInstance().getTeleportManager();
            mgr.removePendingTeleport(player.getUniqueId());
            mgr.performTeleport(player, destination);
        }

        public void cancel() {
            if (task != null) task.cancel();
            TeleportRequestManager mgr = PaperTpa.getInstance().getTeleportManager();
            mgr.removePendingTeleport(player.getUniqueId());
            mgr.cleanupRequesterTarget(player.getUniqueId());
            if (player.isOnline()) {
                PaperTpa plugin = PaperTpa.getInstance();
                MessageUtil.sendMessageWithPlaceholders(player,
                        plugin.getConfigManager().getPrefix()
                        + plugin.getConfigManager().getMessage("teleport.cancelled"));
                SoundUtil.play(player, "teleport-cancelled");
            }
        }

        public boolean isValid()             { return player.isOnline() && remainingSeconds > 0; }
        public void    setTask(BukkitTask t) { this.task = t; }
        public int     getRemainingSeconds() { return remainingSeconds; }
    }
}
