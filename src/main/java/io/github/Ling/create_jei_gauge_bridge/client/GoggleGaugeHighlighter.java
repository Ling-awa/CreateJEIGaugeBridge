package io.github.Ling.create_jei_gauge_bridge.client;

import com.simibubi.create.content.equipment.goggles.GogglesItem;
import com.simibubi.create.content.fluids.transfer.GenericItemEmptying;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock.PanelSlot;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelConnection;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelConnectionHandler;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;
import io.github.Ling.create_jei_gauge_bridge.compat.fluidlogistics.FluidLogisticsCompat;
import io.github.Ling.create_jei_gauge_bridge.config.ClientConfig;
import net.createmod.catnip.math.VecHelper;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Engineer-goggles overlay for factory gauges.
 *
 * <p>While goggles are worn:</p>
 * <ul>
 *   <li>looking at a gauge highlights it and every gauge it is connected to, tracing the same bent
 *       path Create's own arrows use;</li>
 *   <li>holding an item highlights every nearby gauge monitoring that item (and, with Fluid
 *       Logistics installed, every fluid gauge monitoring the held container's fluid).</li>
 * </ul>
 *
 * <p>Colours follow the mechanical arm's own palette: the gauge itself and held-item matches are
 * green, upstream connections (what it takes from) use the extract blue, downstream connections
 * (what it feeds) use the deposit gold.</p>
 */
public final class GoggleGaugeHighlighter {

    /** Soft green, the same one Create uses for "connection valid". */
    public static final int COLOR_SELF = 0x9EFF7F;
    /** {@code ArmInteractionPoint.Mode.TAKE} colour: what this gauge consumes. */
    public static final int COLOR_EXTRACT = 0x7FCDE0;
    /** {@code ArmInteractionPoint.Mode.DEPOSIT} colour: what this gauge feeds. */
    public static final int COLOR_DEPOSIT = 0xDDC166;

    private static final float LINE_WIDTH = 1f / 16f;

    /** How far around the player held-item matches are searched. */
    private static final int SEARCH_RADIUS = 16;
    /** Re-scan the nearby panels every this many ticks; the boxes themselves refresh every tick. */
    private static final int HELD_SCAN_INTERVAL = 10;
    /** Guard against pathological paths turning into thousands of line segments. */
    private static final int MAX_PATH_SEGMENTS = 96;
    /** Small gap kept between a line end and the highlighted panel's box. */
    private static final double BOX_GAP = 0.01;

    private record Line(Vec3 from, Vec3 to, int color) {
    }

    private static final Map<Object, AABB> LOOK_BOXES = new LinkedHashMap<>();
    private static final Map<Object, Line> LOOK_LINES = new LinkedHashMap<>();
    private static final Map<Object, AABB> HELD_BOXES = new LinkedHashMap<>();
    private static final Set<Object> SHOWN = new HashSet<>();

    private static int heldScanTimer;

    /** Pinned panel, kept highlighted for the configured duration after a sneak right-click. */
    private static @Nullable BlockPos pinnedPos;
    private static int pinnedSlot = -1;
    private static boolean pinnedTemplate;
    private static int pinnedTicks;

    private GoggleGaugeHighlighter() {
    }

    /** Pins a panel's highlight for the configured duration, so distant links can be inspected. */
    public static void pin(BlockPos pos, int slot, boolean template) {
        pinnedPos = pos.immutable();
        pinnedSlot = slot;
        pinnedTemplate = template;
        pinnedTicks = ClientConfig.pinTicks();
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || minecraft.level == null || minecraft.screen != null) {
            clear();
            return;
        }
        if (!GogglesItem.isWearingGoggles(player)) {
            clear();
            return;
        }

        LOOK_BOXES.clear();
        LOOK_LINES.clear();
        if (pinnedTicks > 0) {
            pinnedTicks--;
            renderPinned(minecraft);
        }

        boolean sneaking = player.isShiftKeyDown();
        if (ClientConfig.isVisible(ClientConfig.connectionMode(), sneaking)) {
            updateLookAt(minecraft);
        }

        boolean heldVisible = ClientConfig.isVisible(ClientConfig.heldItemMode(), sneaking);
        if (!heldVisible) {
            HELD_BOXES.clear();
        }
        if (--heldScanTimer <= 0) {
            heldScanTimer = HELD_SCAN_INTERVAL;
            if (heldVisible) {
                updateHeldItem(minecraft, player);
            }
        }

        draw(minecraft);
    }

    // --- look-at ------------------------------------------------------------------------------

    private static void renderPinned(Minecraft minecraft) {
        if (pinnedPos == null || pinnedSlot < 0) {
            return;
        }
        // Pinning is an explicit request, but it is still a connection overlay: honour "off".
        if (ClientConfig.connectionMode() == ClientConfig.HighlightMode.OFF) {
            return;
        }
        if (pinnedTemplate) {
            if (ModList.get().isLoaded("createimp")) {
                io.github.Ling.create_jei_gauge_bridge.compat.createimp.TemplateHighlights
                        .highlight(minecraft, pinnedPos, pinnedSlot);
            }
            return;
        }
        if (!(minecraft.level.getBlockState(pinnedPos).getBlock() instanceof FactoryPanelBlock)) {
            return;
        }
        PanelSlot[] slots = PanelSlot.values();
        if (pinnedSlot >= slots.length) {
            return;
        }
        FactoryPanelBehaviour panel = FactoryPanelBehaviour.at(minecraft.level,
                new FactoryPanelPosition(pinnedPos, slots[pinnedSlot]));
        if (panel != null) {
            highlightFactoryPanel(minecraft, panel, minecraft.level.getBlockState(pinnedPos));
        }
    }

    private static void updateLookAt(Minecraft minecraft) {
        HitResult hitResult = minecraft.hitResult;
        if (!(hitResult instanceof BlockHitResult blockHit) || hitResult.getType() != HitResult.Type.BLOCK) {
            return;
        }
        BlockPos pos = blockHit.getBlockPos();
        BlockState state = minecraft.level.getBlockState(pos);

        if (state.getBlock() instanceof FactoryPanelBlock) {
            PanelSlot slot = FactoryPanelBlock.getTargetedSlot(pos, state, blockHit.getLocation());
            FactoryPanelBehaviour panel = FactoryPanelBehaviour.at(minecraft.level,
                    new FactoryPanelPosition(pos, slot));
            if (panel == null) {
                return;
            }
            highlightFactoryPanel(minecraft, panel, state);
            return;
        }

        if (ModList.get().isLoaded("createimp")) {
            io.github.Ling.create_jei_gauge_bridge.compat.createimp.TemplateHighlights
                    .lookAt(minecraft, blockHit, state, pos);
        }
    }

    private static void highlightFactoryPanel(Minecraft minecraft, FactoryPanelBehaviour panel,
                                              BlockState selfState) {
        FactoryPanelPosition self = panel.getPanelPosition();
        AABB selfBox = FactoryPanelConnectionHandler.getBB(selfState, self);
        addLookBox(boxKey(self.pos(), self.slot()), selfBox);
        Vec3 selfCenter = selfBox.getCenter();

        for (FactoryPanelConnection connection : panel.targetedBy.values()) {
            BlockState sourceState = minecraft.level.getBlockState(connection.from.pos());
            AABB sourceBox = FactoryPanelConnectionHandler.getBB(sourceState, connection.from);
            addLookBox(boxKey(connection.from.pos(), connection.from.slot()), sourceBox);
            addFactoryPath("in:" + keySuffix(connection.from.pos(), connection.from.slot()),
                    selfCenter, sourceBox.getCenter(),
                    connection.getPath(minecraft.level, selfState, self), selfState,
                    COLOR_EXTRACT, selfBox, sourceBox);
        }

        for (FactoryPanelPosition target : panel.targeting) {
            FactoryPanelBehaviour downstream = FactoryPanelBehaviour.at(minecraft.level, target);
            if (downstream == null) {
                continue;
            }
            BlockState downstreamState = downstream.blockEntity.getBlockState();
            AABB downstreamBox = FactoryPanelConnectionHandler.getBB(downstreamState, target);
            addLookBox(boxKey(target.pos(), target.slot()), downstreamBox);
            FactoryPanelConnection back = downstream.targetedBy.get(self);
            if (back != null) {
                addFactoryPath("out:" + keySuffix(target.pos(), target.slot()),
                        downstreamBox.getCenter(), selfCenter,
                        back.getPath(minecraft.level, downstreamState, target), downstreamState,
                        COLOR_DEPOSIT, downstreamBox, selfBox);
            } else {
                addClippedLine("out-straight:" + keySuffix(target.pos(), target.slot()),
                        downstreamBox.getCenter(), selfCenter, COLOR_DEPOSIT, downstreamBox, selfBox);
            }
        }
    }

    private static void addFactoryPath(String base, Vec3 start, Vec3 end, List<Direction> path,
                                       BlockState state, int color, AABB startBox, AABB endBox) {
        float xRot = (float) Math.toDegrees(FactoryPanelBlock.getXRot(state));
        float yRot = (float) Math.toDegrees(FactoryPanelBlock.getYRot(state));
        addPolyline(base, start, end, path, xRot, yRot, color, startBox, endBox);
    }

    /**
     * Turns Create's local-space path (half-block steps) into world-space line segments.
     *
     * <p>{@code FactoryPanelConnection#calculatePathDiff} converts world to local with
     * {@code rot(-yRot, Y), rot(-xRot-90, X), rot(-180, Y)}, so the inverse is applied here.</p>
     */
    public static void addPolyline(String base, Vec3 start, Vec3 end, List<Direction> path,
                                   float xRotDegrees, float yRotDegrees, int color,
                                   @Nullable AABB startBox, @Nullable AABB endBox) {
        if (path.size() > MAX_PATH_SEGMENTS) {
            addClippedLine(base + ":straight", start, end, color, startBox, endBox);
            return;
        }

        List<Vec3> points = new ArrayList<>();
        points.add(start);
        Vec3 cursor = start;
        for (Direction direction : path) {
            cursor = cursor.add(localToWorldDirection(direction, xRotDegrees, yRotDegrees).scale(0.5));
            points.add(cursor);
        }
        points.add(end);

        // Stop at the highlight boxes instead of running into the gauge centres.
        points.set(0, exitBox(startBox, start, points.get(1)));
        int last = points.size() - 1;
        points.set(last, exitBox(endBox, end, points.get(last - 1)));

        for (int i = 0; i < points.size() - 1; i++) {
            addLookLine(base + ":seg:" + i, points.get(i), points.get(i + 1), color);
        }
    }

    static Vec3 localToWorldDirection(Direction direction, float xRotDegrees, float yRotDegrees) {
        Vec3 vector = new Vec3(direction.getStepX(), direction.getStepY(), direction.getStepZ());
        vector = VecHelper.rotate(vector, 180, Direction.Axis.Y);
        vector = VecHelper.rotate(vector, xRotDegrees + 90, Direction.Axis.X);
        vector = VecHelper.rotate(vector, yRotDegrees, Direction.Axis.Y);
        return vector;
    }

    /** The point where a ray leaving {@code inside} towards {@code target} leaves the box. */
    static Vec3 exitBox(@Nullable AABB box, Vec3 inside, Vec3 target) {
        if (box == null) {
            return inside;
        }
        Vec3 direction = target.subtract(inside);
        double length = direction.length();
        if (length < 1.0E-6) {
            return inside;
        }
        Vec3 normal = direction.scale(1.0 / length);
        double distance = Double.MAX_VALUE;
        distance = planeDistance(distance, normal.x, box.minX, box.maxX, inside.x);
        distance = planeDistance(distance, normal.y, box.minY, box.maxY, inside.y);
        distance = planeDistance(distance, normal.z, box.minZ, box.maxZ, inside.z);
        if (distance == Double.MAX_VALUE) {
            return inside;
        }
        return inside.add(normal.scale(Math.min(distance, length) + BOX_GAP));
    }

    private static double planeDistance(double current, double direction, double min, double max,
                                        double origin) {
        if (Math.abs(direction) < 1.0E-6) {
            return current;
        }
        double plane = direction > 0 ? max : min;
        double candidate = (plane - origin) / direction;
        return candidate > 0 && candidate < current ? candidate : current;
    }

    public static void addClippedLine(String base, Vec3 start, Vec3 end, int color,
                               @Nullable AABB startBox, @Nullable AABB endBox) {
        addLookLine(base, exitBox(startBox, start, end), exitBox(endBox, end, start), color);
    }

    // --- held item ----------------------------------------------------------------------------

    private static void updateHeldItem(Minecraft minecraft, Player player) {
        HELD_BOXES.clear();

        ItemStack mainHand = player.getMainHandItem();
        final ItemStack held = mainHand.isEmpty() ? player.getOffhandItem() : mainHand;
        if (held.isEmpty()) {
            return;
        }

        final FluidStack heldFluid = fluidOf(minecraft.level, held);
        forEachNearbyPanel(minecraft.level, player.blockPosition(), blockEntity -> {
            if (blockEntity instanceof FactoryPanelBlockEntity factoryBE) {
                for (FactoryPanelBehaviour panel : factoryBE.panels.values()) {
                    if (!panel.active || !matchesHeld(panel, held, heldFluid)) {
                        continue;
                    }
                    BlockState state = panel.blockEntity.getBlockState();
                    HELD_BOXES.put(boxKey(panel.getPos(), panel.getPanelPosition().slot()),
                            FactoryPanelConnectionHandler.getBB(state, panel.getPanelPosition()));
                }
            } else if (ModList.get().isLoaded("createimp")) {
                io.github.Ling.create_jei_gauge_bridge.compat.createimp.TemplateHighlights
                        .collectHeld(blockEntity, held, heldFluid);
            }
        });
    }

    private static boolean matchesHeld(FactoryPanelBehaviour panel, ItemStack held, FluidStack heldFluid) {
        if (ModList.get().isLoaded("fluidlogistics") && FluidLogisticsCompat.isFluidPanel(panel)) {
            if (heldFluid.isEmpty()) {
                return false;
            }
            FluidStack panelFluid = FluidLogisticsCompat.fluidOf(panel);
            return !panelFluid.isEmpty() && panelFluid.getFluid() == heldFluid.getFluid();
        }
        ItemStack filter = panel.getFilter();
        if (ModList.get().isLoaded("extra_gauges")
                && io.github.Ling.create_jei_gauge_bridge.compat.extragauges.ExtraGaugesCompat
                .filterAccepts(panel, held)) {
            // A filter gauge monitors every item its filter accepts, not just the current one.
            return true;
        }
        if (filter.isEmpty()) {
            return false;
        }
        // Exact match only: sharing a broad tag (c:tools) made an axe highlight a pickaxe gauge.
        return ItemStack.isSameItemSameComponents(filter, held);
    }

    /** The fluid a held stack carries, either through Create's containers or a Fluid Logistics key. */
    private static FluidStack fluidOf(Level level, ItemStack stack) {
        try {
            var emptied = GenericItemEmptying.emptyItem(level, stack, true);
            if (emptied != null && emptied.getFirst() != null && !emptied.getFirst().isEmpty()) {
                return emptied.getFirst();
            }
        } catch (RuntimeException ignored) {
            // Not a container Create knows about; fall through to the Fluid Logistics key check.
        }
        if (ModList.get().isLoaded("fluidlogistics")) {
            try {
                FluidStack direct = FluidLogisticsCompat.fluidOfStack(stack);
                if (!direct.isEmpty()) {
                    return direct;
                }
            } catch (RuntimeException ignored) {
                // Not a fluid key either.
            }
        }
        return FluidStack.EMPTY;
    }

    // --- scanning -----------------------------------------------------------------------------

    private static void forEachNearbyPanel(Level level, BlockPos center, Consumer<BlockEntity> consumer) {
        var centerChunk = new net.minecraft.world.level.ChunkPos(center);
        int chunkRadius = (SEARCH_RADIUS >> 4) + 1;
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                ChunkAccess chunk = level.getChunk(centerChunk.x + dx, centerChunk.z + dz);
                if (!(chunk instanceof LevelChunk levelChunk)) {
                    continue;
                }
                for (BlockEntity blockEntity : levelChunk.getBlockEntities().values()) {
                    if (blockEntity.getBlockPos().closerThan(center, SEARCH_RADIUS)) {
                        consumer.accept(blockEntity);
                    }
                }
            }
        }
    }

    // --- drawing ------------------------------------------------------------------------------

    private static void draw(Minecraft minecraft) {
        if (minecraft.level == null) {
            return;
        }
        Outliner outliner = Outliner.getInstance();
        Set<Object> current = new HashSet<>();

        for (Map.Entry<Object, AABB> entry : LOOK_BOXES.entrySet()) {
            outliner.showAABB(entry.getKey(), entry.getValue())
                    .colored(COLOR_SELF)
                    .lineWidth(LINE_WIDTH);
            current.add(entry.getKey());
        }
        for (Map.Entry<Object, AABB> entry : HELD_BOXES.entrySet()) {
            outliner.showAABB(entry.getKey(), entry.getValue())
                    .colored(COLOR_SELF)
                    .lineWidth(LINE_WIDTH);
            current.add(entry.getKey());
        }
        for (Map.Entry<Object, Line> entry : LOOK_LINES.entrySet()) {
            Line line = entry.getValue();
            List<double[]> spans = visibleSpans(line.from(), line.to(), allBoxes());
            for (int i = 0; i < spans.size(); i++) {
                double[] span = spans.get(i);
                Vec3 from = line.from().lerp(line.to(), span[0]);
                Vec3 to = line.from().lerp(line.to(), span[1]);
                if (from.distanceToSqr(to) < 1.0E-6) {
                    continue;
                }
                Object key = entry.getKey() + ":visible:" + i;
                outliner.showLine(key, from, to)
                        .colored(line.color())
                        .lineWidth(LINE_WIDTH);
                current.add(key);
            }
        }

        for (Object previous : SHOWN) {
            if (!current.contains(previous)) {
                outliner.remove(previous);
            }
        }
        SHOWN.clear();
        SHOWN.addAll(current);
    }

    private static List<AABB> allBoxes() {
        List<AABB> boxes = new ArrayList<>(LOOK_BOXES.values());
        boxes.addAll(HELD_BOXES.values());
        return boxes;
    }

    /**
     * Splits a segment into the parts that are outside every highlighted panel box, so a path that
     * passes through another gauge is interrupted there instead of being drawn on top of it.
     */
    private static List<double[]> visibleSpans(Vec3 from, Vec3 to, List<AABB> boxes) {
        List<double[]> blocked = new ArrayList<>();
        for (AABB box : boxes) {
            double[] span = intersect(box, from, to);
            if (span != null) {
                blocked.add(span);
            }
        }
        if (blocked.isEmpty()) {
            return List.of(new double[]{0.0, 1.0});
        }
        blocked.sort(java.util.Comparator.comparingDouble(span -> span[0]));

        List<double[]> visible = new ArrayList<>();
        double cursor = 0;
        for (double[] span : blocked) {
            if (span[0] > cursor) {
                visible.add(new double[]{cursor, Math.min(span[0], 1.0)});
            }
            cursor = Math.max(cursor, span[1]);
            if (cursor >= 1.0) {
                break;
            }
        }
        if (cursor < 1.0) {
            visible.add(new double[]{cursor, 1.0});
        }
        return visible;
    }

    /** @return the {@code [tMin, tMax]} slice of the segment inside the box, or {@code null}. */
    private static double[] intersect(AABB box, Vec3 from, Vec3 to) {
        double tMin = 0;
        double tMax = 1;
        double[] mins = {box.minX, box.minY, box.minZ};
        double[] maxs = {box.maxX, box.maxY, box.maxZ};
        double[] starts = {from.x, from.y, from.z};
        double[] deltas = {to.x - from.x, to.y - from.y, to.z - from.z};

        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(deltas[axis]) < 1.0E-9) {
                if (starts[axis] < mins[axis] || starts[axis] > maxs[axis]) {
                    return null;
                }
                continue;
            }
            double t1 = (mins[axis] - starts[axis]) / deltas[axis];
            double t2 = (maxs[axis] - starts[axis]) / deltas[axis];
            if (t1 > t2) {
                double swap = t1;
                t1 = t2;
                t2 = swap;
            }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
            if (tMin > tMax) {
                return null;
            }
        }
        return new double[]{tMin, tMax};
    }

    private static void clear() {
        if (SHOWN.isEmpty()) {
            return;
        }
        Outliner outliner = Outliner.getInstance();
        for (Object key : SHOWN) {
            outliner.remove(key);
        }
        SHOWN.clear();
        LOOK_BOXES.clear();
        LOOK_LINES.clear();
        HELD_BOXES.clear();
    }

    // --- keys and hooks used by the Create: Improve compat layer ------------------------------

    private static String keySuffix(BlockPos pos, PanelSlot slot) {
        return pos.asLong() + ":" + slot.ordinal();
    }

    private static Object boxKey(BlockPos pos, PanelSlot slot) {
        return "create_jei_gauge_bridge:box:" + keySuffix(pos, slot);
    }

    public static void addLookBox(Object key, AABB box) {
        LOOK_BOXES.put(key, box);
    }

    public static void addLookLine(Object key, Vec3 from, Vec3 to, int color) {
        LOOK_LINES.put(key, new Line(from, to, color));
    }

    public static void addHeldBox(Object key, AABB box) {
        HELD_BOXES.put(key, box);
    }

    public static Object boxKey(String namespace, BlockPos pos, int slotOrdinal) {
        return "create_jei_gauge_bridge:" + namespace + ":" + pos.asLong() + ":" + slotOrdinal;
    }
}
