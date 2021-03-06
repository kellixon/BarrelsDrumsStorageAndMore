package funwayguy.bdsandm.blocks;

import funwayguy.bdsandm.blocks.tiles.TileEntityBarrel;
import funwayguy.bdsandm.core.BDSM;
import funwayguy.bdsandm.core.BdsmConfig;
import funwayguy.bdsandm.inventory.capability.BdsmCapabilies;
import funwayguy.bdsandm.inventory.capability.CapabilityBarrel;
import funwayguy.bdsandm.inventory.capability.IBarrel;
import net.minecraft.block.BlockDirectional;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;

public class BlockBarrelBase extends BlockDirectional implements ITileEntityProvider
{
    private final int initCap;
    private final int maxCap;
    
    protected BlockBarrelBase(Material materialIn, int initCap, int maxCap)
    {
        super(materialIn);
        this.setDefaultState(this.blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));
        this.setCreativeTab(BDSM.tabBdsm);
        
        this.initCap = initCap;
        this.maxCap = maxCap;
    }
    
    @Override
    public boolean onBlockActivated(World worldIn, BlockPos pos, IBlockState state, EntityPlayer playerIn, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ)
    {
        if(worldIn.isRemote) return true;
        
        TileEntity tile = worldIn.getTileEntity(pos);
        
        if(tile == null || !tile.hasCapability(BdsmCapabilies.BARREL_CAP, null)) return true;
        
        CapabilityBarrel barrel = (CapabilityBarrel)tile.getCapability(BdsmCapabilies.BARREL_CAP, null);
        
        if(barrel == null)
        {
            return false;
        } else if(!playerIn.isSneaking() && barrel.installUpgrade(playerIn, playerIn.getHeldItem(hand)))
        {
            return true;
        } else
        {
            depositItem(barrel, playerIn, hand);
        }
        
        return true;
    }
    
    @Override
    public void onBlockClicked(World worldIn, BlockPos pos, EntityPlayer playerIn)
    {
        if(worldIn.isRemote) return;
        
        TileEntity tile = worldIn.getTileEntity(pos);
        if(tile == null || !tile.hasCapability(BdsmCapabilies.CRATE_CAP, null)) return;
        
        CapabilityBarrel barrel = (CapabilityBarrel)tile.getCapability(BdsmCapabilies.BARREL_CAP, null);
        if(barrel == null) return;
        
        withdrawItem(barrel, playerIn);
    }
    
    private void depositItem(CapabilityBarrel barrel, EntityPlayer player, EnumHand hand)
    {
        ItemStack refItem = barrel.getRefItem();
        FluidStack refFluid = barrel.getRefFluid();
        ItemStack held = player.getHeldItem(hand);
        IFluidHandlerItem container = held.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null);
        int maxDrain = barrel.getStackCap() < 0 ? 1000 : (int)Math.min(1000, barrel.getStackCap() * 1000L - (refFluid == null ? 0 : barrel.getCount()));
        
        if(container != null && refItem.isEmpty() && !held.isEmpty()) // Fill fluid
        {
            FluidStack drainStack;
            
            if(refFluid == null)
            {
                drainStack = container.drain(maxDrain, false);
            } else
            {
                drainStack = refFluid.copy();
                drainStack.amount = maxDrain;
                drainStack = container.drain(drainStack, false);
            }
            
            if(drainStack != null && drainStack.amount > 0)
            {
                drainStack.amount = barrel.fill(drainStack, true);
                
                if(drainStack.amount > 0)
                {
                    container.drain(drainStack, true);
                    player.setHeldItem(hand, container.getContainer());
                }
                
                return;
            }
        }
        
        if(BdsmConfig.multiPurposeBarrel)
        {
            if(!held.isEmpty() && (refItem.isEmpty() || barrel.canMergeWith(held)))
            {
                player.setHeldItem(hand, barrel.insertItem(barrel.getSlots() - 1, held, false));
            } else if(!refItem.isEmpty()) // Insert all
            {
                for(int i = 0; i < player.inventory.getSizeInventory(); i++)
                {
                    ItemStack invoStack = player.inventory.getStackInSlot(i);
            
                    if(barrel.canMergeWith(invoStack))
                    {
                        invoStack = barrel.insertItem(barrel.getSlots() - 1, invoStack, false);
                        boolean done = !invoStack.isEmpty();
                        player.inventory.setInventorySlotContents(i, invoStack);
                
                        if(done)
                        {
                            break;
                        }
                    }
                }
        
            }
        }
    }
    
    private void withdrawItem(CapabilityBarrel barrel, EntityPlayer player)
    {
        ItemStack ref = barrel.getRefItem();
        FluidStack refFluid = barrel.getRefFluid();
        ItemStack held = player.getHeldItem(EnumHand.MAIN_HAND);
        IFluidHandlerItem container = held.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null);
        int maxFill = barrel.getStackCap() < 0 ? 1000 : (int)Math.min(1000, barrel.getStackCap() * 1000L - (refFluid == null ? 0 : barrel.getCount()));
        
        if(container != null && refFluid != null && !held.isEmpty() && barrel.getCount() >= held.getCount())
        {
            FluidStack fillStack = refFluid.copy();
            fillStack.amount = maxFill / held.getCount();
            
            int testFill = container.fill(fillStack, false); // Doesn't really matter if we overfill here. Just checking capacity and fluid match
            
            if(testFill > 0)
            {
                fillStack.amount = testFill * held.getCount();
                FluidStack drained = barrel.drain(fillStack, true);
                if(drained != null)
                {
                    drained.amount /= held.getCount();
                    container.fill(drained, true);
                    player.setHeldItem(EnumHand.MAIN_HAND, container.getContainer());
                }
            }
        } else if(!ref.isEmpty())
        {
            ItemStack out = barrel.extractItem(0, !player.isSneaking() ? 64 : 1, false);
            if(!player.addItemStackToInventory(out)) player.dropItem(out, true, false);
        }
    }
	
    @Override
    @SuppressWarnings("deprecation")
    public EnumBlockRenderType getRenderType(IBlockState state)
    {
        return EnumBlockRenderType.MODEL;
    }
    
	@Override
    @SideOnly(Side.CLIENT)
    public BlockRenderLayer getRenderLayer()
    {
        return BlockRenderLayer.CUTOUT;
    }
    
    @Override
    @SuppressWarnings("deprecation")
    public boolean isFullCube(IBlockState state)
    {
        return false;
    }
    
    @Override
    @SuppressWarnings("deprecation")
    public boolean isOpaqueCube(IBlockState state)
    {
        return false;
    }
    
    @Nullable
    @Override
    public TileEntity createNewTileEntity(World worldIn, int meta)
    {
        return new TileEntityBarrel(initCap, maxCap);
    }
    
    @Override
    public IBlockState getStateForPlacement(World worldIn, BlockPos pos, EnumFacing facing, float hitX, float hitY, float hitZ, int meta, EntityLivingBase placer, EnumHand hand)
    {
        return this.getDefaultState().withProperty(FACING, EnumFacing.getDirectionFromEntityLiving(pos, placer).getOpposite());
    }
    
    @Override
    public int getMetaFromState(IBlockState state)
    {
        return (state.getValue(FACING)).getIndex();
    }
    
    @Override
    @SuppressWarnings("deprecation")
    public IBlockState getStateFromMeta(int meta)
    {
        return this.getDefaultState().withProperty(FACING, EnumFacing.byIndex(meta & 7));
    }
    
    @Override
    protected BlockStateContainer createBlockState()
    {
        return new BlockStateContainer(this, FACING);
    }
    
    // =v= DROP MODIFICATIONS =v=
    
    public void dropBlockAsItemWithChance(World worldIn, BlockPos pos, IBlockState state, float chance, int fortune)
    {
    }
    
    public void onBlockHarvested(World worldIn, BlockPos pos, IBlockState state, EntityPlayer player)
    {
        TileEntity tile = worldIn.getTileEntity(pos);
        
        if(tile instanceof TileEntityBarrel)
        {
            ((TileEntityBarrel)tile).setCreativeBroken(player.capabilities.isCreativeMode);
        }
    }
    
    public void breakBlock(World worldIn, BlockPos pos, IBlockState state)
    {
        TileEntity tileentity = worldIn.getTileEntity(pos);

        if (tileentity instanceof TileEntityBarrel && !((TileEntityBarrel)tileentity).isCreativeBroken())
        {
            TileEntityBarrel tileBarrel = (TileEntityBarrel)tileentity;
            ItemStack stack = new ItemStack(Item.getItemFromBlock(this));
            IBarrel tileCap = tileBarrel.getCapability(BdsmCapabilies.BARREL_CAP, null);
            IBarrel itemCap = stack.getCapability(BdsmCapabilies.BARREL_CAP, null);
            itemCap.copyContainer(tileCap);
            
            spawnAsEntity(worldIn, pos, stack);
        }

        super.breakBlock(worldIn, pos, state);
    }
    
    @Override
    @SuppressWarnings("deprecation")
    public ItemStack getItem(World worldIn, BlockPos pos, IBlockState state)
    {
        ItemStack stack = super.getItem(worldIn, pos, state);
        TileEntity tileBarrel = worldIn.getTileEntity(pos);
        
        if(tileBarrel instanceof TileEntityBarrel)
        {
            IBarrel tileCap = tileBarrel.getCapability(BdsmCapabilies.BARREL_CAP, null);
            IBarrel itemCap = stack.getCapability(BdsmCapabilies.BARREL_CAP, null);
    
            itemCap.copyContainer(tileCap);
        }
        
        return stack;
    }
    
    @Override
    @SuppressWarnings("deprecation")
    public IBlockState withRotation(IBlockState state, Rotation rot)
    {
        return state.withProperty(FACING, rot.rotate(state.getValue(FACING)));
    }
    
    @Override
    @SuppressWarnings("deprecation")
    public IBlockState withMirror(IBlockState state, Mirror mirrorIn)
    {
        return state.withRotation(mirrorIn.toRotation(state.getValue(FACING)));
    }
    
    @Override
    public boolean rotateBlock(World world, BlockPos pos, EnumFacing axis)
    {
        if(world.isRemote) return super.rotateBlock(world, pos, axis);
        
        TileEntity tile = world.getTileEntity(pos);
        boolean changed = super.rotateBlock(world, pos, axis);
        TileEntity nTile = world.getTileEntity(pos);
        
        if(changed && tile instanceof TileEntityBarrel && nTile instanceof TileEntityBarrel)
        {
            nTile.getCapability(BdsmCapabilies.BARREL_CAP, null).copyContainer(tile.getCapability(BdsmCapabilies.BARREL_CAP, null));
            ((TileEntityBarrel)nTile).onCrateChanged();
        }
        
        return changed;
    }
}
