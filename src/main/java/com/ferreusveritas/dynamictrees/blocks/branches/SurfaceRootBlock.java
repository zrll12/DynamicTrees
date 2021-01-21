package com.ferreusveritas.dynamictrees.blocks.branches;

import com.ferreusveritas.dynamictrees.api.TreeHelper;
import com.ferreusveritas.dynamictrees.init.DTRegistries;
import com.ferreusveritas.dynamictrees.util.CoordUtils;
import com.ferreusveritas.dynamictrees.util.RootConnections;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.state.BooleanProperty;
import net.minecraft.state.IntegerProperty;
import net.minecraft.state.StateContainer;
import net.minecraft.util.Direction;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.shapes.IBooleanFunction;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.world.IBlockDisplayReader;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.IWorld;
import net.minecraft.world.World;
import net.minecraftforge.common.ToolType;

import javax.annotation.Nonnull;

public class SurfaceRootBlock extends Block {
	
	public static final int RADMAX_NORMAL = 8;
	
	protected static final IntegerProperty RADIUS = IntegerProperty.create("radius", 1, RADMAX_NORMAL);

	public static final BooleanProperty GROUNDED = BooleanProperty.create("grounded");

	private final Item branchItem;

	public SurfaceRootBlock(String name, Item branchItem) {
		this(Material.WOOD, name, branchItem);
	}

	public SurfaceRootBlock(Material material, String name, Item branchItem) {
		super(Block.Properties.create(material)
				.harvestTool(ToolType.AXE)
				.harvestLevel(0)
				.hardnessAndResistance(2.5f, 1.0F)
				.sound(SoundType.WOOD));

		this.setRegistryName(name);
		this.branchItem = branchItem;
	}

	public class RootConnection {
		public RootConnections.ConnectionLevel level;
		public int radius;

		public RootConnection(RootConnections.ConnectionLevel level, int radius) {
			this.level = level;
			this.radius = radius;
		}
	}

	@Override
	public ItemStack getItem(IBlockReader worldIn, BlockPos pos, BlockState state) {
		return new ItemStack(this.branchItem);
	}

	///////////////////////////////////////////
	// BLOCKSTATES
	///////////////////////////////////////////

	@Override
	protected void fillStateContainer(StateContainer.Builder<Block, BlockState> builder) {
		builder.add(RADIUS, GROUNDED);
	}

	public int getRadius(BlockState blockState) {
		return blockState.getBlock() == this ? blockState.get(RADIUS) : 0;
	}

	public int setRadius(IWorld world, BlockPos pos, int radius, Direction originDir, int flags) {
		world.setBlockState(pos, getStateForRadius(radius), flags);
		return radius;
	}

	public BlockState getStateForRadius(int radius) {
		return getDefaultState().with(RADIUS, MathHelper.clamp(radius, 0, getMaxRadius()));
	}

	public int getMaxRadius() {
		return RADMAX_NORMAL;
	}

	public int getRadialHeight(int radius) {
		return radius * 2;
	}


	///////////////////////////////////////////
	// RENDERING
	///////////////////////////////////////////

	public RootConnections getConnectionData(@Nonnull IBlockDisplayReader world, @Nonnull BlockPos pos, @Nonnull BlockState state) {
		final RootConnections connections = new RootConnections();

		int radius = this.getRadius(world.getBlockState(pos));

		for (Direction dir : CoordUtils.HORIZONTALS) {
			final RootConnection connection = this.getSideConnectionRadius(world, pos, radius, dir);

			if (connection == null) continue;

			connections.setRadius(dir, connection.radius);
			connections.setConnectionLevel(dir, connection.level);
		}

		return connections;
	}



	///////////////////////////////////////////
	// PHYSICAL BOUNDS
	///////////////////////////////////////////

	@Override
	public VoxelShape getShape(BlockState state, IBlockReader world, BlockPos pos, ISelectionContext context) {
		boolean connectionMade = false;
		int thisRadius = getRadius(state);

		VoxelShape shape = VoxelShapes.empty();

		for (Direction dir : CoordUtils.HORIZONTALS) {
			RootConnection conn = getSideConnectionRadius(world, pos, thisRadius, dir);
			if (conn != null) {
				connectionMade = true;
				int r = MathHelper.clamp(conn.radius, 1, thisRadius);
				double radius = r / 16.0;
				double radialHeight = getRadialHeight(r) / 16.0;
				double gap = 0.5 - radius;
				AxisAlignedBB aabb = new AxisAlignedBB(-radius, 0, -radius, radius, radialHeight, radius);
				aabb = aabb.expand(dir.getXOffset() * gap, 0, dir.getZOffset() * gap).offset(0.5, 0.0, 0.5);
				shape = VoxelShapes.combine(shape, VoxelShapes.create(aabb), IBooleanFunction.OR);
			}
		}

		if(!connectionMade) {
			double radius = thisRadius / 16.0;
			double radialHeight = getRadialHeight(thisRadius) / 16.0;
			AxisAlignedBB aabb = new AxisAlignedBB(0.5 - radius, 0, 0.5 - radius, 0.5 + radius, radialHeight, 0.5 + radius);
			shape = VoxelShapes.combine(shape, VoxelShapes.create(aabb), IBooleanFunction.OR);
		}

		return shape;
	}

	protected RootConnection getSideConnectionRadius(IBlockReader blockReader, BlockPos pos, int radius, Direction side) {
		if(side.getAxis().isHorizontal()) {
			BlockPos dPos = pos.offset(side);
			BlockState blockState = blockReader.getBlockState(dPos);
			BlockState upState = blockReader.getBlockState(pos.up());
			RootConnections.ConnectionLevel level = (upState.getBlock() == Blocks.AIR && blockState.isNormalCube(blockReader, dPos)) ? RootConnections.ConnectionLevel.HIGH : (blockState.getBlock() == Blocks.AIR ? RootConnections.ConnectionLevel.LOW : RootConnections.ConnectionLevel.MID);

			if(level != RootConnections.ConnectionLevel.MID) {
				dPos = dPos.up(level.getYOffset());
				blockState = blockReader.getBlockState(dPos);
			}

			if(blockState.getBlock() instanceof SurfaceRootBlock) {
				return new RootConnection(level, ((SurfaceRootBlock)blockState.getBlock()).getRadius(blockState));
			} else
			if(level == RootConnections.ConnectionLevel.MID && TreeHelper.isBranch(blockState) && TreeHelper.getTreePart(blockState).getRadius(blockState) >= 8) {
				return new RootConnection(RootConnections.ConnectionLevel.MID, 8);
			}

		}
		return null;
	}

	@Override
	public boolean removedByPlayer(BlockState state, World world, BlockPos pos, PlayerEntity player, boolean willHarvest, FluidState fluid) {
		BlockState upstate = world.getBlockState(pos.up());

		if (upstate.getBlock() == DTRegistries.trunkShellBlock) {
			world.setBlockState(pos, upstate);
		}

		for(Direction dir : CoordUtils.HORIZONTALS) {
			BlockPos dPos = pos.offset(dir).down();
			world.getBlockState(dPos).neighborChanged(world, dPos, this, pos, false);
		}

		return super.removedByPlayer(state, world, pos, player, willHarvest, fluid);
	}

	@Override
	public void neighborChanged(BlockState state, World world, BlockPos pos, Block blockIn, BlockPos fromPos, boolean isMoving) {
		if(!canBlockStay(world, pos, state)) {
			world.removeBlock(pos, false);
		}
	}

	protected boolean canBlockStay(World world, BlockPos pos, BlockState state) {

		BlockPos below = pos.down();
		BlockState belowState = world.getBlockState(below);

		int thisRadius = getRadius(state);

		if(belowState.isNormalCube(world,below)) {//If a branch is sitting on a solid block
			for(Direction dir : CoordUtils.HORIZONTALS) {
				RootConnection conn = getSideConnectionRadius(world, pos, thisRadius, dir);
				if(conn != null && conn.radius > thisRadius) {
					return true;
				}
			}
		} else {//If the branch has no solid block under it
			boolean connections = false;
			for(Direction dir : CoordUtils.HORIZONTALS) {
				RootConnection conn = getSideConnectionRadius(world, pos, thisRadius, dir);
				if(conn != null) {
					if(conn.level == RootConnections.ConnectionLevel.MID) {
						return false;
					}
					if(conn.radius > thisRadius) {
						connections = true;
					}
				}
			}
			return connections;
		}

		return false;
	}

}
