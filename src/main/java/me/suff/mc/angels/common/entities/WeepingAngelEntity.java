package me.suff.mc.angels.common.entities;

import me.suff.mc.angels.api.EventAngelBreakEvent;
import me.suff.mc.angels.client.poses.WeepingAngelPose;
import me.suff.mc.angels.common.WAObjects;
import me.suff.mc.angels.common.entities.ai.GoalWalkWhenNotWatched;
import me.suff.mc.angels.common.entities.attributes.WAAttributes;
import me.suff.mc.angels.common.misc.WAConstants;
import me.suff.mc.angels.config.WAConfig;
import me.suff.mc.angels.utils.AngelUtil;
import me.suff.mc.angels.utils.NBTPatcher;
import me.suff.mc.angels.utils.WATeleporter;
import net.minecraft.block.*;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.attributes.AttributeModifierMap;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.ai.goal.BreakDoorGoal;
import net.minecraft.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.network.play.server.SPlaySoundEffectPacket;
import net.minecraft.particles.BlockParticleData;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.pathfinding.GroundPathNavigator;
import net.minecraft.pathfinding.PathNavigator;
import net.minecraft.util.*;
import net.minecraft.util.concurrent.TickDelayedTask;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.*;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.MinecraftForge;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.Objects;
import java.util.function.Predicate;

import static me.suff.mc.angels.utils.AngelUtil.breakBlock;

public class WeepingAngelEntity extends QuantumLockEntity {

    private static final DataParameter<String> TYPE = EntityDataManager.defineId(WeepingAngelEntity.class, DataSerializers.STRING);
    private static final DataParameter<String> CURRENT_POSE = EntityDataManager.defineId(WeepingAngelEntity.class, DataSerializers.STRING);
    private static final DataParameter<String> VARIENT = EntityDataManager.defineId(WeepingAngelEntity.class, DataSerializers.STRING);
    private static final DataParameter<Float> LAUGH = EntityDataManager.defineId(WeepingAngelEntity.class, DataSerializers.FLOAT);
    private static final Predicate<Difficulty> DIFFICULTY = (difficulty) -> difficulty == Difficulty.EASY;
    private static final SoundEvent[] CHILD_SOUNDS = new SoundEvent[]{SoundEvents.VEX_AMBIENT, WAObjects.Sounds.LAUGHING_CHILD.get()};
    public long timeSincePlayedSound = 0;

    public WeepingAngelEntity(EntityType<? extends QuantumLockEntity> type, World world) {
        this(world);
    }

    public WeepingAngelEntity(World world) {
        super(world, WAObjects.EntityEntries.WEEPING_ANGEL.get());
        goalSelector.addGoal(0, new BreakDoorGoal(this, DIFFICULTY));
        goalSelector.addGoal(5, new MoveTowardsRestrictionGoal(this, 1.0D));
        goalSelector.addGoal(7, new GoalWalkWhenNotWatched(this, 1.0D));
        xpReward = WAConfig.CONFIG.xpGained.get();
    }


    public static AttributeModifierMap.MutableAttribute createAttributes() {
        return createMonsterAttributes().
                add(Attributes.ATTACK_DAMAGE, WAConfig.CONFIG.damage.get()).
                add(Attributes.MAX_HEALTH, 50D).
                add(Attributes.KNOCKBACK_RESISTANCE, 1.0D).
                add(Attributes.MOVEMENT_SPEED, 0.8D).
                add(WAAttributes.BLOCK_BREAK_RANGE.get(), WAConfig.CONFIG.blockBreakRange.get()).
                add(Attributes.ARMOR, 2.0D);
    }

    @Override
    public void knockback(float strength, double ratioX, double ratioZ) {

    }

    public void dropAngelStuff() {
        if (level.isClientSide()) return;
        AngelUtil.dropEntityLoot(this, this.lastHurtByPlayer);
        spawnAtLocation(getMainHandItem());
        spawnAtLocation(getOffhandItem());
    }

    @Override
    public boolean displayFireAnimation() {
        return false;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        getEntityData().define(TYPE, AngelUtil.randomType().name());
        getEntityData().define(CURRENT_POSE, WeepingAngelPose.getRandomPose(AngelUtil.RAND).name());
        getEntityData().define(VARIENT, AngelUtil.randomVarient().name());
        getEntityData().define(LAUGH, random.nextFloat());
    }

    public String getVarient() {
        return getEntityData().get(VARIENT);
    }

    public void setVarient(AngelVariants varient) {
        getEntityData().set(VARIENT, varient.name());
    }

    @Nullable
    @Override
    public ILivingEntityData finalizeSpawn(IServerWorld serverWorld, DifficultyInstance difficultyInstance, SpawnReason spawnReason, @Nullable ILivingEntityData livingEntityData, @Nullable CompoundNBT compoundNBT) {
        playSound(WAObjects.Sounds.ANGEL_AMBIENT.get(), 0.5F, 1.0F);
        return super.finalizeSpawn(serverWorld, difficultyInstance, spawnReason, livingEntityData, compoundNBT);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSourceIn) {
        return SoundEvents.STONE_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return WAObjects.Sounds.ANGEL_DEATH.get();
    }

    @Override
    protected SoundEvent getAmbientSound() {
        if (isCherub() && tickCount % AngelUtil.secondsToTicks(2) == 0) {
            return CHILD_SOUNDS[random.nextInt(CHILD_SOUNDS.length)];
        }
        return null;
    }

    @Override
    public float getEyeHeight(Pose p_213307_1_) {
        return isCherub() ? 0.5F : 1.3F;
    }

    @Override
    public EntitySize getDimensions(Pose poseIn) {
        return isCherub() ? new EntitySize(0.8F, 0.8F, true) : super.getDimensions(poseIn);
    }

    @Override
    public boolean isBaby() {
        return isCherub();
    }

    @Override
    public boolean doHurtTarget(Entity entity) {
        if (entity instanceof ServerPlayerEntity) {
            ServerPlayerEntity serverPlayerEntity = (ServerPlayerEntity) entity;
            boolean isCherub = isCherub();
            if (isCherub) {
                dealDamage(serverPlayerEntity);
                if (WAConfig.CONFIG.torchBlowOut.get()) {
                    AngelUtil.removeLightFromHand(serverPlayerEntity, this);
                }
                return true;
            }

            boolean shouldTeleport = random.nextInt(10) < 5 && getHealth() > 5 && !isInCatacomb() || WAConfig.CONFIG.justTeleport.get();
            if (shouldTeleport) {
                teleportInteraction(serverPlayerEntity);
                return true;
            }
            dealDamage(serverPlayerEntity);
        }
        return true;
    }

    public boolean isInCatacomb() {
        if (level instanceof ServerWorld) {
            ServerWorld serverWorld = (ServerWorld) level;
            BlockPos catacomb = serverWorld.getLevel().findNearestMapFeature(WAObjects.Structures.CATACOMBS.get(), blockPosition(), 100, false);

            if (catacomb == null) {
                return false;
            }

            return distanceToSqr(catacomb.getX(), catacomb.getY(), catacomb.getZ()) < 50;
        }

        return false;
    }

    public void dealDamage(PlayerEntity playerMP) {
        playerMP.hurt(getHealth() > 5 ? WAObjects.ANGEL : WAObjects.ANGEL_NECK_SNAP, 4F);
        heal(getHealth() > 5 ? 4F : 2F);
        stealItems(playerMP);
    }

    private void stealItems(PlayerEntity playerMP) {
        // Steals items from the player
        if (getMainHandItem().isEmpty() && random.nextBoolean()) {
            for (int i = 0; i < playerMP.inventory.getContainerSize(); i++) {
                ItemStack stack = playerMP.inventory.getItem(i);
                if (stack.getItem().is(AngelUtil.THEFT)) {
                    setItemInHand(Hand.MAIN_HAND, playerMP.inventory.getItem(i).copy());
                    playerMP.inventory.getItem(i).setCount(0);
                    playerMP.inventoryMenu.broadcastChanges();
                    return;
                }
            }
        }
    }

    public long getTimeSincePlayedSound() {
        return timeSincePlayedSound;
    }

    public void setTimeSincePlayedSound(long timeSincePlayedSound) {
        this.timeSincePlayedSound = timeSincePlayedSound;
    }

    @Override
    protected boolean shouldDropExperience() {
        return true;
    }

    @Override
    public void die(DamageSource cause) {
        super.die(cause);
        spawnAtLocation(getMainHandItem());
        spawnAtLocation(getOffhandItem());

        if (getAngelType() == AngelEnums.AngelType.ANGELA_MC) {
            AngelVariants angelVarient = AngelVariants.valueOf(getVarient());
            spawnAtLocation(angelVarient.getDropStack());
        }

    }

    @Override
    public void makeStuckInBlock(BlockState state, Vector3d motionMultiplierIn) {
        if (!state.is(Blocks.COBWEB)) {
            super.makeStuckInBlock(state, motionMultiplierIn);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundNBT compound) {
        super.addAdditionalSaveData(compound);
        compound.putString(WAConstants.POSE, getAngelPose());
        compound.putString(WAConstants.TYPE, getAngelType().name());
        compound.putString(WAConstants.VARIENT, getVarient());
        compound.putFloat(WAConstants.LAUGH, getLaugh());
    }

    @Override
    public void load(CompoundNBT compound) {
        super.load(compound);

        NBTPatcher.angelaToVillager(compound, WAConstants.TYPE);

        if (compound.contains(WAConstants.POSE))
            setPose(WeepingAngelPose.getPose(compound.getString(WAConstants.POSE)));

        if (compound.contains(WAConstants.LAUGH))
            setLaugh(serializeNBT().getFloat(WAConstants.LAUGH));

        if (compound.contains(WAConstants.TYPE)) setType(compound.getString(WAConstants.TYPE));

        if (compound.contains(WAConstants.VARIENT))
            setVarient(AngelVariants.valueOf(compound.getString(WAConstants.VARIENT)));
    }

    @Override
    public void onSyncedDataUpdated(DataParameter<?> key) {
        super.onSyncedDataUpdated(key);
        if (TYPE.equals(key)) {
            refreshDimensions();
        }
    }

    @Override
    public void invokeSeen(PlayerEntity player) {
        super.invokeSeen(player);
        if (player instanceof ServerPlayerEntity && getSeenTime() == 1 && getPrevPos().asLong() != blockPosition().asLong()) {
            setPrevPos(blockPosition());
            playSeenSound(player);
            randomisePose();
        }
        this.zza = 0.0F;
        this.yya = 0.0F;
        this.zza = 0.0F;
    }


    private void randomisePose() {
        if (getAngelType() != AngelEnums.AngelType.VIO_1) {
            setPose(WeepingAngelPose.getRandomPose(AngelUtil.RAND));
            return;
        }
        setPose(Objects.requireNonNull(random.nextBoolean() ? WeepingAngelPose.ANGRY : WeepingAngelPose.HIDING));
    }

    private void playSeenSound(PlayerEntity player) {
        boolean canPlaySound = !player.isCreative() && getTimeSincePlayedSound() == 0 || System.currentTimeMillis() - getTimeSincePlayedSound() >= 20000;
        if (canPlaySound) {
            if (WAConfig.CONFIG.playSeenSounds.get() && player.distanceTo(this) < 15) {
                setTimeSincePlayedSound(System.currentTimeMillis());
                ((ServerPlayerEntity) player).connection.send(new SPlaySoundEffectPacket(WAObjects.Sounds.ANGEL_SEEN.get(), SoundCategory.HOSTILE, player.getX(), player.getY(), player.getZ(), 0.1F, 1.0F));
            }
        }
    }


    @Override
    protected void playStepSound(BlockPos pos, BlockState blockIn) {
        if (!blockIn.getMaterial().isLiquid()) {
            BlockState blockstate = this.level.getBlockState(pos.above());
            SoundType soundtype = blockstate.getBlock() == Blocks.SNOW ? blockstate.getSoundType(level, pos, this) : blockIn.getSoundType(level, pos, this);

            if (isCherub()) {
                if (level.random.nextInt(5) == 4) {
                    playSound(WAObjects.Sounds.CHILD_RUN.get(), 0.1F, soundtype.getPitch());
                }
            } else if (WAConfig.CONFIG.playScrapeSounds.get() && level.random.nextInt(5) == 4) {
                playSound(WAObjects.Sounds.STONE_SCRAP.get(), 0.1F, soundtype.getPitch());
            }
        }
    }

    @Override
    public void tick() {
        modelCheck();
        super.tick();
        if (getSeenTime() == 0 || level.isEmptyBlock(blockPosition().below())) {
            setNoAi(false);
        }

        if (tickCount % 500 == 0 && getTarget() == null && getSeenTime() == 0) {
            setPose(Objects.requireNonNull(WeepingAngelPose.HIDING));
        }
        setItemInHand(Hand.MAIN_HAND, new ItemStack(WAObjects.Items.TIMEY_WIMEY_DETECTOR.get()));
        if (random.nextBoolean() && WAConfig.CONFIG.blockBreaking.get() && isSeen() && level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).get()) {
            replaceBlocks();
        }
    }

    private void modelCheck() {
        for (AngelEnums.AngelType angelType : AngelEnums.AngelType.values()) {
            if (!WAConfig.CONFIG.allowedTypes.get().contains(angelType.name())) {
                setType(WAConfig.CONFIG.allowedTypes.get().get(0));
            }
        }
    }

    @Override
    protected PathNavigator createNavigation(World worldIn) {
        GroundPathNavigator navigator = new GroundPathNavigator(this, worldIn);
        navigator.setCanFloat(false);
        navigator.setCanOpenDoors(true);
        navigator.setAvoidSun(false);
        navigator.setSpeedModifier(1.0D);
        return navigator;
    }

    private void replaceBlocks() {
        if (level.isClientSide || tickCount % 100 != 0) return;

        if (level.getMaxLocalRawBrightness(blockPosition()) == 0) {
            return;
        }
        int range = (int) getAttributeValue(WAAttributes.BLOCK_BREAK_RANGE.get());
        for (Iterator<BlockPos> iterator = BlockPos.withinManhattanStream(blockPosition(), range, 3, range).iterator(); iterator.hasNext(); ) {
            BlockPos pos = iterator.next();
            ServerWorld serverWorld = (ServerWorld) level;
            BlockState blockState = serverWorld.getBlockState(pos);
            if (isAllowed(blockState, pos)) {

                if (blockState.getBlock().is(AngelUtil.BANNED_BLOCKS) || blockState.getBlock() == Blocks.LAVA) {
                    continue;
                }

                if (blockState.getBlock() == Blocks.TORCH || blockState.getBlock() == Blocks.REDSTONE_TORCH || blockState.getBlock() == Blocks.GLOWSTONE) {
                    breakBlock(this, pos, Blocks.AIR.defaultBlockState());
                    return;
                }

                if (blockState.getBlock() == Blocks.REDSTONE_LAMP) {
                    if (blockState.getValue(RedstoneLampBlock.LIT)) {
                        breakBlock(this, pos, blockState.setValue(RedstoneLampBlock.LIT, false));
                        return;
                    }
                }

                if (blockState.getBlock() instanceof NetherPortalBlock || blockState.getBlock() instanceof EndPortalBlock) {
                    if (getHealth() < getMaxHealth()) {
                        heal(0.5F);
                        Vector3d start = position();
                        Vector3d end = new Vector3d(pos.getX(), pos.getY(), pos.getZ());
                        Vector3d path = start.subtract(end);
                        for (int i = 0; i < 10; ++i) {
                            double percent = i / 10.0;
                            ((ServerWorld) level).sendParticles(ParticleTypes.PORTAL, pos.getX() + 0.5 + path.x() * percent, pos.getY() + 1.3 + path.y() * percent, pos.getZ() + 0.5 + path.z * percent, 20, 0, 0, 0, 0);
                        }
                        return;
                    } else {
                        continue;
                    }
                }

                if (blockState.getLightValue(level, pos) > 0 && !(blockState.getBlock() instanceof NetherPortalBlock) && !(blockState.getBlock() instanceof EndPortalBlock)) {
                    breakBlock(this, pos, Blocks.AIR.defaultBlockState());
                    return;
                }
            }
        }
    }

    private void teleportInteraction(ServerPlayerEntity player) {
        if (level.isClientSide) return;
        AngelUtil.EnumTeleportType type = WAConfig.CONFIG.teleportType.get();
        switch (type) {

            case DONT:
                doHurtTarget(player);
                break;
            case STRUCTURES:
                Objects.requireNonNull(level.getServer()).tell(new TickDelayedTask(level.getServer().getTickCount()+1, () -> {
                    if (!WATeleporter.handleStructures(player)) {
                        dealDamage(player);
                    }
                }));
                break;
            case RANDOM_PLACE:
                double x = player.getX() + random.nextInt(WAConfig.CONFIG.teleportRange.get());
                double z = player.getZ() + random.nextInt(WAConfig.CONFIG.teleportRange.get());

                ServerWorld teleportWorld = WAConfig.CONFIG.angelDimTeleport.get() ? WATeleporter.getRandomDimension(random) : (ServerWorld) player.level;
                ChunkPos chunkPos = new ChunkPos(new BlockPos(x, 0, z));
                teleportWorld.setChunkForced(chunkPos.x, chunkPos.z, true);

                teleportWorld.getServer().tell(new TickDelayedTask(teleportWorld.getServer().getTickCount() + 1, () -> {
                    BlockPos blockPos = WATeleporter.findSafePlace(player, teleportWorld, new BlockPos(x, player.getY(), z));

                    if (AngelUtil.isOutsideOfBorder(teleportWorld, blockPos)) {
                        dealDamage(player);
                        return;
                    }

                    if (teleportWorld != null) {
                        WATeleporter.teleportPlayerTo(player, blockPos, teleportWorld);
                        teleportWorld.setChunkForced(chunkPos.x, chunkPos.z, false);
                        heal(10);
                    }
                }));
                break;
        }
    }

    @Override
    public boolean checkSpawnRules(IWorld worldIn, SpawnReason spawnReasonIn) {
        return worldIn.getDifficulty() != Difficulty.PEACEFUL && this.isValidLightLevel() && super.checkSpawnRules(worldIn, spawnReasonIn);
    }


    protected boolean isValidLightLevel() {
        BlockPos blockpos = new BlockPos(this.getX(), this.getBoundingBox().minY, this.getZ());
        if (this.level.getBrightness(LightType.SKY, blockpos) > this.random.nextInt(32)) {
            return false;
        } else {
            int i = this.level.isThundering() ? this.level.getMaxLocalRawBrightness(blockpos, 10) : this.level.getMaxLocalRawBrightness(blockpos);
            return i <= this.random.nextInt(8);
        }
    }

    public String getAngelPose() {
        return getEntityData().get(CURRENT_POSE);
    }

    public void setPose(WeepingAngelPose weepingAngelPose) {
        getEntityData().set(CURRENT_POSE, weepingAngelPose.name());
    }

    public boolean isCherub() {
        return getAngelType() == AngelEnums.AngelType.CHERUB;
    }

    public AngelEnums.AngelType getAngelType() {
        String type = getEntityData().get(TYPE);
        return type.isEmpty() ? AngelEnums.AngelType.ANGELA_MC : AngelEnums.AngelType.valueOf(type);
    }

    public void setType(String angelType) {
        getEntityData().set(TYPE, angelType);
    }

    public void setType(AngelEnums.AngelType angelType) {
        setType(angelType.name());
    }


    @Override
    protected void tickDeath() {
        ++this.deathTime;
        if (this.deathTime == 20) {
            hurtTime = 0;
            this.removeAfterChangingDimensions();
            playSound(getDeathSound(), 1, 1);
        }
        for (int i = 0; i < 20; ++i) {
            double d0 = this.random.nextGaussian() * 0.02D;
            double d1 = this.random.nextGaussian() * 0.02D;
            double d2 = this.random.nextGaussian() * 0.02D;
            this.level.addParticle(new BlockParticleData(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()), this.getRandomX(1.0D), this.getRandomY(), this.getRandomZ(1.0D), d0, d1, d2);
        }
    }

    @Override
    public void kill() {
        remove();
    }

    public boolean isAllowed(BlockState blockState, BlockPos blockPos) {
        EventAngelBreakEvent eventAngelBreakEvent = new EventAngelBreakEvent(this, blockState, blockPos);
        MinecraftForge.EVENT_BUS.post(eventAngelBreakEvent);
        return !eventAngelBreakEvent.isCanceled();
    }

    public float getLaugh() {
        return getEntityData().get(LAUGH);
    }

    public void setLaugh(float laugh) {
        getEntityData().set(LAUGH, laugh);
    }

    public enum AngelVariants {
        MOSSY(new ItemStack(Blocks.VINE)), NORMAL(new ItemStack(Blocks.COBBLESTONE)), RUSTED(new ItemStack(Blocks.GRANITE)), RUSTED_NO_ARM(new ItemStack(Blocks.GRANITE)), RUSTED_NO_WING(new ItemStack(Blocks.GRANITE)), RUSTED_HEADLESS(true, new ItemStack(Blocks.GRANITE));

        private final boolean headless;
        private final ItemStack dropStack;

        AngelVariants(ItemStack stack) {
            this(false, stack);
        }

        AngelVariants(boolean b, ItemStack stack) {
            headless = b;
            this.dropStack = stack;
        }

        public ItemStack getDropStack() {
            return dropStack;
        }

        public boolean isHeadless() {
            return headless;
        }
    }

}
