package com.ferreusveritas.dynamictrees.potion;

import com.ferreusveritas.dynamictrees.api.TreeHelper;
import com.ferreusveritas.dynamictrees.api.network.MapSignal;
import com.ferreusveritas.dynamictrees.api.substances.ISubstanceEffect;
import com.ferreusveritas.dynamictrees.blocks.BlockRootyDirt;
import com.ferreusveritas.dynamictrees.inspectors.NodeTwinkle;

import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class SubstanceFertilize implements ISubstanceEffect {

	int amount = 1;
	
	@Override
	public boolean apply(World world, BlockRootyDirt dirt, BlockPos pos) {
		if(dirt.fertilize(world, pos, amount)) {
			if(world.isRemote) {
				TreeHelper.getSafeTreePart(world, pos.up()).analyse(world, pos.up(), null, new MapSignal(new NodeTwinkle(EnumParticleTypes.VILLAGER_HAPPY, 8)));
			}
			return true;
		}
		return false;
	}

	@Override
	public boolean update(World world, BlockRootyDirt dirt, BlockPos pos, int deltaTicks) {
		return false;
	}
	
	@Override
	public String getName() {
		return "fertilize";
	}
	
	public SubstanceFertilize setAmount(int amount) {
		this.amount = amount;
		return this;
	}

	@Override
	public boolean isLingering() {
		return false;
	}
	
}