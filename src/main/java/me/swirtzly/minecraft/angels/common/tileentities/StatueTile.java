package me.swirtzly.minecraft.angels.common.tileentities;

import me.swirtzly.minecraft.angels.client.poses.AngelPoses;
import me.swirtzly.minecraft.angels.common.WAObjects;
import me.swirtzly.minecraft.angels.common.entities.AngelEnums;
import me.swirtzly.minecraft.angels.common.entities.AngelEnums.AngelType;
import me.swirtzly.minecraft.angels.common.entities.WeepingAngelEntity;
import me.swirtzly.minecraft.angels.common.misc.WAConstants;
import me.swirtzly.minecraft.angels.config.WAConfig;
import me.swirtzly.minecraft.angels.utils.NBTPatcher;
import me.swirtzly.minecraft.angels.utils.ViewUtil;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityPredicate;
import net.minecraft.entity.monster.EndermanEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SUpdateTileEntityPacket;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EntityPredicates;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.server.ServerLifecycleHooks;

import java.util.List;

public class StatueTile extends TileEntity implements ITickableTileEntity {

    private int rotation = 0;
    private String type = AngelEnums.AngelType.ANGELA_MC.name();
    private ResourceLocation pose = AngelPoses.getRandomPose().getRegistryName();
    private WeepingAngelEntity.AngelVarients angelVarients = WeepingAngelEntity.AngelVarients.NORMAL;


    public StatueTile() {
        super(WAObjects.Tiles.STATUE.get());
    }

    @Override
    public void read(BlockState state, CompoundNBT compound) {
        super.read(state, compound);
        NBTPatcher.angelaToVillager(compound, "model");
        setPose(new ResourceLocation(compound.getString("pose")));
        rotation = compound.getInt("rotation");
        type = compound.getString("model");
        if (compound.contains(WAConstants.VARIENT)) {
            setAngelVarients(WeepingAngelEntity.AngelVarients.valueOf(compound.getString(WAConstants.VARIENT)));
        }
    }

    @Override
    public CompoundNBT write(CompoundNBT compound) {
        super.write(compound);
        compound.putInt("rotation", rotation);
        compound.putString("model", type);
        compound.putString("pose", pose.toString());
        compound.putString(WAConstants.VARIENT, angelVarients.name());
        return compound;
    }

    public AngelEnums.AngelType getAngelType() {
        return AngelEnums.AngelType.valueOf(type.isEmpty() ? AngelType.ANGELA_MC.name() : type);
    }

    public void setAngelType(String type) {
        this.type = type;
    }

    public void setAngelType(AngelEnums.AngelType type) {
        this.type = type.name();
    }

    public int getRotation() {
        return rotation;
    }

    public void setRotation(int rotation) {
        this.rotation = rotation;
        sendUpdates();
    }

    @Override
    public SUpdateTileEntityPacket getUpdatePacket() {
        return new SUpdateTileEntityPacket(pos, 3, getUpdateTag());
    }

    @Override
    public CompoundNBT getUpdateTag() {
        return write(new CompoundNBT());
    }

    @Override
    public void handleUpdateTag(BlockState state, CompoundNBT tag) {
        this.read(state, tag);
    }

    @Override
    public void onDataPacket(NetworkManager net, SUpdateTileEntityPacket pkt) {
        super.onDataPacket(net, pkt);
        handleUpdateTag(getBlockState(), pkt.getNbtCompound());
    }

    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        return super.getRenderBoundingBox().grow(8, 8, 8);
    }

    public void sendUpdates() {
        world.updateComparatorOutputLevel(pos, getBlockState().getBlock());
        world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
        markDirty();
    }

    @Override
    public void tick() {
        if (world.isRemote) return;

        if (world.getRedstonePowerFromNeighbors(pos) > 0 && world.getTileEntity(pos) instanceof PlinthTile) {
            PlinthTile plinth = (PlinthTile) world.getTileEntity(pos);
            if (!plinth.getHasSpawned()) {
                WeepingAngelEntity angel = new WeepingAngelEntity(world);
                angel.setVarient(angelVarients);
                angel.setType(type);
                angel.setLocationAndAngles(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0, 0);
                angel.setPose(getPose());
                world.addEntity(angel);
                plinth.setHasSpawned(true);
                sendUpdates();
            }
        }
    }

    public ResourceLocation getPose() {
        return new ResourceLocation(pose.toString());
    }

    public void setPose(ResourceLocation pose) {
        this.pose = pose;
    }

    public WeepingAngelEntity.AngelVarients getAngelVarients() {
        return angelVarients;
    }

    public void setAngelVarients(WeepingAngelEntity.AngelVarients angelVarients) {
        this.angelVarients = angelVarients;
    }

}
