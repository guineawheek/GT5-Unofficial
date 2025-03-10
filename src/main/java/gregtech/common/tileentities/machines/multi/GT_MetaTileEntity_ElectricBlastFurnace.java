package gregtech.common.tileentities.machines.multi;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.enums.GT_HatchElement.*;
import static gregtech.api.enums.GT_Values.V;
import static gregtech.api.enums.GT_Values.VN;
import static gregtech.api.enums.Textures.BlockIcons.*;
import static gregtech.api.util.GT_StructureUtility.buildHatchAdder;
import static gregtech.api.util.GT_StructureUtility.ofCoil;

import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import gregtech.GT_Mod;
import gregtech.api.GregTech_API;
import gregtech.api.enums.HeatingCoilLevel;
import gregtech.api.enums.Materials;
import gregtech.api.gui.GT_GUIContainer_MultiMachine;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.*;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GT_Multiblock_Tooltip_Builder;
import gregtech.api.util.GT_Recipe;
import gregtech.api.util.GT_Utility;
import java.util.ArrayList;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.FluidStack;

public class GT_MetaTileEntity_ElectricBlastFurnace
        extends GT_MetaTileEntity_AbstractMultiFurnace<GT_MetaTileEntity_ElectricBlastFurnace>
        implements ISurvivalConstructable {
    private int mHeatingCapacity = 0;
    private boolean isBussesSeparate = false;
    protected final ArrayList<GT_MetaTileEntity_Hatch_Output> mPollutionOutputHatches = new ArrayList<>();
    protected final FluidStack[] pollutionFluidStacks = {
        Materials.CarbonDioxide.getGas(1000),
        Materials.CarbonMonoxide.getGas(1000),
        Materials.SulfurDioxide.getGas(1000)
    };

    protected static final int CASING_INDEX = 11;
    protected static final String STRUCTURE_PIECE_MAIN = "main";
    private static final IStructureDefinition<GT_MetaTileEntity_ElectricBlastFurnace> STRUCTURE_DEFINITION =
            StructureDefinition.<GT_MetaTileEntity_ElectricBlastFurnace>builder()
                    .addShape(STRUCTURE_PIECE_MAIN, transpose(new String[][] {
                        {"ttt", "tmt", "ttt"},
                        {"CCC", "C-C", "CCC"},
                        {"CCC", "C-C", "CCC"},
                        {"b~b", "bbb", "bbb"}
                    }))
                    .addElement(
                            't',
                            buildHatchAdder(GT_MetaTileEntity_ElectricBlastFurnace.class)
                                    .atLeast(OutputHatch.withAdder(
                                                    GT_MetaTileEntity_ElectricBlastFurnace::addOutputHatchToTopList)
                                            .withCount(t -> t.mPollutionOutputHatches.size()))
                                    .casingIndex(CASING_INDEX)
                                    .dot(1)
                                    .buildAndChain(GregTech_API.sBlockCasings1, CASING_INDEX))
                    .addElement('m', Muffler.newAny(CASING_INDEX, 2))
                    .addElement(
                            'C',
                            ofCoil(
                                    GT_MetaTileEntity_ElectricBlastFurnace::setCoilLevel,
                                    GT_MetaTileEntity_ElectricBlastFurnace::getCoilLevel))
                    .addElement(
                            'b',
                            buildHatchAdder(GT_MetaTileEntity_ElectricBlastFurnace.class)
                                    .atLeast(InputHatch, OutputHatch, InputBus, OutputBus, Maintenance, Energy)
                                    .casingIndex(CASING_INDEX)
                                    .dot(1)
                                    .buildAndChain(GregTech_API.sBlockCasings1, CASING_INDEX))
                    .build();

    public GT_MetaTileEntity_ElectricBlastFurnace(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public GT_MetaTileEntity_ElectricBlastFurnace(String aName) {
        super(aName);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new GT_MetaTileEntity_ElectricBlastFurnace(this.mName);
    }

    @Override
    protected GT_Multiblock_Tooltip_Builder createTooltip() {
        GT_Multiblock_Tooltip_Builder tt = new GT_Multiblock_Tooltip_Builder();
        tt.addMachineType("Blast Furnace")
                .addInfo("Controller block for the Electric Blast Furnace")
                .addInfo("You can use some fluids to reduce recipe time. Place the circuit in the Input Bus")
                .addInfo("Each 900K over the min. Heat required reduces power consumption by 5% (multiplicatively)")
                .addInfo("Each 1800K over the min. Heat required grants one perfect overclock")
                .addInfo(
                        "For each perfect overclock the EBF will reduce recipe time 4 times (instead of 2) (100% efficiency)")
                .addInfo("Additionally gives +100K for every tier past MV")
                .addPollutionAmount(getPollutionPerSecond(null))
                .addSeparator()
                .beginStructureBlock(3, 4, 3, true)
                .addController("Front bottom")
                .addCasingInfo("Heat Proof Machine Casing", 0)
                .addOtherStructurePart("Heating Coils", "Two middle Layers")
                .addEnergyHatch("Any bottom layer casing", 3)
                .addMaintenanceHatch("Any bottom layer casing", 3)
                .addMufflerHatch("Top middle", 2)
                .addInputBus("Any bottom layer casing", 3)
                .addInputHatch("Any bottom layer casing", 3)
                .addOutputBus("Any bottom layer casing", 3)
                .addOutputHatch("Liquid form of fluids, Any bottom layer casing")
                .addOutputHatch("Gas form of fluids, Any top layer casing", 1)
                .addStructureInfo("Recovery amount scales with Muffler Hatch tier")
                .toolTipFinisher("Gregtech");
        return tt;
    }

    @Override
    public ITexture[] getTexture(
            IGregTechTileEntity aBaseMetaTileEntity,
            byte aSide,
            byte aFacing,
            byte aColorIndex,
            boolean aActive,
            boolean aRedstone) {
        if (aSide == aFacing) {
            if (aActive)
                return new ITexture[] {
                    casingTexturePages[0][CASING_INDEX],
                    TextureFactory.builder()
                            .addIcon(OVERLAY_FRONT_ELECTRIC_BLAST_FURNACE_ACTIVE)
                            .extFacing()
                            .build(),
                    TextureFactory.builder()
                            .addIcon(OVERLAY_FRONT_ELECTRIC_BLAST_FURNACE_ACTIVE_GLOW)
                            .extFacing()
                            .glow()
                            .build()
                };
            return new ITexture[] {
                casingTexturePages[0][CASING_INDEX],
                TextureFactory.builder()
                        .addIcon(OVERLAY_FRONT_ELECTRIC_BLAST_FURNACE)
                        .extFacing()
                        .build(),
                TextureFactory.builder()
                        .addIcon(OVERLAY_FRONT_ELECTRIC_BLAST_FURNACE_GLOW)
                        .extFacing()
                        .glow()
                        .build()
            };
        }
        return new ITexture[] {casingTexturePages[0][CASING_INDEX]};
    }

    @Override
    public Object getClientGUI(int aID, InventoryPlayer aPlayerInventory, IGregTechTileEntity aBaseMetaTileEntity) {
        return new GT_GUIContainer_MultiMachine(
                aPlayerInventory, aBaseMetaTileEntity, getLocalName(), "ElectricBlastFurnace.png");
    }

    @Override
    public int getPollutionPerSecond(ItemStack aStack) {
        return GT_Mod.gregtechproxy.mPollutionEBFPerSecond;
    }

    @Override
    public GT_Recipe.GT_Recipe_Map getRecipeMap() {
        return GT_Recipe.GT_Recipe_Map.sBlastRecipes;
    }

    @Override
    public boolean isCorrectMachinePart(ItemStack aStack) {
        return true;
    }

    @Override
    public IStructureDefinition<GT_MetaTileEntity_ElectricBlastFurnace> getStructureDefinition() {
        return STRUCTURE_DEFINITION;
    }

    @Override
    public boolean checkRecipe(ItemStack aStack) {
        if (isBussesSeparate) {
            FluidStack[] tFluids = getStoredFluids().toArray(new FluidStack[0]);
            for (GT_MetaTileEntity_Hatch_InputBus tBus : mInputBusses) {
                ArrayList<ItemStack> tInputs = new ArrayList<>();
                tBus.mRecipeMap = getRecipeMap();

                if (isValidMetaTileEntity(tBus)) {
                    for (int i = tBus.getBaseMetaTileEntity().getSizeInventory() - 1; i >= 0; i--) {
                        if (tBus.getBaseMetaTileEntity().getStackInSlot(i) != null) {
                            tInputs.add(tBus.getBaseMetaTileEntity().getStackInSlot(i));
                        }
                    }
                }
                ItemStack[] tItems = tInputs.toArray(new ItemStack[0]);
                if (processRecipe(tItems, tFluids)) {
                    return true;
                }
            }
            return false;
        } else {
            return processRecipe(getCompactedInputs(), getCompactedFluids());
        }
    }

    protected boolean processRecipe(ItemStack[] tItems, FluidStack[] tFluids) {
        if (tItems.length <= 0) return false;

        long tVoltage = getMaxInputVoltage();
        byte tTier = (byte) Math.max(1, GT_Utility.getTier(tVoltage));

        GT_Recipe tRecipe = GT_Recipe.GT_Recipe_Map.sBlastRecipes.findRecipe(
                getBaseMetaTileEntity(), false, V[tTier], tFluids, tItems);

        if (tRecipe == null) return false;
        if (this.mHeatingCapacity < tRecipe.mSpecialValue) return false;
        if (!tRecipe.isRecipeInputEqual(true, tFluids, tItems)) return false;
        // In case recipe is too OP for that machine
        if (mMaxProgresstime == Integer.MAX_VALUE - 1 && mEUt == Integer.MAX_VALUE - 1) return false;

        this.mEfficiency = (10000 - (getIdealStatus() - getRepairStatus()) * 1000);
        this.mEfficiencyIncrease = 10000;

        int tHeatCapacityDivTiers = (mHeatingCapacity - tRecipe.mSpecialValue) / 900;
        byte overclockCount = calculateOverclockednessEBF(tRecipe.mEUt, tRecipe.mDuration, tVoltage);
        if (this.mEUt > 0) {
            this.mEUt = (-this.mEUt);
        }
        if (tHeatCapacityDivTiers > 0) {
            this.mEUt = (int) (this.mEUt * (Math.pow(0.95, tHeatCapacityDivTiers)));
            this.mMaxProgresstime >>=
                    Math.min(tHeatCapacityDivTiers / 2, overclockCount); // extra free overclocking if possible
            if (this.mMaxProgresstime < 1) this.mMaxProgresstime = 1; // no eu efficiency correction
        }
        this.mMaxProgresstime = Math.max(1, this.mMaxProgresstime);
        this.mOutputItems = new ItemStack[] {tRecipe.getOutput(0), tRecipe.getOutput(1)};
        this.mOutputFluids = new FluidStack[] {tRecipe.getFluidOutput(0)};
        updateSlots();
        return true;
    }
    /**
     * Calcualtes overclocked ness using long integers
     *
     * @param aEUt      - recipe EUt
     * @param aDuration - recipe Duration
     */
    protected byte calculateOverclockednessEBF(int aEUt, int aDuration, long maxInputVoltage) {
        byte mTier = (byte) Math.max(0, GT_Utility.getTier(maxInputVoltage)), timesOverclocked = 0;
        if (mTier == 0) {
            // Long time calculation
            long xMaxProgresstime = ((long) aDuration) << 1;
            if (xMaxProgresstime > Integer.MAX_VALUE - 1) {
                // make impossible if too long
                mEUt = Integer.MAX_VALUE - 1;
                mMaxProgresstime = Integer.MAX_VALUE - 1;
            } else {
                mEUt = aEUt >> 2;
                mMaxProgresstime = (int) xMaxProgresstime;
            }
            // return 0;
        } else {
            // Long EUt calculation
            long xEUt = aEUt;
            // Isnt too low EUt check?
            long tempEUt = Math.max(xEUt, V[1]);

            mMaxProgresstime = aDuration;

            while (tempEUt <= V[mTier - 1]) {
                tempEUt <<= 2; // this actually controls overclocking
                // xEUt *= 4;//this is effect of everclocking
                mMaxProgresstime >>= 1; // this is effect of overclocking
                xEUt = mMaxProgresstime == 0
                        ? xEUt >> 1
                        : xEUt << 2; // U know, if the time is less than 1 tick make the machine use less power
                timesOverclocked++;
            }
            if (xEUt > Integer.MAX_VALUE - 1) {
                mEUt = Integer.MAX_VALUE - 1;
                mMaxProgresstime = Integer.MAX_VALUE - 1;
            } else {
                mEUt = (int) xEUt;
                if (mEUt == 0) mEUt = 1;
                if (mMaxProgresstime == 0) mMaxProgresstime = 1; // set time to 1 tick
            }
        }
        return timesOverclocked;
    }

    public boolean addOutputHatchToTopList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
        if (aMetaTileEntity == null) return false;
        if (aMetaTileEntity instanceof GT_MetaTileEntity_Hatch_Output) {
            ((GT_MetaTileEntity_Hatch) aMetaTileEntity).updateTexture(aBaseCasingIndex);
            return mPollutionOutputHatches.add((GT_MetaTileEntity_Hatch_Output) aMetaTileEntity);
        }
        return false;
    }

    @Override
    public boolean checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack) {
        this.mHeatingCapacity = 0;

        setCoilLevel(HeatingCoilLevel.None);

        mPollutionOutputHatches.clear();

        if (!checkPiece(STRUCTURE_PIECE_MAIN, 1, 3, 0)) return false;

        if (getCoilLevel() == HeatingCoilLevel.None) return false;

        if (mMaintenanceHatches.size() != 1) return false;

        this.mHeatingCapacity = (int) getCoilLevel().getHeat() + 100 * (GT_Utility.getTier(getMaxInputVoltage()) - 2);
        return true;
    }

    @Override
    public boolean addOutput(FluidStack aLiquid) {
        if (aLiquid == null) return false;
        FluidStack tLiquid = aLiquid.copy();
        boolean isOutputPollution = false;
        for (FluidStack pollutionFluidStack : pollutionFluidStacks) {
            if (!tLiquid.isFluidEqual(pollutionFluidStack)) continue;

            isOutputPollution = true;
            break;
        }
        ArrayList<GT_MetaTileEntity_Hatch_Output> tOutputHatches;
        if (isOutputPollution) {
            tOutputHatches = this.mPollutionOutputHatches;
            int pollutionReduction = 0;
            for (GT_MetaTileEntity_Hatch_Muffler tHatch : mMufflerHatches) {
                if (!isValidMetaTileEntity(tHatch)) continue;
                pollutionReduction = 100 - tHatch.calculatePollutionReduction(100);
                break;
            }
            tLiquid.amount = tLiquid.amount * (pollutionReduction + 5) / 100;
        } else {
            tOutputHatches = this.mOutputHatches;
        }
        return dumpFluid(tOutputHatches, tLiquid, true) || dumpFluid(tOutputHatches, tLiquid, false);
    }

    @Override
    public String[] getInfoData() {
        int mPollutionReduction = 0;
        for (GT_MetaTileEntity_Hatch_Muffler tHatch : mMufflerHatches) {
            if (!isValidMetaTileEntity(tHatch)) continue;
            mPollutionReduction = Math.max(tHatch.calculatePollutionReduction(100), mPollutionReduction);
        }

        long storedEnergy = 0;
        long maxEnergy = 0;
        for (GT_MetaTileEntity_Hatch_Energy tHatch : mEnergyHatches) {
            if (!isValidMetaTileEntity(tHatch)) continue;
            storedEnergy += tHatch.getBaseMetaTileEntity().getStoredEU();
            maxEnergy += tHatch.getBaseMetaTileEntity().getEUCapacity();
        }

        return new String[] {
            StatCollector.translateToLocal("GT5U.multiblock.Progress") + ": " + EnumChatFormatting.GREEN
                    + GT_Utility.formatNumbers(mProgresstime / 20) + EnumChatFormatting.RESET + " s / "
                    + EnumChatFormatting.YELLOW
                    + GT_Utility.formatNumbers(mMaxProgresstime / 20) + EnumChatFormatting.RESET + " s",
            StatCollector.translateToLocal("GT5U.multiblock.energy") + ": " + EnumChatFormatting.GREEN
                    + GT_Utility.formatNumbers(storedEnergy) + EnumChatFormatting.RESET + " EU / "
                    + EnumChatFormatting.YELLOW
                    + GT_Utility.formatNumbers(maxEnergy) + EnumChatFormatting.RESET + " EU",
            StatCollector.translateToLocal("GT5U.multiblock.usage") + ": " + EnumChatFormatting.RED
                    + GT_Utility.formatNumbers(-mEUt) + EnumChatFormatting.RESET + " EU/t",
            StatCollector.translateToLocal("GT5U.multiblock.mei") + ": " + EnumChatFormatting.YELLOW
                    + GT_Utility.formatNumbers(getMaxInputVoltage()) + EnumChatFormatting.RESET + " EU/t(*2A) "
                    + StatCollector.translateToLocal("GT5U.machines.tier")
                    + ": " + EnumChatFormatting.YELLOW
                    + VN[GT_Utility.getTier(getMaxInputVoltage())] + EnumChatFormatting.RESET,
            StatCollector.translateToLocal("GT5U.multiblock.problems") + ": " + EnumChatFormatting.RED
                    + (getIdealStatus() - getRepairStatus()) + EnumChatFormatting.RESET + " "
                    + StatCollector.translateToLocal("GT5U.multiblock.efficiency")
                    + ": " + EnumChatFormatting.YELLOW
                    + mEfficiency / 100.0F + EnumChatFormatting.RESET + " %",
            StatCollector.translateToLocal("GT5U.EBF.heat") + ": " + EnumChatFormatting.GREEN
                    + GT_Utility.formatNumbers(mHeatingCapacity) + EnumChatFormatting.RESET + " K",
            StatCollector.translateToLocal("GT5U.multiblock.pollution") + ": " + EnumChatFormatting.GREEN
                    + mPollutionReduction + EnumChatFormatting.RESET + " %"
        };
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        buildPiece(STRUCTURE_PIECE_MAIN, stackSize, hintsOnly, 1, 3, 0);
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        return survivialBuildPiece(STRUCTURE_PIECE_MAIN, stackSize, 1, 3, 0, elementBudget, env, false, true);
    }

    @Override
    public void onScrewdriverRightClick(byte aSide, EntityPlayer aPlayer, float aX, float aY, float aZ) {
        isBussesSeparate = !isBussesSeparate;
        GT_Utility.sendChatToPlayer(
                aPlayer, StatCollector.translateToLocal("GT5U.machines.separatebus") + " " + isBussesSeparate);
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setBoolean("isBussesSeparate", isBussesSeparate);
    }

    @Override
    public void loadNBTData(final NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        isBussesSeparate = aNBT.getBoolean("isBussesSeparate");
    }
}
