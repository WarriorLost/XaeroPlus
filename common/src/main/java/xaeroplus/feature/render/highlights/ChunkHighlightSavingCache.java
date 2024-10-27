package xaeroplus.feature.render.highlights;

import com.google.common.util.concurrent.*;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.LongList;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xaero.map.MapProcessor;
import xaero.map.core.XaeroWorldMapCore;
import xaero.map.gui.GuiMap;
import xaeroplus.Globals;
import xaeroplus.XaeroPlus;
import xaeroplus.util.ChunkUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static net.minecraft.world.level.Level.*;
import static xaeroplus.util.ChunkUtils.getActualDimension;
import static xaeroplus.util.GuiMapHelper.*;

public class ChunkHighlightSavingCache implements ChunkHighlightCache {
    // these are initialized lazily
    @Nullable private ChunkHighlightDatabase database = null;
    @Nullable private String currentWorldId;
    private boolean worldCacheInitialized = false;
    @Nullable private final String databaseName;
    @Nullable private ListeningExecutorService executorService;
    private final Map<ResourceKey<Level>, ChunkHighlightCacheDimensionHandler> dimensionCacheMap = new ConcurrentHashMap<>(3);

    public ChunkHighlightSavingCache(final @NotNull String databaseName) {
        this.databaseName = databaseName;
    }

    @Override
    public boolean addHighlight(final int x, final int z) {
        try {
            ChunkHighlightCacheDimensionHandler cacheForCurrentDimension = getCacheForCurrentDimension();
            if (cacheForCurrentDimension == null) throw new RuntimeException("Didn't find cache for current dimension");
            cacheForCurrentDimension.addHighlight(x, z);
            getAllCaches().stream()
                    .map(ChunkHighlightCacheDimensionHandler::writeAllHighlightsToDatabase)
                    .collect(Collectors.toList());
            return true;
        } catch (final Exception e) {
            XaeroPlus.LOGGER.debug("Error adding highlight to {} disk cache: {}, {}", databaseName, x, z, e);
            return false;
        }
    }

    public void addHighlight(final int x, final int z, final long foundTime, final ResourceKey<Level> dimension) {
        if (dimension == null) return;
        ChunkHighlightCacheDimensionHandler cacheForDimension = getCacheForDimension(dimension, true);
        if (cacheForDimension == null) return;
        cacheForDimension.addHighlight(x, z, foundTime);
    }

    @Override
    public boolean removeHighlight(final int x, final int z) {
        try {
            ChunkHighlightCacheDimensionHandler cacheForCurrentDimension = getCacheForCurrentDimension();
            if (cacheForCurrentDimension == null) throw new RuntimeException("Didn't find cache for current dimension");
            cacheForCurrentDimension.removeHighlight(x, z);
            return true;
        } catch (final Exception e) {
            XaeroPlus.LOGGER.debug("Error removing highlight from {} disk cache: {}, {}", databaseName, x, z, e);
            return false;
        }
    }

    @Override
    public boolean isHighlighted(final int chunkPosX, final int chunkPosZ, final ResourceKey<Level> dimensionId) {
        if (dimensionId == null) return false;
        ChunkHighlightCacheDimensionHandler cacheForDimension = getCacheForDimension(dimensionId, false);
        if (cacheForDimension == null) return false;
        return cacheForDimension.isHighlighted(chunkPosX, chunkPosZ, dimensionId);
    }

    public boolean isHighlighted(final int chunkPosX, final int chunkPosZ) {
        ChunkHighlightCacheDimensionHandler cacheForDimension = getCacheForDimension(getActualDimension(), false);
        if (cacheForDimension == null) return false;
        return cacheForDimension.isHighlighted(chunkPosX, chunkPosZ, getActualDimension());
    }

    @Override
    public LongList getHighlightsSnapshot(final ResourceKey<Level> dimensionId) {
        if (dimensionId == null) return LongList.of();
        ChunkHighlightCacheDimensionHandler cacheForDimension = getCacheForDimension(dimensionId, false);
        if (cacheForDimension == null) return LongList.of();
        return cacheForDimension.getHighlightsSnapshot(dimensionId);
    }

    @Override
    public void handleWorldChange() {
        Futures.whenAllComplete(saveAllChunks())
                .call(() -> {
                    reset();
                    initializeWorld();
                    loadChunksInActualDimension();
                    return null;
                }, Globals.cacheRefreshExecutorService.get());
    }

    public synchronized void reset() {
        this.worldCacheInitialized = false;
        this.currentWorldId = null;
        if (this.executorService != null) this.executorService.shutdown();
        if (this.database != null) this.database.close();
        this.dimensionCacheMap.clear();
        this.database = null;
    }

    private List<ListenableFuture<?>> saveAllChunks() {
        if (!worldCacheInitialized) return Collections.emptyList();
        return getAllCaches().stream()
                .map(ChunkHighlightCacheDimensionHandler::writeAllHighlightsToDatabase)
                .collect(Collectors.toList());
    }

    public ChunkHighlightCacheDimensionHandler getCacheForCurrentDimension() {
        if (!worldCacheInitialized) return null;
        return getCacheForDimension(ChunkUtils.getActualDimension(), true);
    }

    private ChunkHighlightCacheDimensionHandler initializeDimensionCacheHandler(final ResourceKey<Level> dimension) {
        if (dimension == null) return null;
        var db = this.database;
        var executor = this.executorService;
        if (db == null || executor == null) {
            XaeroPlus.LOGGER.error("Unable to initialize {} disk cache handler for: {}, database or executor is null", databaseName, dimension.location());
            return null;
        }
        var cacheHandler = new ChunkHighlightCacheDimensionHandler(dimension, db, executor);
        db.initializeDimension(dimension);
        this.dimensionCacheMap.put(dimension, cacheHandler);
        return cacheHandler;
    }

    public ChunkHighlightCacheDimensionHandler getCacheForDimension(final ResourceKey<Level> dimension, boolean create) {
        if (!worldCacheInitialized) return null;
        if (dimension == null) return null;
        var dimensionCache = dimensionCacheMap.get(dimension);
        if (dimensionCache == null) {
            if (!create) return null;
            XaeroPlus.LOGGER.info("Initializing {} disk cache for dimension: {}", databaseName, dimension.location());
            dimensionCache = initializeDimensionCacheHandler(dimension);
        }
        return dimensionCache;
    }

    private List<ChunkHighlightCacheDimensionHandler> getAllCaches() {
        return new ArrayList<>(dimensionCacheMap.values());
    }

    public List<ChunkHighlightCacheDimensionHandler> getCachesExceptDimension(final ResourceKey<Level> dimension) {
        var caches = new ArrayList<ChunkHighlightCacheDimensionHandler>(dimensionCacheMap.size());
        for (var entry : dimensionCacheMap.entrySet()) {
            if (!entry.getKey().equals(dimension)) {
                caches.add(entry.getValue());
            }
        }
        return caches;
    }

    private synchronized void initializeWorld() {
        try {
            MapProcessor mapProcessor = XaeroWorldMapCore.currentSession.getMapProcessor();
            if (mapProcessor == null) return;
            final String worldId = mapProcessor.getCurrentWorldId();
            if (worldId == null) return;
            this.currentWorldId = worldId;
            this.executorService = MoreExecutors.listeningDecorator(
                Executors.newSingleThreadExecutor(
                    new ThreadFactoryBuilder()
                        .setNameFormat(databaseName + "-DiskCache")
                        .setUncaughtExceptionHandler((t, e) -> {
                            XaeroPlus.LOGGER.error("Uncaught exception handler in {}", t.getName(), e);
                        })
                        .build()));
            this.database = new ChunkHighlightDatabase(worldId, databaseName);
            initializeDimensionCacheHandler(OVERWORLD);
            initializeDimensionCacheHandler(NETHER);
            initializeDimensionCacheHandler(END);
            this.worldCacheInitialized = true;
            loadChunksInActualDimension();
        } catch (final Exception e) {
            // expected on game launch
        }
    }

    private void loadChunksInActualDimension() {
        ChunkHighlightCacheDimensionHandler cacheForCurrentDimension = getCacheForCurrentDimension();
        if (cacheForCurrentDimension == null) return;
        cacheForCurrentDimension
            .setWindow(ChunkUtils.actualPlayerRegionX(), ChunkUtils.actualPlayerRegionZ(), getMinimapRegionWindowSize());
    }

    @Override
    public void onEnable() {
        if (!worldCacheInitialized) {
            initializeWorld();
        }
    }

    @Override
    public void onDisable() {
        Futures.whenAllComplete(saveAllChunks()).call(() -> {
            reset();
            return null;
        }, Globals.cacheRefreshExecutorService.get());
    }

    @Override
    public Long2LongMap getHighlightsState() {
        return null;
    }

    @Override
    public void loadPreviousState(final Long2LongMap state) {

    }

    public int getMinimapRegionWindowSize() {
        return Math.max(3, Globals.minimapScaleMultiplier);
    }

    int tickCounter = 0;

    @Override
    public void handleTick() {
        if (!worldCacheInitialized) return;
        // limit so we don't overflow
        if (tickCounter > 2400) tickCounter = 0;
        if (tickCounter++ % 30 != 0) { // run once every 1.5 seconds
            return;
        }
        // autosave current window every 60 seconds
        if (tickCounter % 1200 == 0) {
            getAllCaches().forEach(ChunkHighlightCacheDimensionHandler::writeAllHighlightsToDatabase);
            return;
        }

        Optional<GuiMap> guiMapOptional = getGuiMap();
        if (guiMapOptional.isPresent()) {
            final GuiMap guiMap = guiMapOptional.get();
            final ResourceKey<Level> mapDimension = Globals.getCurrentDimensionId();
            final int mapCenterX = getGuiMapCenterRegionX(guiMap);
            final int mapCenterZ = getGuiMapCenterRegionZ(guiMap);
            final int mapSize = getGuiMapRegionSize(guiMap);
            final ChunkHighlightCacheDimensionHandler cacheForDimension = getCacheForDimension(mapDimension, true);
            if (cacheForDimension != null) cacheForDimension.setWindow(mapCenterX, mapCenterZ, mapSize);
            getCachesExceptDimension(mapDimension)
                .forEach(cache -> cache.setWindow(0, 0, 0));
        } else {
            final ChunkHighlightCacheDimensionHandler cacheForDimension = getCacheForDimension(Globals.getCurrentDimensionId(), true);
            if (cacheForDimension != null) cacheForDimension.setWindow(ChunkUtils.getPlayerRegionX(), ChunkUtils.getPlayerRegionZ(), getMinimapRegionWindowSize());
            getCachesExceptDimension(Globals.getCurrentDimensionId())
                .forEach(cache -> cache.setWindow(0, 0, 0));
        }
    }
}
