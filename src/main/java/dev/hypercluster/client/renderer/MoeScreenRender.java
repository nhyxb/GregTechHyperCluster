package dev.hypercluster.client.renderer;

import com.gregtechceu.gtceu.client.renderer.machine.DynamicRender;
import com.gregtechceu.gtceu.client.renderer.machine.DynamicRenderType;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.mojang.serialization.Codec;
import dev.hypercluster.HyperCluster;
import dev.hypercluster.common.machine.model.MoeRouterRackMachine;
import org.joml.Matrix4f;

/**
 * 0.6.3 · the floating {@code 6×6} whale-maid display above the MoE routing rack.
 *
 * <p>
 * The rack stays a {@code 5×5×5} multiblock — this renderer adds no blocks, so the existing
 * {@code TopologyTests} and every saved world keep working untouched. The screen is a pure
 * client-side quad {@value #SCREEN_BLOCKS} blocks wide and tall, centred on the cabinet's
 * footprint and floating just above the top frame.
 *
 * <p>
 * <b>Pixel density.</b> The texture is {@code 96×96}. Drawn across a {@code 6×6} block area at
 * 16 texels per block that is exactly one texel per pixel: the artwork reproduces 1:1.
 *
 * <p>
 * <b>Fullbright.</b> The quad is submitted with {@link LightTexture#FULL_BRIGHT}, so the panel
 * stays readable in an unlit machine hall and at night, like a real LED wall. It is drawn with
 * {@link RenderType#entityCutoutNoCull} so it is visible from both sides and its transparent
 * margin does not paint a black box.
 *
 * <p>
 * <b>Facing.</b> The screen plane follows the controller's horizontal facing, so rotating the
 * cabinet rotates the panel with it. Only the four horizontal directions occur: the MoE rack is
 * declared {@code RotationState.NON_Y_AXIS}.
 *
 * <p>
 * <b>No Z-fighting.</b> The panel floats {@value #SCREEN_LIFT} blocks above the roof, in open
 * air, and is the only geometry in its plane — nothing is coplanar with it.
 */
public class MoeScreenRender extends DynamicRender<MoeRouterRackMachine, MoeScreenRender> {

    /** The screen texture, 96×96 px. */
    public static final ResourceLocation TEXTURE = HyperCluster
            .id("textures/screens/whale_maid_screen_96.png");

    /** Screen width/height in blocks: 96 px / 16 px-per-block. */
    public static final int SCREEN_BLOCKS = 6;
    /** Blocks between the cabinet roof and the screen's bottom edge. */
    public static final float SCREEN_LIFT = 0.75F;

    public static final Codec<MoeScreenRender> CODEC = Codec.unit(MoeScreenRender::new);
    public static final DynamicRenderType<MoeRouterRackMachine, MoeScreenRender> TYPE = new DynamicRenderType<>(
            CODEC);

    @Override
    public DynamicRenderType<MoeRouterRackMachine, MoeScreenRender> getType() {
        return TYPE;
    }

    @Override
    public void render(MoeRouterRackMachine machine, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        if (!machine.isScreenLit()) {
            return;
        }
        Direction front = machine.getFrontFacing();
        if (front.getAxis() == Direction.Axis.Y) {
            front = Direction.NORTH;
        }

        poseStack.pushPose();
        // Move to the controller's block centre.
        poseStack.translate(0.5, 0.5, 0.5);
        // Rotate so that the quad's local +Z points along the controller's front face and local
        // +X runs to the viewer's right when looking at that face.
        poseStack.mulPose(Axis.YP.rotationDegrees(yRotation(front)));
        // Rise above the cabinet roof, then leave SCREEN_LIFT of clearance before the panel.
        poseStack.translate(0.0, ROOF_ABOVE_CONTROLLER + SCREEN_LIFT, 0.0);

        float half = SCREEN_BLOCKS / 2.0F;
        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        // Centred on the cabinet: local X spans [-half, +half], Y spans [0, SCREEN_BLOCKS].
        vertex(consumer, matrix, -half, 0.0F, 0.0F, 1.0F);
        vertex(consumer, matrix, half, 0.0F, 1.0F, 1.0F);
        vertex(consumer, matrix, half, SCREEN_BLOCKS, 1.0F, 0.0F);
        vertex(consumer, matrix, -half, SCREEN_BLOCKS, 0.0F, 0.0F);
        poseStack.popPose();
    }

    /**
     * Blocks from the controller block's centre up to the cabinet roof's top face.
     *
     * <p>
     * In the {@code 5×5×5} pattern the controller sits in the second layer ({@code y=1}, so the
     * block spans world {@code y=1..2} and its centre is {@code y=1.5}) while the roof occupies
     * {@code y=4..5}. The top face is therefore {@code 5.0 - 1.5 = 3.5} blocks above the centre.
     */
    private static final float ROOF_ABOVE_CONTROLLER = 3.5F;

    /** Y rotation that maps local +Z onto {@code front}. */
    private static float yRotation(Direction front) {
        return switch (front) {
            case SOUTH -> 0.0F;
            case WEST -> 90.0F;
            case NORTH -> 180.0F;
            case EAST -> 270.0F;
            default -> 0.0F;
        };
    }

    /** Emits one corner: fullbright, no overlay, outward normal along local +Z. */
    private static void vertex(VertexConsumer consumer, Matrix4f matrix, float x, float y, float u, float v) {
        consumer.vertex(matrix, x, y, 0.0F)
                .color(255, 255, 255, 255)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(0.0F, 0.0F, 1.0F)
                .endVertex();
    }

    @Override
    public boolean shouldRenderOffScreen(MoeRouterRackMachine machine) {
        // The panel is 6 blocks wide and floats above the controller's own chunk section, so it
        // must not be culled together with the controller block.
        return true;
    }

    @Override
    public boolean shouldRender(MoeRouterRackMachine machine, Vec3 cameraPos) {
        return machine.isFormed();
    }

    @Override
    public int getViewDistance() {
        return 128;
    }

    /**
     * Covers the whole floating panel. Without this the frustum test would only know about the
     * controller block, and the screen would pop out of view when the player looks at it from
     * the side.
     */
    @Override
    public AABB getRenderBoundingBox(MoeRouterRackMachine machine) {
        BlockPos pos = machine.getPos();
        int reach = SCREEN_BLOCKS + 1;
        return new AABB(pos.getX() - reach, pos.getY() - 1, pos.getZ() - reach,
                pos.getX() + reach + 1, pos.getY() + SCREEN_BLOCKS + 8, pos.getZ() + reach + 1);
    }
}
