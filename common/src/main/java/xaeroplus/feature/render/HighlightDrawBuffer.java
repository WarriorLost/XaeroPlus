package xaeroplus.feature.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import it.unimi.dsi.fastutil.longs.LongList;
import org.jetbrains.annotations.Nullable;
import xaeroplus.util.ChunkUtils;

public class HighlightDrawBuffer {
    private boolean stale = true;
    @Nullable private VertexBuffer vertexBuffer = null;

    public boolean needsRefresh() {
        return vertexBuffer == null || vertexBuffer.isInvalid() || stale;
    }

    public void refresh(LongList highlights) {
        stale = false;
        if (highlights.isEmpty()) {
            close();
            return;
        }
        var byteBuffer = new ByteBufferBuilder(256);
        var bufferBuilder = new BufferBuilder(byteBuffer, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);

        for (int j = 0; j < highlights.size(); j++) {
            long highlight = highlights.getLong(j);
            var chunkPosX = ChunkUtils.longToChunkX(highlight);
            var chunkPosZ = ChunkUtils.longToChunkZ(highlight);
            var blockPosX = ChunkUtils.chunkCoordToCoord(chunkPosX);
            var blockPosZ = ChunkUtils.chunkCoordToCoord(chunkPosZ);
            var chunkSize = 16;
            float x1 = blockPosX;
            float x2 = blockPosX + chunkSize;
            float y1 = blockPosZ;
            float y2 = blockPosZ + chunkSize;
            bufferBuilder.addVertex(x1, y1, 0.0F);
            bufferBuilder.addVertex(x2, y1, 0.0F);
            bufferBuilder.addVertex(x2, y2, 0.0F);
            bufferBuilder.addVertex(x1, y2, 0.0F);
        }
        vertexBuffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        var meshData = bufferBuilder.buildOrThrow();
        vertexBuffer.bind();
        vertexBuffer.upload(meshData);
    }

    public void render() {
        if (vertexBuffer == null || vertexBuffer.isInvalid()) return;
        var shader = XaeroPlusShaders.HIGHLIGHT_SHADER;
        if (shader == null) return;
        vertexBuffer.bind();
        vertexBuffer.drawWithShader(RenderSystem.getModelViewMatrix(), RenderSystem.getProjectionMatrix(), shader);
    }

    public void markStale() {
        stale = true;
    }

    public void close() {
        if (vertexBuffer != null) {
            vertexBuffer.close();
            vertexBuffer = null;
        }
    }
}