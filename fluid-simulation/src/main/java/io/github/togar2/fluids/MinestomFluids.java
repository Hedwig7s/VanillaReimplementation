package io.github.togar2.fluids;

import net.minestom.server.ServerProcess;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.coordinate.Point;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.instance.InstanceTickEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class MinestomFluids {
    public static final Fluid WATER = new WaterFluid();
    public static final Fluid EMPTY = new EmptyFluid();

    private static final Map<Instance, Map<Long, Set<BlockVec>>> UPDATES = new ConcurrentHashMap<>();

    public static Fluid get(Block block) {
        if (block.compare(Block.WATER)) {
            return WATER;
        } else if (block.compare(Block.LAVA)) {
            return EMPTY;
        } else {
            return EMPTY;
        }
    }

    public static void tick(InstanceTickEvent event) {
        Set<BlockVec> currentUpdate = UPDATES.computeIfAbsent(event.getInstance(), i -> new ConcurrentHashMap<>())
                .get(event.getInstance().getWorldAge());
        if (currentUpdate == null) return;
        for (BlockVec blockVec : currentUpdate) {
            tick(event.getInstance(), blockVec);
        }
        UPDATES.get(event.getInstance()).remove(event.getInstance().getWorldAge());
    }

    public static void tick(Instance instance, BlockVec blockVec) {
        get(instance.getBlock(blockVec)).onTick(instance, blockVec, instance.getBlock(blockVec));
    }

    public static void scheduleTick(Instance instance, BlockVec blockVec, Block block) {
        int tickDelay = MinestomFluids.get(block).getNextTickDelay(instance, blockVec, block);
        if (tickDelay == -1) return;

        //TODO figure out a way to remove instance from map if unregistered?
        long newAge = instance.getWorldAge() + tickDelay;
        UPDATES.get(instance).computeIfAbsent(newAge, l -> new HashSet<>()).add(blockVec);
    }

    public static void init(ServerProcess process) {
        process.block().registerBlockPlacementRule(new FluidPlacementRule(Block.WATER));
        process.eventHandler().addChild(events());
    }

    public static EventNode<Event> events() {
        EventNode<Event> node = EventNode.all("fluid-events");
        node.addListener(InstanceTickEvent.class, MinestomFluids::tick);
        return node;
    }
}
