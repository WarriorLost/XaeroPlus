package xaeroplus.module.impl;

import com.google.common.hash.Hashing;
import net.lenni0451.lambdaevents.EventHandler;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import xaero.common.XaeroMinimapSession;
import xaero.common.minimap.waypoints.*;
import xaero.map.mods.SupportMods;
import xaeroplus.XaeroPlus;
import xaeroplus.event.ClientTickEvent;
import xaeroplus.event.XaeroWorldChangeEvent;
import xaeroplus.feature.extensions.IWaypointDimension;
import xaeroplus.module.Module;
import xaeroplus.util.BlayWaystonesHelper;
import xaeroplus.util.ColorHelper.WaystoneColor;
import xaeroplus.util.FabricWaystonesHelper;
import xaeroplus.util.WaypointsHelper;
import xaeroplus.util.WaystonesHelper;

import java.util.*;
import java.util.stream.Collectors;

import static xaero.common.settings.ModSettings.COLORS;

public class WaystoneSync extends Module {
    private final BlayWaystonesHelper blayWaystonesHelper = new BlayWaystonesHelper();
    private WaystoneColor color = WaystoneColor.RANDOM;
    private boolean separateWaypointSet = false;
    private int visibilityType = 0;

    @Override
    public void onEnable() {
        if (WaystonesHelper.isWaystonesPresent()) {
            blayWaystonesHelper.subscribeWaystonesEvent();
        }
        if (WaystonesHelper.isFabricWaystonesPresent()) {
            FabricWaystonesHelper.subcribeWaystonesEventsRunnable.run();
        }
        reloadWaystones();
    }

    @Override
    public void onDisable() {
        blayWaystonesHelper.toSyncWaystones = Collections.emptyList();
    }

    @EventHandler
    public void onXaeroWorldChangeEvent(final XaeroWorldChangeEvent event) {
        if (event.worldId() == null) {
            blayWaystonesHelper.toSyncWaystones = Collections.emptyList();
        }
    }

    @EventHandler
    public void onClientTickEvent(final ClientTickEvent.Post event) {
        if (WaystonesHelper.isWaystonesPresent()) {
            if (blayWaystonesHelper.shouldSync) {
                if (syncBlayWaystones()) {
                    blayWaystonesHelper.shouldSync = false;
                    blayWaystonesHelper.toSyncWaystones = Collections.emptyList();
                }
            }
        } else if (WaystonesHelper.isFabricWaystonesPresent()) {
            if (FabricWaystonesHelper.shouldSync) {
                syncFabricWaystones();
                FabricWaystonesHelper.shouldSync = false;
            }
        }
    }

    public void syncFabricWaystones() {
        commonWaystoneSync(FabricWaystonesHelper.getWaystones());
    }

    public boolean syncBlayWaystones() {
        return commonWaystoneSync(blayWaystonesHelper.getToSyncWaystones());
    }

    public boolean commonWaystoneSync(final List<Waystone> waystones) {
        try {
            XaeroMinimapSession minimapSession = XaeroMinimapSession.getCurrentSession();
            if (minimapSession == null) return false;
            final WaypointsManager waypointsManager = minimapSession.getWaypointsManager();
            WaypointSet waypointSet = waypointsManager.getWaypoints();
            if (waypointSet == null) return false;
            final String currentContainerId = waypointsManager.getCurrentContainerID();
            if (waypointsManager.getCurrentWorld() == null) return false;

            // iterate over ALL waypoint sets and lists and remove waystones
            // todo: this doesn't iterate over dims/set permutations where we have no waystones at all
            //  there isn't a great interface xaero provides to get all permutations unfortunately - this already has lots of hacks
            final Map<Waystone, List<Waypoint>> waypointToWaypointsList = waystones.stream()
                .collect(Collectors.toMap((k1) -> k1,
                                          (v1) -> getWaypointsList(v1, waypointsManager, currentContainerId),
                                          (v1, v2) -> v1));
            for (List<Waypoint> waypointsList : new HashSet<>(waypointToWaypointsList.values())) {
                waypointsList.removeIf(waypoint -> waypoint.isTemporary() && waypoint.getName().endsWith(" [Waystone]"));
            }
            for (Map.Entry<Waystone, List<Waypoint>> entry : waypointToWaypointsList.entrySet()) {
                try {
                    waypointsListSync(entry.getKey(), entry.getValue());
                } catch (final Exception e) {
                    XaeroPlus.LOGGER.error("Error syncing waystone: {}", entry.getKey().name(), e);
                }
            }
            SupportMods.xaeroMinimap.requestWaypointsRefresh();
            return true;
        } catch (Exception e) {
            XaeroPlus.LOGGER.error("Error syncing waystones", e);
            return true; // stops immediate retry. we'll still spam logs on the next iteration though
        }
    }

    private void waypointsListSync(final Waystone waystone, final List<Waypoint> waypointsList) {
        Waypoint waystoneWp = new Waypoint(
            waystone.x(),
            waystone.y(),
            waystone.z(),
            waystone.name() + " [Waystone]",
            waystone.name().isEmpty()
                ? "W"
                : waystone.name().substring(0, 1).toUpperCase(Locale.ROOT),
            getWaystoneColor(waystone),
            0,
            true
        );
        waystoneWp.setVisibilityType(visibilityType);
        ((IWaypointDimension) waystoneWp).setDimension(waystone.dimension());
        waypointsList.add(waystoneWp);
    }

    private List<Waypoint> getWaypointsList(final Waystone waystone,
                                            final WaypointsManager waypointsManager,
                                            final String currentContainerId) {
        final String waypointSetName = this.separateWaypointSet ? "Waystones" : "gui.xaero_default";
        final WaypointWorld waypointWorld = getWaypointWorldForWaystone(waystone, waypointsManager, currentContainerId);
        WaypointSet waypointSet = waypointWorld.getSets().get(waypointSetName);
        if (waypointSet == null) {
            waypointWorld.getSets().put(waypointSetName, new WaypointSet(waypointSetName));
            waypointSet = waypointWorld.getSets().get(waypointSetName);
        }
        return waypointSet.getList();
    }

    private WaypointWorld getWaypointWorldForWaystone(final Waystone waystone,
                                                      final WaypointsManager waypointsManager,
                                                      final String currentContainerId) {
        final ResourceKey<Level> waystoneDimension = waystone.dimension();
        final String waystoneDimensionDirectoryName = waypointsManager.getDimensionDirectoryName(waystoneDimension);
        final int waystoneDim = WaypointsHelper.getDimensionForWaypointWorldKey(waystoneDimensionDirectoryName);
        final WaypointWorld currentWpWorld = waypointsManager.getCurrentWorld();
        if (currentWpWorld == null) {
            throw new RuntimeException("WaystoneSync: current waypoint world is null");
        }
        if (currentWpWorld.getDimId() == waystoneDimension) {
            return currentWpWorld;
        }
        final String worldContainerSuffix;
        if (waystoneDim == Integer.MIN_VALUE) // non-vanilla dimensions
            worldContainerSuffix = waystoneDimension.location().getNamespace() + "$" + waystoneDimension.location().getPath().replace("/", "%");
        else
            // this is a crapshoot
            // waypoint containers have no single naming scheme. this can change depending on the server, the world, and the dimension
            worldContainerSuffix = String.valueOf(waystoneDim);
        final WaypointWorldContainer waypointWorldContainer = waypointsManager.getWorldContainer(currentContainerId.substring(
            0,
            currentContainerId.lastIndexOf(37) + 1) + worldContainerSuffix);
        WaypointWorld crossDimWaypointWorld = waypointWorldContainer.worlds.get(currentWpWorld.getId());
        if (crossDimWaypointWorld == null) {
            waypointWorldContainer.worlds.put(currentWpWorld.getId(), new WaypointWorld(waypointWorldContainer, currentWpWorld.getId(), waystoneDimension));
            crossDimWaypointWorld = waypointWorldContainer.worlds.get(currentWpWorld.getId());
        }
        return crossDimWaypointWorld;
    }

    private int getWaystoneColor(Waystone waystone) {
        if (color == WaystoneColor.RANDOM) {
            return Math.abs(Hashing.murmur3_128().hashUnencodedChars(waystone.name()).asInt()) % COLORS.length;
        } else {
            return color.getColorIndex();
        }
    }

    public void setColor(final WaystoneColor color) {
        this.color = color;
        reloadWaystones();
    }

    public void setWaypointSet(final boolean waypointSet) {
        this.separateWaypointSet = waypointSet;
        reloadWaystones();
    }

    public void setVisibilityType(final int visibilityType) {
        this.visibilityType = visibilityType;
        reloadWaystones();
    }

    public void reloadWaystones() {
        blayWaystonesHelper.shouldSync = true;
        FabricWaystonesHelper.shouldSync = true;
    }

    public record Waystone(String name, ResourceKey<Level> dimension, int x, int y, int z) { }
}
