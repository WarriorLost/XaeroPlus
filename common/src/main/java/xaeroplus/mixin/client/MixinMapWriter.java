package xaeroplus.mixin.client;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import com.llamalad7.mixinextras.sugar.ref.LocalLongRef;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.map.MapProcessor;
import xaero.map.MapWriter;
import xaero.map.biome.BiomeColorCalculator;
import xaero.map.biome.BlockTintProvider;
import xaero.map.region.MapBlock;
import xaero.map.region.MapRegion;
import xaero.map.region.OverlayBuilder;
import xaero.map.region.OverlayManager;
import xaero.map.world.MapWorld;
import xaeroplus.feature.extensions.CustomMapProcessor;
import xaeroplus.settings.XaeroPlusSettingRegistry;
import xaeroplus.util.ChunkUtils;

import java.util.concurrent.TimeUnit;

import static java.util.Objects.nonNull;
import static net.minecraft.world.level.Level.NETHER;

@Mixin(value = MapWriter.class, remap = false)
public abstract class MixinMapWriter {
    // insert our own limiter on new tiles being written but this one's keyed on the actual chunk
    // tile "writes" also include a lot of extra operations and lookups before any writing is actually done
    // when we remove existing limiters those extra operations add up to a lot of unnecessary cpu time
    private final Cache<Long, Long> tileUpdateCache = Caffeine.newBuilder()
            // I would usually expect even second long expiration here to be fine
            // but there are some operations that make repeat invocations actually required
            // perhaps another time ill rewrite those. Or make the cache lock more aware of when we don't have any new updates to write/load
            // there's still alot of performance and efficiency on the table to regain
            // but i think this is a good middle ground for now
            .maximumSize(10000)
            .expireAfterWrite(5L, TimeUnit.SECONDS)
            .<Long, Long>build();

    @Shadow
    private MapProcessor mapProcessor;
    @Shadow
    @Final
    private BlockPos.MutableBlockPos mutableLocalPos;
    @Shadow
    public long writeFreeSinceLastWrite;


    @Inject(method = "loadPixel", at = @At("HEAD"), remap = false)
    public void setObsidianColumnLocalVar(final Level world, final Registry<Block> blockRegistry, final MapBlock pixel, final MapBlock currentPixel, final LevelChunk bchunk, final int insideX, final int insideZ, final int highY, final int lowY, final boolean cave, final boolean fullCave, final int mappedHeight, final boolean canReuseBiomeColours, final boolean ignoreHeightmaps, final Registry<Biome> biomeRegistry, final boolean flowers, final int worldBottomY, final BlockPos.MutableBlockPos mutableBlockPos3, final CallbackInfo ci,
                                          @Share("columnRoofObsidian") LocalBooleanRef columnRoofObsidianRef) {
        if (!XaeroPlusSettingRegistry.transparentObsidianRoofSetting.getValue()) return;
        columnRoofObsidianRef.set(false);
    }

    @Inject(method = "loadPixel", at = @At(
        value = "INVOKE",
        target = "Lnet/minecraft/world/level/block/state/BlockState;getFluidState()Lnet/minecraft/world/level/material/FluidState;",
        ordinal = 0
    ), remap = true)
    public void obsidianRoofHeadInject(final Level world, final Registry<Block> blockRegistry, final MapBlock pixel, final MapBlock currentPixel, final LevelChunk bchunk, final int insideX, final int insideZ, final int highY, final int lowY, final boolean cave, final boolean fullCave, final int mappedHeight, final boolean canReuseBiomeColours, final boolean ignoreHeightmaps, final Registry<Biome> biomeRegistry, final boolean flowers, final int worldBottomY, final BlockPos.MutableBlockPos mutableBlockPos3, final CallbackInfo ci,
                                       @Local(name = "state") LocalRef<BlockState> stateRef,
                                       @Local(name = "h") LocalIntRef hRef,
                                       @Local(name = "transparentSkipY") LocalIntRef transparentSkipYRef,
                                       @Share("columnRoofObsidian") LocalBooleanRef columnRoofObsidianRef
    ) {
        if (!XaeroPlusSettingRegistry.transparentObsidianRoofSetting.getValue()) return;
        final Block b = stateRef.get().getBlock();
        final boolean blockHeightAboveYLimit = hRef.get() >= XaeroPlusSettingRegistry.transparentObsidianRoofYSetting.getValue();

        if (blockHeightAboveYLimit) {
            boolean shouldMakeTransparent = (b == Blocks.OBSIDIAN || b == Blocks.CRYING_OBSIDIAN);
            if (b == Blocks.SNOW) {
                this.mutableLocalPos.setY(hRef.get() - 1);
                BlockState belowState = bchunk.getBlockState(this.mutableLocalPos);
                this.mutableLocalPos.setY(hRef.get());
                shouldMakeTransparent = belowState.getBlock() == Blocks.OBSIDIAN || belowState.getBlock() == Blocks.CRYING_OBSIDIAN;
            }
            if (shouldMakeTransparent) {
                if (XaeroPlusSettingRegistry.transparentObsidianRoofDarkeningSetting.getValue() == 0) {
                    stateRef.set(Blocks.AIR.defaultBlockState());
                    transparentSkipYRef.set(transparentSkipYRef.get() - 1);
                }
                if (!columnRoofObsidianRef.get()) columnRoofObsidianRef.set(true);
            }
        }
    }

    // What we want: a = !b && !c
    // But we're wrapping b
    // so instead we do a = !(b || c)
    @WrapOperation(method = "loadPixel", at = @At(
        value = "INVOKE",
        target = "Lxaero/map/region/OverlayBuilder;isEmpty()Z"
    ), remap = false)
    public boolean checkObsidianRoofColumn(final OverlayBuilder instance, final Operation<Boolean> original,
                                           @Share("columnRoofObsidian") final LocalBooleanRef columnRoofObsidianRef) {
        if (!XaeroPlusSettingRegistry.transparentObsidianRoofSetting.getValue()) return original.call(instance);
        return original.call(instance) || columnRoofObsidianRef.get();
    }

    @ModifyExpressionValue(method = "loadPixelHelp", at = @At(
        value = "INVOKE",
        target = "Lxaero/map/MapWriter;shouldOverlayCached(Lnet/minecraft/world/level/block/state/StateHolder;)Z",
        ordinal = 0
    ), remap = true) // $REMAP
    public boolean obsidianRoofOverlayMod(final boolean original,
                                          @Local(argsOnly = true) LevelChunk bChunk,
                                          @Local(name = "b") Block b,
                                          @Local(name = "h") int h
    ) {
        if (XaeroPlusSettingRegistry.transparentObsidianRoofSetting.getValue()
            && h > XaeroPlusSettingRegistry.transparentObsidianRoofYSetting.getValue()) {
            if (b == Blocks.OBSIDIAN || b == Blocks.CRYING_OBSIDIAN) {
                return true;
            } else if (b == Blocks.SNOW) {
                this.mutableLocalPos.setY(h - 1);
                BlockState belowState = bChunk.getBlockState(this.mutableLocalPos);
                this.mutableLocalPos.setY(h);
                return belowState.getBlock() == Blocks.OBSIDIAN || belowState.getBlock() == Blocks.CRYING_OBSIDIAN;
            }
        }
        return original;
    }

    @WrapOperation(method = "loadPixelHelp", at = @At(
        value = "INVOKE",
        target = "Lnet/minecraft/world/level/block/state/BlockState;getLightBlock(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)I",
        ordinal = 1
    ), remap = true)
    public int getOpacityForObsidianRoof(BlockState instance, BlockGetter world, BlockPos pos, Operation<Integer> original,
                                         @Local(name = "h") int h) {
        if (XaeroPlusSettingRegistry.transparentObsidianRoofSetting.getValue()
            && h > XaeroPlusSettingRegistry.transparentObsidianRoofYSetting.getValue()) {
            boolean shouldMakeTransparent = instance.getBlock() == Blocks.OBSIDIAN || instance.getBlock() == Blocks.CRYING_OBSIDIAN;
            if (instance.getBlock() == Blocks.SNOW) {
                this.mutableLocalPos.setY(h - 1);
                BlockState belowState = world.getBlockState(this.mutableLocalPos);
                this.mutableLocalPos.setY(h);
                if (belowState.getBlock() == Blocks.OBSIDIAN || belowState.getBlock() == Blocks.CRYING_OBSIDIAN)
                    shouldMakeTransparent = true;
            }
            if (shouldMakeTransparent) {
                return 5;
            }
        }
        return original.call(instance, world, pos);
    }

    @Inject(method = "onRender", at = @At(
        value = "FIELD",
        target = "Lxaero/map/MapWriter;lastWrite:J",
        opcode = Opcodes.GETFIELD,
        ordinal = 2
    ))
    public void fastMapMaxTilesPerCycleSetting(final BiomeColorCalculator biomeColorCalculator, final OverlayManager overlayManager, final CallbackInfo ci,
                                        @Local(name = "tilesToUpdate") LocalLongRef tilesToUpdateRef,
                                        @Local(name = "sizeTiles") int sizeTiles) {
        if (XaeroPlusSettingRegistry.fastMapSetting.getValue()) {
            this.writeFreeSinceLastWrite = Math.max(1L, this.writeFreeSinceLastWrite);
            if (this.mapProcessor.getCurrentCaveLayer() == Integer.MAX_VALUE) {
                tilesToUpdateRef.set((long) Math.min(sizeTiles,
                                                     XaeroPlusSettingRegistry.fastMapMaxTilesPerCycle.getValue()));
            }
        }
    }

    @ModifyExpressionValue(method = "onRender", at = @At(
        value = "INVOKE",
        target = "Ljava/lang/System;nanoTime()J",
        ordinal = 2
    ))
    public long removeWriteTimeLimiterPerFrame(long original) {
        if (XaeroPlusSettingRegistry.fastMapSetting.getValue()) {
            if (this.mapProcessor.getCurrentCaveLayer() == Integer.MAX_VALUE) {
                return 0;
            }
        }
        return original;
    }

    @Inject(method = "writeChunk", at = @At(value = "HEAD"), cancellable = true, remap = false)
    public void writeChunk(
            Level world,
            Registry<Block> blockRegistry,
            int distance,
            boolean onlyLoad,
            Registry<Biome> biomeRegistry,
            OverlayManager overlayManager,
            boolean loadChunks,
            boolean updateChunks,
            boolean ignoreHeightmaps,
            boolean flowers,
            boolean detailedDebug,
            BlockPos.MutableBlockPos mutableBlockPos3,
            BlockTintProvider blockTintProvider,
            int caveDepth,
            int caveStart,
            int layerToWrite,
            int tileChunkX,
            int tileChunkZ,
            int tileChunkLocalX,
            int tileChunkLocalZ,
            int chunkX,
            int chunkZ,
            final CallbackInfoReturnable<Boolean> cir) {
        if (!XaeroPlusSettingRegistry.fastMapSetting.getValue()) return;
        if (this.mapProcessor.getCurrentCaveLayer() != Integer.MAX_VALUE) return;

        final Long cacheable = ChunkUtils.chunkPosToLong(chunkX, chunkZ);
        final Long cacheValue = tileUpdateCache.getIfPresent(cacheable);
        if (nonNull(cacheValue)) {
            if (cacheValue < System.currentTimeMillis() - (long) XaeroPlusSettingRegistry.fastMapWriterDelaySetting.getValue()) {
                tileUpdateCache.put(cacheable, System.currentTimeMillis());
            } else {
                cir.setReturnValue(false);
                cir.cancel();
            }
        } else {
            tileUpdateCache.put(cacheable, System.currentTimeMillis());
        }
    }

    @Inject(method = "loadPixel", at = @At("HEAD"), remap = false)
    public void netherCaveFixInject(final Level world, final Registry<Block> blockRegistry, final MapBlock pixel, final MapBlock currentPixel, final LevelChunk bchunk, final int insideX, final int insideZ, final int highY, final int lowY, final boolean cave, final boolean fullCave, final int mappedHeight, final boolean canReuseBiomeColours, final boolean ignoreHeightmaps, final Registry<Biome> biomeRegistry, final boolean flowers, final int worldBottomY, final BlockPos.MutableBlockPos mutableBlockPos3, final CallbackInfo ci,
                                    @Local(index = 10, argsOnly = true) LocalBooleanRef caveRef,
                                    @Local(index = 11, argsOnly = true) LocalBooleanRef fullCaveRef
                                    ) {
        if (XaeroPlusSettingRegistry.netherCaveFix.getValue()) {
            var nether = world.dimension() == NETHER;
            var shouldForceFullInNether = !cave && nether;
            caveRef.set(shouldForceFullInNether || cave);
            fullCaveRef.set(shouldForceFullInNether || fullCave);
        }
    }

    @WrapOperation(method = "onRender", at = @At(
        value = "INVOKE",
        target = "Lxaero/map/world/MapWorld;getCurrentDimensionId()Lnet/minecraft/resources/ResourceKey;"),
        remap = true) // $REMAP
    public ResourceKey<Level> removeCustomDimSwitchWriterPrevention(final MapWorld mapWorld, final Operation<ResourceKey<Level>> original) {
        var world = mapProcessor.getWorld();
        return XaeroPlusSettingRegistry.writesWhileDimSwitched.getValue() && world != null && mapWorld.isMultiplayer()
            ? world.dimension() // makes if condition in injected code always true
            : original.call(mapWorld);
    }

    /**
     * Redirects or WrapMethods inside writeChunk can cause delayed ARM JVM crashes due to a bug in the C2 compiler
     *
     * We are using terrible threadlocal hacks to signal to methods we are interested in to change their behavior themselves
     *
     * if writeChunk or a method down the stack throws an exception it is possible for the signal not to be reset, although signals won't cross thread barriers
     */

    @Inject(method = "writeChunk", at = @At("HEAD"))
    public void getActualMapRegionDimSignalHead(final Level world, final Registry<Block> blockRegistry, final int distance, final boolean onlyLoad, final Registry<Biome> biomeRegistry, final OverlayManager overlayManager, final boolean loadChunks, final boolean updateChunks, final boolean ignoreHeightmaps, final boolean flowers, final boolean detailedDebug, final BlockPos.MutableBlockPos mutableBlockPos3, final BlockTintProvider blockTintProvider, final int caveDepth, final int caveStart, final int layerToWrite, final int tileChunkX, final int tileChunkZ, final int tileChunkLocalX, final int tileChunkLocalZ, final int chunkX, final int chunkZ, final CallbackInfoReturnable<Boolean> cir) {
        ((CustomMapProcessor) mapProcessor).xaeroPlus$getLeafRegionActualDimSignal().set(XaeroPlusSettingRegistry.writesWhileDimSwitched.getValue() && mapProcessor.getMapWorld().isMultiplayer());
    }

    @Inject(method = "writeChunk", at = @At("RETURN"))
    public void getActualMapRegionDimSignalReturn(final Level world, final Registry<Block> blockRegistry, final int distance, final boolean onlyLoad, final Registry<Biome> biomeRegistry, final OverlayManager overlayManager, final boolean loadChunks, final boolean updateChunks, final boolean ignoreHeightmaps, final boolean flowers, final boolean detailedDebug, final BlockPos.MutableBlockPos mutableBlockPos3, final BlockTintProvider blockTintProvider, final int caveDepth, final int caveStart, final int layerToWrite, final int tileChunkX, final int tileChunkZ, final int tileChunkLocalX, final int tileChunkLocalZ, final int chunkX, final int chunkZ, final CallbackInfoReturnable<Boolean> cir) {
        ((CustomMapProcessor) mapProcessor).xaeroPlus$getLeafRegionActualDimSignal().set(false);
    }

    @Inject(method = "writeChunk", at = @At("HEAD"))
    public void setGetCurrentDimActualDimSignalHead(final Level world, final Registry<Block> blockRegistry, final int distance, final boolean onlyLoad, final Registry<Biome> biomeRegistry, final OverlayManager overlayManager, final boolean loadChunks, final boolean updateChunks, final boolean ignoreHeightmaps, final boolean flowers, final boolean detailedDebug, final BlockPos.MutableBlockPos mutableBlockPos3, final BlockTintProvider blockTintProvider, final int caveDepth, final int caveStart, final int layerToWrite, final int tileChunkX, final int tileChunkZ, final int tileChunkLocalX, final int tileChunkLocalZ, final int chunkX, final int chunkZ, final CallbackInfoReturnable<Boolean> cir) {
        var mapWorld = mapProcessor.getWorld();
        var setSignal = XaeroPlusSettingRegistry.writesWhileDimSwitched.getValue() && mapWorld != null && mapProcessor.getMapWorld().isMultiplayer();
        ((CustomMapProcessor) mapProcessor).xaeroPlus$getCurrentDimensionActualDimSignal().set(setSignal);
    }

    @Inject(method = "writeChunk", at = @At("RETURN"))
    public void setGetCurrentDimActualDimSignalTail(final Level world, final Registry<Block> blockRegistry, final int distance, final boolean onlyLoad, final Registry<Biome> biomeRegistry, final OverlayManager overlayManager, final boolean loadChunks, final boolean updateChunks, final boolean ignoreHeightmaps, final boolean flowers, final boolean detailedDebug, final BlockPos.MutableBlockPos mutableBlockPos3, final BlockTintProvider blockTintProvider, final int caveDepth, final int caveStart, final int layerToWrite, final int tileChunkX, final int tileChunkZ, final int tileChunkLocalX, final int tileChunkLocalZ, final int chunkX, final int chunkZ, final CallbackInfoReturnable<Boolean> cir) {
        ((CustomMapProcessor) mapProcessor).xaeroPlus$getCurrentDimensionActualDimSignal().set(false);
    }

    @WrapOperation(method = "onRender", at = @At(
        value = "INVOKE",
        target = "Lxaero/map/MapProcessor;getLeafMapRegion(IIIZ)Lxaero/map/region/MapRegion;"
    ))
    public MapRegion getActualMapRegionInOnRender(final MapProcessor mapProcessor, int caveLayer, int regX, int regZ, boolean create, final Operation<MapRegion> original) {
        if (XaeroPlusSettingRegistry.writesWhileDimSwitched.getValue() && mapProcessor.getMapWorld().isMultiplayer()) {
            ((CustomMapProcessor) mapProcessor).xaeroPlus$getLeafRegionActualDimSignal().set(true);
        }
        try {
            return original.call(
                mapProcessor,
                caveLayer,
                regX,
                regZ,
                create);
        } finally {
            ((CustomMapProcessor) mapProcessor).xaeroPlus$getLeafRegionActualDimSignal().set(false);
        }
    }
}
