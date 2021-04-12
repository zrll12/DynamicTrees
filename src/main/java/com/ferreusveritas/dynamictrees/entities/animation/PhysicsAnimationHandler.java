package com.ferreusveritas.dynamictrees.entities.animation;

import java.util.Random;

import com.ferreusveritas.dynamictrees.api.TreeHelper;
import com.ferreusveritas.dynamictrees.blocks.branches.BranchBlock;
import com.ferreusveritas.dynamictrees.entities.FallingTreeEntity;
import com.ferreusveritas.dynamictrees.init.DTRegistries;
import com.mojang.blaze3d.matrix.MatrixStack;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FlowingFluidBlock;
import net.minecraft.util.Direction;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.vector.Quaternion;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

public class PhysicsAnimationHandler implements IAnimationHandler {
	@Override public String getName() { return "physics"; };
	
	static class HandlerData extends DataAnimationHandler {
		float rotYaw = 0;
		float rotPit = 0;
	}
	
	HandlerData getData(FallingTreeEntity entity) {
		return entity.dataAnimationHandler instanceof HandlerData ? (HandlerData) entity.dataAnimationHandler : new HandlerData();
	}

	@Override
	public void initMotion(FallingTreeEntity entity) {
		entity.dataAnimationHandler = new HandlerData();
		final BlockPos cutPos = entity.getDestroyData().cutPos;
		
		final long seed = entity.level.random.nextLong();
		final Random random = new Random(seed ^ (((long)cutPos.getX()) << 32 | ((long)cutPos.getZ())) );
		final float mass = entity.getDestroyData().woodVolume.getVolume();
		final float inertialMass = MathHelper.clamp(mass, 1, 3);
		entity.setDeltaMovement(entity.getDeltaMovement().x / inertialMass,
				entity.getDeltaMovement().y / inertialMass, entity.getDeltaMovement().z / inertialMass);
		
		getData(entity).rotPit = (random.nextFloat() - 0.5f) * 4 / inertialMass;
		getData(entity).rotYaw = (random.nextFloat() - 0.5f) * 4 / inertialMass;

		final Direction cutDir = entity.getDestroyData().cutDir;
		entity.push(cutDir.getOpposite().getStepX() * 0.1,cutDir.getOpposite().getStepX() * 0.1,
				cutDir.getOpposite().getStepX() * 0.1);
		
		FallingTreeEntity.standardDropLeavesPayLoad(entity);//Seeds and stuff fall out of the tree before it falls over
	}
	
	@Override
	public void handleMotion(FallingTreeEntity entity) {
		
		if (entity.landed) {
			return;
		}

		entity.setDeltaMovement(entity.getDeltaMovement().x, entity.getDeltaMovement().y - AnimationConstants.TREE_GRAVITY, entity.getDeltaMovement().z);

		//Create drag in air
		entity.setDeltaMovement(entity.getDeltaMovement().x * 0.98f, entity.getDeltaMovement().y * 0.98f, entity.getDeltaMovement().z * 0.98f);
		getData(entity).rotYaw *= 0.98f;
		getData(entity).rotPit *= 0.98f;

		//Apply motion

		entity.setPos(entity.getX() + entity.getDeltaMovement().x, entity.getY() + entity.getDeltaMovement().y, entity.getZ() + entity.getDeltaMovement().z);
		entity.xRot = MathHelper.wrapDegrees(entity.xRot + getData(entity).rotPit);
		entity.yRot = MathHelper.wrapDegrees(entity.yRot + getData(entity).rotYaw);

		int radius = 8;
		BlockState state = entity.getDestroyData().getBranchBlockState(0);
		if(TreeHelper.isBranch(state)) {
			radius = ((BranchBlock)state.getBlock()).getRadius(state);
		}
		World world = entity.level;
		AxisAlignedBB fallBox = new AxisAlignedBB(entity.getX() - radius, entity.getY(), entity.getZ() - radius, entity.getX() + radius, entity.getY() + 1.0, entity.getZ() + radius);
		BlockPos pos = new BlockPos(entity.getX(), entity.getY(), entity.getZ());
		BlockState collState = world.getBlockState(pos);

		if(!TreeHelper.isLeaves(collState) && !TreeHelper.isBranch(collState) && collState.getBlock() != DTRegistries.TRUNK_SHELL) {
			if(collState.getBlock() instanceof FlowingFluidBlock) {
				//Undo the gravity
				entity.setDeltaMovement(entity.getDeltaMovement().x, entity.getDeltaMovement().y + (AnimationConstants.TREE_GRAVITY), entity.getDeltaMovement().z);
				//Create drag in liquid
				entity.setDeltaMovement(entity.getDeltaMovement().x * 0.8f, entity.getDeltaMovement().y * 0.8f, entity.getDeltaMovement().z * 0.8f);
				getData(entity).rotYaw *= 0.8f;
				getData(entity).rotPit *= 0.8f;
				//Add a little buoyancy
				entity.setDeltaMovement(entity.getDeltaMovement().x, entity.getDeltaMovement().y + 0.01, entity.getDeltaMovement().z);
				entity.onFire = false;
			} else {
				VoxelShape shape = collState.getBlockSupportShape(world, pos);
				AxisAlignedBB collBox = new AxisAlignedBB(0,0,0,0,0,0);
				if (!shape.isEmpty()){
					collBox = collState.getBlockSupportShape(world, pos).bounds();
				}

				collBox = collBox.move(pos);
				if(fallBox.intersects(collBox)) {
					entity.setDeltaMovement(entity.getDeltaMovement().x, 0, entity.getDeltaMovement().z);
					entity.setPos(entity.getX(), collBox.maxY, entity.getZ());
					entity.yo = entity.getY();
					entity.landed = true;
					entity.setOnGround(true);
					if(entity.onFire) {
						if(entity.level.isEmptyBlock(pos.above())) {
							entity.level.setBlockAndUpdate(pos.above(), Blocks.FIRE.defaultBlockState());
						}
					}
				}
			}
		}
		
	}
	
	@Override
	public void dropPayload(FallingTreeEntity entity) {
		World world = entity.level;
		entity.getPayload().forEach(i -> Block.popResource(world, new BlockPos(entity.getX(), entity.getY(), entity.getZ()), i));
		entity.getDestroyData().leavesDrops.forEach(bis -> Block.popResource(world, entity.getDestroyData().cutPos.offset(bis.pos), bis.stack));
	}
	
	public boolean shouldDie(FallingTreeEntity entity) {
		boolean dead = entity.landed || entity.tickCount > 120;
		
		if(dead) {
			entity.cleanupRootyDirt();
		}
		
		return dead;
	}
	
	@Override
	@OnlyIn(Dist.CLIENT)
	public void renderTransform(FallingTreeEntity entity, float entityYaw, float partialTicks, MatrixStack matrixStack) {
		float yaw = MathHelper.wrapDegrees(com.ferreusveritas.dynamictrees.util.MathHelper.angleDegreesInterpolate(entity.yRotO, entity.yRot, partialTicks));
		float pit = MathHelper.wrapDegrees(com.ferreusveritas.dynamictrees.util.MathHelper.angleDegreesInterpolate(entity.xRotO, entity.xRot, partialTicks));

		Vector3d mc = entity.getMassCenter();
		matrixStack.translate(mc.x, mc.y, mc.z);
		matrixStack.mulPose(new Quaternion(new Vector3f(0, 1, 0), -yaw, true));
		matrixStack.mulPose(new Quaternion(new Vector3f(1, 0, 0), pit, true));
		matrixStack.translate(-mc.x - 0.5, -mc.y, -mc.z - 0.5);

	}

	@Override
	@OnlyIn(Dist.CLIENT)
	public boolean shouldRender(FallingTreeEntity entity) {
		return true;
	}
}
