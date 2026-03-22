package dev.indrajeeth.tpshield.manager;

import dev.indrajeeth.tpshield.TpShield;
import dev.indrajeeth.tpshield.model.RatingSession;
import dev.indrajeeth.tpshield.model.TPARequest;
import dev.indrajeeth.tpshield.util.MessageUtil;
import dev.indrajeeth.tpshield.util.SoundUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class TeleportRequestManager {

    private final TpShield plugin;
    private final DatabaseManager database;

    private final Map<UUID, TPARequest> activeRequests = new ConcurrentHashMap<>();
    private final Map<UUID, PendingTeleport> pendingTeleports = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> requesterToTarget = new ConcurrentHashMap<>();
    private final Map<UUID, Long> immunePlayers = new ConcurrentHashMap<>();
    private final Set<UUID> pendingRatingScheduled = ConcurrentHashMap.newKeySet();

    /**
     * Holds requests that have been accepted by the target but are awaiting
     * confirmation from the sender before the teleport fires.
     * Key: requesterId (sender), Value: AcceptedRequest data (accepter + destination)
     */
    private final Map<UUID, AcceptedRequest> acceptedPending = new ConcurrentHashMap<>();

    /** Immutable snapshot of an accepted request awaiting sender confirmation. */
    private record AcceptedRequest(UUID accepterId, Location destination, long acceptTime) {}

    /** Extra grace period to retain expired cooldown entries before purging. */
    private static final long COOLDOWN_GRACE_PERIOD_MS = 60_000L;

    public TeleportRequestManager(TpShield plugin, DatabaseManager database) {
        this.plugin   = plugin;
        this.database = database;
    }

    /**
     * Must be called on the main thread so that {@link Player#getLocation()} is safe.
     */
    public CompletableFuture<RequestResult> sendRequest(Player requester, Player target) {
        UUID requesterId = requester.getUniqueId();
        UUID targetId    = target.getUniqueId();

        TPARequest existing = activeRequests.get(requesterId);
        if (existing != null && existing.getTargetId().equals(targetId)) {
            return CompletableFuture.completedFuture(RequestResult.ALREADY_HAS_REQUEST);
        }

        if (!requester.hasPermission("tpshield.cooldown.bypass")) {
            long last = cooldowns.getOrDefault(requesterId, 0L);
            long cooldownMs = plugin.getConfigManager().getCooldown() * 1000L;
            if (System.currentTimeMillis() - last < cooldownMs) {
                return CompletableFuture.completedFuture(RequestResult.ON_COOLDOWN);
            }
        }

        Location requesterLoc = requester.getLocation().clone();

        return database.areRequestsEnabled(targetId).thenCompose(enabled -> {
            if (!enabled && !requester.hasPermission("tpshield.bypass")) {
                return CompletableFuture.completedFuture(RequestResult.REQUESTS_DISABLED);
            }

            long now = System.currentTimeMillis();
            TPARequest request = new TPARequest(requesterId, targetId, now, requesterLoc, false);
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

    /** Result returned by {@link #acceptRequest}. */
    public enum AcceptResult {
        /** Normal TPA accept — requester still needs to confirm via GUI or /tpconfirm. */
        NORMAL,
        /** tpahere accept — target has been teleported to the requester immediately. */
        HERE,
        /** No matching pending request was found. */
        FAILED
    }

    /**
     * Sends a /tpahere request: requester asks the target to teleport to them.
     * Must be called on the main thread.
     */
    public CompletableFuture<RequestResult> sendHereRequest(Player requester, Player target) {
        UUID requesterId = requester.getUniqueId();
        UUID targetId    = target.getUniqueId();

        TPARequest existing = activeRequests.get(requesterId);
        if (existing != null && existing.getTargetId().equals(targetId)) {
            return CompletableFuture.completedFuture(RequestResult.ALREADY_HAS_REQUEST);
        }

        if (!requester.hasPermission("tpshield.cooldown.bypass")) {
            long last = cooldowns.getOrDefault(requesterId, 0L);
            long cooldownMs = plugin.getConfigManager().getCooldown() * 1000L;
            if (System.currentTimeMillis() - last < cooldownMs) {
                return CompletableFuture.completedFuture(RequestResult.ON_COOLDOWN);
            }
        }

        Location requesterLoc = requester.getLocation().clone();

        return database.areRequestsEnabled(targetId).thenCompose(enabled -> {
            if (!enabled && !requester.hasPermission("tpshield.bypass")) {
                return CompletableFuture.completedFuture(RequestResult.REQUESTS_DISABLED);
            }

            long now = System.currentTimeMillis();
            TPARequest request = new TPARequest(requesterId, targetId, now, requesterLoc, true);
            activeRequests.put(requesterId, request);
            cooldowns.put(requesterId, now);
            database.updateLastRequestTime(requesterId, now);
            database.incrementStat(requesterId, "total_sent");
            database.incrementStat(targetId,    "total_received");

            return database.isAutoAcceptEnabled(targetId)
                .thenApply(autoAccept -> autoAccept ? RequestResult.AUTO_ACCEPTED : RequestResult.SUCCESS);
        });
    }

    public CompletableFuture<AcceptResult> acceptRequest(Player accepter, UUID requesterId) {
        UUID accepterId = accepter.getUniqueId();
        TPARequest request = activeRequests.get(requesterId);
        if (request == null || !request.getTargetId().equals(accepterId)) {
            return CompletableFuture.completedFuture(AcceptResult.FAILED);
        }

        if (!activeRequests.remove(requesterId, request)) {
            return CompletableFuture.completedFuture(AcceptResult.FAILED);
        }

        Player requester = Bukkit.getPlayer(requesterId);
        if (requester == null) {
            return CompletableFuture.completedFuture(AcceptResult.FAILED);
        }

        database.incrementStat(accepterId, "total_accepted");

        if (request.isReverse()) {
            Location destination = requester.getLocation().clone();
            requesterToTarget.put(accepterId, requesterId);
            teleportPlayer(accepter, destination);
            return CompletableFuture.completedFuture(AcceptResult.HERE);
        }

        Location destination = accepter.getLocation().clone();
        acceptedPending.put(requesterId, new AcceptedRequest(accepterId, destination, System.currentTimeMillis()));
        return CompletableFuture.completedFuture(AcceptResult.NORMAL);
    }

    /**
     * Called when the original sender confirms they want to proceed with the teleport
     * after the target has accepted. Must be called on the main thread.
     *
     * @return true if the confirmation was valid and teleport was initiated, false otherwise
     */
    public boolean confirmTeleport(Player requester) {
        UUID requesterId = requester.getUniqueId();
        AcceptedRequest accepted = acceptedPending.remove(requesterId);
        if (accepted == null) return false;

        Location dest = accepted.destination();
        if (dest.getWorld() == null || Bukkit.getWorld(dest.getWorld().getUID()) == null) {
            Player accepter = Bukkit.getPlayer(accepted.accepterId());
            if (accepter == null) return false;
            dest = accepter.getLocation();
        }

        requesterToTarget.put(requesterId, accepted.accepterId());
        teleportPlayer(requester, dest);
        return true;
    }

    /**
     * Sends the sender a simple notification that their request was accepted,
     * plus a clickable "[View]" button to open the ConfirmGUI.
     * Must be called on the main thread.
     */
    public void sendRequesterAcceptConfirmation(Player requester, Player accepter) {
        ConfigManager cfg = plugin.getConfigManager();

        Component msg = MessageUtil.toComponent(
                cfg.getPrefix() + cfg.getMessage("requests.accepted-awaiting-confirm",
                        Map.of("player", accepter.getName())));
        Component viewButton = MessageUtil.toComponent(cfg.getMessage("ui.button.view"))
                .clickEvent(ClickEvent.runCommand("/tpaview"))
                .hoverEvent(HoverEvent.showText(
                        MessageUtil.toComponent(cfg.getMessage("ui.hover.view"))));

        requester.sendMessage(Component.text()
                .append(msg)
                .append(Component.text(" "))
                .append(viewButton)
                .build());
        SoundUtil.play(requester, "request-accepted");
    }

    /** Returns the accepter's UUID for a sender who has an accepted-pending request, or null. */
    public UUID getAcceptedRequestTarget(UUID requesterId) {
        AcceptedRequest accepted = acceptedPending.get(requesterId);
        return accepted != null ? accepted.accepterId() : null;
    }

    /** Returns the captured destination for a sender who has an accepted-pending request, or null. */
    public Location getAcceptedDestination(UUID requesterId) {
        AcceptedRequest accepted = acceptedPending.get(requesterId);
        return accepted != null ? accepted.destination() : null;
    }

    /**
     * Cancels an accepted-but-unconfirmed request (sender chose not to proceed).
     *
     * @return true if a pending accepted request was found and removed
     */
    public boolean cancelAcceptedRequest(UUID requesterId) {
        return acceptedPending.remove(requesterId) != null;
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

    /** Cancels the request from {@code requesterId} only if it was directed at {@code targetId}. */
    public boolean cancelRequest(UUID requesterId, UUID targetId) {
        TPARequest request = activeRequests.get(requesterId);
        if (request == null || !request.getTargetId().equals(targetId)) {
            return false;
        }
        return activeRequests.remove(requesterId, request);
    }

    public void teleportPlayer(Player player, Location destination) {
        UUID playerId = player.getUniqueId();
        if (pendingTeleports.containsKey(playerId)) return;

        ConfigManager cfg = plugin.getConfigManager();
        int delay = cfg.getTeleportDelay();

        if (delay == 0 && cfg.isTpIdleEnabled()
                && !player.hasPermission("tpshield.delay.bypass")) {
            int idleTime = cfg.getTpIdleTime();
            if (idleTime > 0) delay = idleTime;
        }

        if (delay > 0 && !player.hasPermission("tpshield.delay.bypass")) {
            Location start = player.getLocation().clone();
            PendingTeleport pending = new PendingTeleport(player, destination, start, delay, this);
            pendingTeleports.put(playerId, pending);

            MessageUtil.sendActionBar(player,
                cfg.getMessage("teleport.starting-bar", Map.of("time", String.valueOf(delay))));
            SoundUtil.play(player, "teleport-start");

            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                PendingTeleport tp = pendingTeleports.get(playerId);
                if (tp == null) return;

                if (!tp.isValid()) { tp.cancel(); return; }
                tp.tick();
            }, 20L, 20L);
            pending.setTask(task);
        } else {
            performTeleport(player, destination);
        }
    }

    void performTeleport(Player player, Location destination) {
        if (destination == null) {
            requesterToTarget.remove(player.getUniqueId());
            return;
        }
        if (destination.getWorld() == null
                || Bukkit.getWorld(destination.getWorld().getUID()) == null) {
            requesterToTarget.remove(player.getUniqueId());
            return;
        }
        if (!player.isOnline()) {
            requesterToTarget.remove(player.getUniqueId());
            return;
        }
        if (!isSafeLocation(destination)) {
            requesterToTarget.remove(player.getUniqueId());
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
                    if (p != null) applyImmunity(p);
                });
            } else {
                requesterToTarget.remove(player.getUniqueId());
            }
        });
    }

    private static final Set<Material> DANGEROUS_MATERIALS = Set.of(
            Material.LAVA,
            Material.FIRE,
            Material.MAGMA_BLOCK,
            Material.SOUL_FIRE,
            Material.CAMPFIRE,
            Material.SOUL_CAMPFIRE,
            Material.SWEET_BERRY_BUSH,
            Material.WITHER_ROSE
    );

    private boolean isSafeLocation(Location loc) {
        World world = loc.getWorld();
        if (world == null) return false;
        if (loc.getY() < world.getMinHeight()) return false;

        int bx = loc.getBlockX(), by = loc.getBlockY(), bz = loc.getBlockZ();
        Block feet   = world.getBlockAt(bx, by,     bz);
        Block head   = world.getBlockAt(bx, by + 1, bz);
        Block ground = world.getBlockAt(bx, by - 1, bz);

        Material feetType   = feet.getType();
        Material headType   = head.getType();
        Material groundType = ground.getType();

        return !feetType.isSolid()
                && !headType.isSolid()
                && groundType.isSolid()
                && !DANGEROUS_MATERIALS.contains(feetType)
                && !DANGEROUS_MATERIALS.contains(headType)
                && !DANGEROUS_MATERIALS.contains(groundType);
    }

    private void scheduleRatingPrompt(UUID playerId) {
        UUID targetId = requesterToTarget.remove(playerId);
        if (targetId == null) return;

        int delaySec = plugin.getConfigManager().getRatingDelay();
        if (delaySec <= 0) return;

        if (!pendingRatingScheduled.add(playerId)) return;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player rater = Bukkit.getPlayer(playerId);
            if (rater == null) {
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
                    if (r == null) {
                        pendingRatingScheduled.remove(playerId);
                        return;
                    }

                    String targetName = Optional.ofNullable(Bukkit.getPlayer(targetId))
                            .map(Player::getName)
                            .orElseGet(() -> {
                                String name = Bukkit.getOfflinePlayer(targetId).getName();
                                return name != null ? name : targetId.toString();
                            });

                    RatingSession session = new RatingSession(playerId, targetId, System.currentTimeMillis());
                    plugin.getGUIManager().addRatingSession(playerId, session);

                    String rateButtonText = plugin.getConfigManager().getMessage("rating.rate-button");
                    Component prompt = MessageUtil.toComponent(
                            plugin.getConfigManager().getPrefix()
                            + plugin.getConfigManager().getMessage("rating.prompt",
                                    Map.of("player", targetName)));
                    Component clickable = Component.text()
                            .append(prompt)
                            .append(Component.text(" "))
                            .append(MessageUtil.toComponent(rateButtonText)
                                    .clickEvent(ClickEvent.runCommand("/tprate"))
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
     * While immune, entity damage events are cancelled by {@link dev.indrajeeth.tpshield.listener.ImmunityListener}.
     * A per-second action-bar countdown is shown while immunity is active.
     */
    public void applyImmunity(Player player) {
        int immunitySeconds = plugin.getConfigManager().getTpImmunity();
        if (immunitySeconds <= 0) return;

        UUID playerId = player.getUniqueId();
        long expiry = System.currentTimeMillis() + (immunitySeconds * 1000L);
        immunePlayers.put(playerId, expiry);

        AtomicInteger remaining = new AtomicInteger(immunitySeconds);
        AtomicReference<BukkitTask> taskHolder = new AtomicReference<>();
        taskHolder.set(Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Player p = Bukkit.getPlayer(playerId);
            if (p == null) {
                immunePlayers.remove(playerId);
                BukkitTask t = taskHolder.get();
                if (t != null) t.cancel();
                return;
            }
            int secs = remaining.getAndDecrement();
            if (secs > 0) {
                MessageUtil.sendActionBar(p,
                        plugin.getConfigManager().getMessage("immunity.countdown",
                                Map.of("time", String.valueOf(secs))));
            } else {
                immunePlayers.remove(playerId);
                MessageUtil.sendActionBar(p,
                        plugin.getConfigManager().getMessage("immunity.expired"));
                BukkitTask t = taskHolder.get();
                if (t != null) t.cancel();
            }
        }, 0L, 20L));
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
        return activeRequests.entrySet().stream()
                .filter(e -> e.getValue().getTargetId().equals(playerId))
                .map(Map.Entry::getKey)
                .toList();
    }

    public List<UUID> getSentRequestsBy(UUID playerId) {
        TPARequest req = activeRequests.get(playerId);
        return req != null ? List.of(req.getTargetId()) : Collections.emptyList();
    }

    public long getCooldown(UUID playerId) {
        return cooldowns.getOrDefault(playerId, 0L);
    }

    public Set<UUID> getPendingTeleports() {
        return Set.copyOf(pendingTeleports.keySet());
    }

    public void cleanupExpiredRequests() {
        long now       = System.currentTimeMillis();
        long timeoutMs = plugin.getConfigManager().getRequestTimeout() * 1000L;
        activeRequests.entrySet().removeIf(e -> {
            if (now - e.getValue().getRequestTime() > timeoutMs) {
                UUID requesterId = e.getKey();
                requesterToTarget.remove(requesterId);
                Player requester = Bukkit.getPlayer(requesterId);
                if (requester != null) {
                    MessageUtil.sendMessageWithPlaceholders(requester,
                            plugin.getConfigManager().getPrefix()
                            + plugin.getConfigManager().getMessage("requests.expired"));
                    SoundUtil.play(requester, "request-expired");
                }
                UUID targetId = e.getValue().getTargetId();
                Player target = Bukkit.getPlayer(targetId);
                if (target != null) {
                    MessageUtil.sendMessageWithPlaceholders(target,
                            plugin.getConfigManager().getPrefix()
                            + plugin.getConfigManager().getMessage("requests.expired-target"));
                }
                return true;
            }
            return false;
        });
        acceptedPending.entrySet().removeIf(e -> {
            if (now - e.getValue().acceptTime() > timeoutMs) {
                UUID requesterId = e.getKey();
                Player requester = Bukkit.getPlayer(requesterId);
                if (requester != null) {
                    MessageUtil.sendMessageWithPlaceholders(requester,
                            plugin.getConfigManager().getPrefix()
                            + plugin.getConfigManager().getMessage("requests.confirm-expired"));
                }
                return true;
            }
            return false;
        });
        long cooldownMs = plugin.getConfigManager().getCooldown() * 1000L;
        cooldowns.entrySet().removeIf(e -> now - e.getValue() > cooldownMs + COOLDOWN_GRACE_PERIOD_MS);
    }

    /**
     * Called when a player disconnects. Cancels their pending teleport warmup,
     * any request they sent, and any requests that were targeting them — notifying
     * the other party in each case.
     */
    public void handlePlayerQuit(Player player) {
        UUID playerId = player.getUniqueId();

        plugin.getGUIManager().removeRatingSession(playerId);

        PendingTeleport pt = pendingTeleports.remove(playerId);
        if (pt != null) {
            pt.cancelSilently();
        }

        acceptedPending.remove(playerId);

        acceptedPending.entrySet().removeIf(e -> {
            if (e.getValue().accepterId().equals(playerId)) {
                UUID senderId = e.getKey();
                Player sender = Bukkit.getPlayer(senderId);
                if (sender != null) {
                    MessageUtil.sendMessageWithPlaceholders(sender,
                            plugin.getConfigManager().getPrefix()
                            + plugin.getConfigManager().getMessage("requests.confirm-cancelled",
                                    Map.of("player", player.getName())));
                }
                return true;
            }
            return false;
        });

        TPARequest sent = activeRequests.remove(playerId);
        if (sent != null) {
            requesterToTarget.remove(playerId);
            Player target = Bukkit.getPlayer(sent.getTargetId());
            if (target != null) {
                MessageUtil.sendMessageWithPlaceholders(target,
                        plugin.getConfigManager().getPrefix()
                        + plugin.getConfigManager().getMessage("requests.sender-left",
                                Map.of("player", player.getName())));
            }
        }

        List<UUID> senders = getPendingRequestsFor(playerId);
        for (UUID senderId : senders) {
            activeRequests.remove(senderId);
            requesterToTarget.remove(senderId);
            Player sender = Bukkit.getPlayer(senderId);
            if (sender != null) {
                MessageUtil.sendMessageWithPlaceholders(sender,
                        plugin.getConfigManager().getPrefix()
                        + plugin.getConfigManager().getMessage("requests.target-left",
                                Map.of("player", player.getName())));
            }
        }
    }

    public void cancelTeleport(UUID playerId) {
        PendingTeleport pt = pendingTeleports.remove(playerId);
        if (pt != null) pt.cancel();
        requesterToTarget.remove(playerId);
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
        private final TeleportRequestManager manager;
        private int remainingSeconds;
        private BukkitTask task;

        public PendingTeleport(Player player, Location destination,
                               Location startLocation, int delaySeconds,
                               TeleportRequestManager manager) {
            this.player           = player;
            this.destination      = destination;
            this.startLocation    = startLocation;
            this.remainingSeconds = delaySeconds;
            this.manager          = manager;
        }

        public void tick() {
            if (!player.isOnline()) { cancel(); return; }

            Location cur = player.getLocation();
            if (cur.getWorld() != startLocation.getWorld()
                    || cur.distance(startLocation) > 0.5) {
                MessageUtil.sendMessageWithPlaceholders(player,
                        manager.plugin.getConfigManager().getPrefix()
                        + manager.plugin.getConfigManager().getMessage("teleport.cancelled-moved"));
                cancel();
                return;
            }

            if (remainingSeconds > 0) {
                MessageUtil.sendActionBar(player,
                        manager.plugin.getConfigManager().getMessage("teleport.countdown-bar",
                                Map.of("time", String.valueOf(remainingSeconds))));
                SoundUtil.play(player, "teleport-countdown");
            }

            remainingSeconds--;
            if (remainingSeconds <= 0) complete();
        }

        private void complete() {
            if (task != null) task.cancel();
            manager.removePendingTeleport(player.getUniqueId());
            manager.performTeleport(player, destination);
        }

        public void cancel() {
            if (task != null) task.cancel();
            manager.removePendingTeleport(player.getUniqueId());
            manager.cleanupRequesterTarget(player.getUniqueId());
            if (player.isOnline()) {
                MessageUtil.sendMessageWithPlaceholders(player,
                        manager.plugin.getConfigManager().getPrefix()
                        + manager.plugin.getConfigManager().getMessage("teleport.cancelled"));
                SoundUtil.play(player, "teleport-cancelled");
            }
        }

        /** Cancels the warmup task without sending any message (used on player quit). */
        public void cancelSilently() {
            if (task != null) task.cancel();
            manager.removePendingTeleport(player.getUniqueId());
            manager.cleanupRequesterTarget(player.getUniqueId());
        }

        public boolean isValid()             { return player.isOnline() && remainingSeconds > 0; }
        public void    setTask(BukkitTask t) { this.task = t; }
        public int     getRemainingSeconds() { return remainingSeconds; }
    }
}
