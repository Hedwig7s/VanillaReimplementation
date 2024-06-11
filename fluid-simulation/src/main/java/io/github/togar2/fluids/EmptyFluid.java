package io.github.togar2.fluids;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.Material;
import net.minestom.server.utils.Direction;

public class EmptyFluid extends Fluid {

    public EmptyFluid() {
        super(Block.AIR, Material.BUCKET);
    }

    @Override
    protected boolean canBeReplacedWith(Instance instance, BlockVec blockVec, Fluid other, Direction direction) {
        return true;
    }

    @Override
    public int getNextTickDelay(Instance instance, BlockVec blockVec, Block block) {
        return -1;
    }

    @Override
    protected boolean isEmpty() {
        return true;
    }

    @Override
    protected double getBlastResistance() {
        return 0;
    }

    @Override
    public double getHeight(Block block, Instance instance, BlockVec blockVec) {
        return 0;
    }

    @Override
    public double getHeight(Block block) {
        return 0;
    }
}
