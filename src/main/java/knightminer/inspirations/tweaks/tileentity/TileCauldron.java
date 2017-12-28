package knightminer.inspirations.tweaks.tileentity;

import javax.annotation.Nonnull;

import knightminer.inspirations.common.Config;
import knightminer.inspirations.library.InspirationsRegistry;
import knightminer.inspirations.library.recipe.cauldron.ICauldronRecipe;
import knightminer.inspirations.library.recipe.cauldron.ICauldronRecipe.CauldronContents;
import knightminer.inspirations.library.recipe.cauldron.ICauldronRecipe.CauldronState;
import net.minecraft.block.BlockCauldron;
import net.minecraft.block.BlockFire;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.items.ItemHandlerHelper;

public class TileCauldron extends TileEntity {
	private CauldronState state;

	public TileCauldron() {
		this.state = CauldronState.WATER;
	}

	@Override
	public boolean shouldRefresh(World world, BlockPos pos, IBlockState oldState, IBlockState newSate) {
		return oldState.getBlock() != newSate.getBlock();
	}

	@Nonnull
	public CauldronContents getContentType() {
		return state.getType();
	}

	public int getColor() {
		switch(state.getType()) {
			case DYE:
				return state.getColor();
			case POTION:
				return PotionUtils.getPotionColor(state.getPotion());
		}

		return -1;
	}

	public boolean interact(IBlockState blockState, EntityPlayer player, EnumHand hand) {
		ItemStack stack = player.getHeldItem(hand);
		if(stack.isEmpty()) {
			return false;
		}

		boolean boiling = world.getBlockState(pos.down()).getBlock() instanceof BlockFire;
		int level = blockState.getValue(BlockCauldron.LEVEL);
		ICauldronRecipe recipe = InspirationsRegistry.getCauldronResult(stack, boiling, level, state);
		CauldronState state = this.state;
		if(recipe != null) {

			// state
			CauldronState newState = recipe.getState(stack, boiling, level, state);
			if(!isValid(newState)) {
				return false;
			}
			if(!state.equals(newState)) {
				this.state = newState;
				if(world.isRemote) {
					Minecraft.getMinecraft().renderGlobal.notifyBlockUpdate(null, pos, null, null, 0);
				}
			}

			// level
			setLevel(blockState, recipe.getLevel(level));

			// sound
			SoundEvent sound = recipe.getSound(stack, boiling, level, state);
			if(sound != null) {
				world.playSound((EntityPlayer)null, pos, sound, SoundCategory.BLOCKS, recipe.getVolume(), 1.0F);
			}

			// result
			ItemStack result = recipe.getResult(stack, boiling, level, state);
			// update held item
			player.setHeldItem(hand, recipe.transformInput(stack, boiling, level, state));
			// and give the new item to the player
			if(!result.isEmpty()) {
				ItemHandlerHelper.giveItemToPlayer(player, result, player.inventory.currentItem);
			}

			return true;
		}

		return false;
	}

	private boolean isValid(CauldronState state) {
		switch(state.getType()) {
			case POTION:
				return Config.enableCauldronBrewing;
			case DYE:
				return Config.enableCauldronDyeing;
		}
		return true;
	}

	private void setLevel(IBlockState state, int level) {
		level = MathHelper.clamp(level, 0, 3);
		if(state.getValue(BlockCauldron.LEVEL) == level) {
			return;
		}

		Blocks.CAULDRON.setWaterLevel(world, pos, state, level);
		if(level == 0) {
			this.state = CauldronState.WATER;
		}
	}

	public void setColor(int color) {
		this.state = CauldronState.dye(color);
	}


	/* Networking */


	/* NBT */
	public static final String TAG_STATE = "state";

	@Nonnull
	@Override
	public NBTTagCompound getUpdateTag() {
		// new tag instead of super since default implementation calls the super of writeToNBT
		return writeToNBT(new NBTTagCompound());
	}

	@Nonnull
	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound tags) {
		tags = super.writeToNBT(tags);

		tags.setTag(TAG_STATE, state.writeToNBT());

		return tags;
	}

	@Override
	public void readFromNBT(NBTTagCompound tags) {
		super.readFromNBT(tags);

		this.state = CauldronState.fromNBT(tags.getCompoundTag(TAG_STATE));
	}
}
