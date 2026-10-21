package fr.penumbra.client.photo;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.Direction;

import java.util.UUID;

public final class PhotoFrameRenderState extends EntityRenderState {
    public Direction direction = Direction.NORTH;
    public UUID photoId;
    public long capturedAtTick;
}
