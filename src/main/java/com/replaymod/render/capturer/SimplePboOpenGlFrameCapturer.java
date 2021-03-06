package com.replaymod.render.capturer;

import com.replaymod.render.frame.OpenGlFrame;
import com.replaymod.render.utils.ByteBufferPool;
import com.replaymod.render.utils.PixelBufferObject;
import de.johni0702.minecraft.gui.utils.lwjgl.ReadableDimension;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.io.IOException;
import java.nio.ByteBuffer;

public class SimplePboOpenGlFrameCapturer extends OpenGlFrameCapturer<OpenGlFrame, CaptureData> {
    private final int bufferSize;
    private PixelBufferObject pbo, otherPBO;

    public SimplePboOpenGlFrameCapturer(WorldRenderer worldRenderer, RenderInfo renderInfo) {
        super(worldRenderer, renderInfo);

        ReadableDimension size = renderInfo.getFrameSize();
        bufferSize = size.getHeight() * size.getWidth() * 4;
        pbo = new PixelBufferObject(bufferSize, PixelBufferObject.Usage.READ);
        otherPBO = new PixelBufferObject(bufferSize, PixelBufferObject.Usage.READ);
    }

    private void swapPBOs() {
        PixelBufferObject old = pbo;
        pbo = otherPBO;
        otherPBO = old;
    }

    @Override
    public boolean isDone() {
        return framesDone >= renderInfo.getTotalFrames() + 2;
    }

    @Override
    public OpenGlFrame process() {
        OpenGlFrame frame = null;

        if (framesDone > 1) {
            // Read pbo to memory
            pbo.bind();
            ByteBuffer pboBuffer = pbo.mapReadOnly();
            ByteBuffer buffer = ByteBufferPool.allocate(bufferSize);
            buffer.put(pboBuffer);
            buffer.rewind();
            pbo.unmap();
            pbo.unbind();
            frame = new OpenGlFrame(framesDone - 2, frameSize, buffer);
        }

        if (framesDone < renderInfo.getTotalFrames()) {
            // Then fill it again
            renderFrame(framesDone, renderInfo.updateForNextFrame());
        }

        framesDone++;
        swapPBOs();
        return frame;
    }

    @Override
    protected OpenGlFrame captureFrame(int frameId, CaptureData data) {
        pbo.bind();

        frameBuffer().beginWrite(true);
        GL11.glReadPixels(0, 0, getFrameWidth(), getFrameHeight(), GL12.GL_BGRA, GL11.GL_UNSIGNED_BYTE, 0);
        frameBuffer().endWrite();

        pbo.unbind();
        return null;
    }

    @Override
    public void close() throws IOException {
        super.close();
        pbo.delete();
        otherPBO.delete();
    }
}
