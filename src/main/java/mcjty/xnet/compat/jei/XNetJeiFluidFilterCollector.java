package mcjty.xnet.compat.jei;

import mcjty.lib.varia.FluidTools;
import mezz.jei.api.gui.IGuiIngredient;
import mezz.jei.api.gui.IRecipeLayout;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class XNetJeiFluidFilterCollector {

    public enum Target {
        INPUTS,
        OUTPUTS
    }

    public static final class Result {
        private final Target target;
        private final List<ItemStack> filters;

        private Result(Target target, List<ItemStack> filters) {
            this.target = target;
            this.filters = Collections.unmodifiableList(filters);
        }

        public boolean isInputs() {
            return target == Target.INPUTS;
        }

        public boolean isOutputs() {
            return target == Target.OUTPUTS;
        }

        public List<ItemStack> getFilters() {
            return filters;
        }
    }

    private XNetJeiFluidFilterCollector() {
    }

    public static Result collect(IRecipeLayout recipeLayout, Target target) {
        List<ItemStack> merged = new ArrayList<>();
        boolean wantInputs = target == Target.INPUTS;

        Map<Integer, ? extends IGuiIngredient<FluidStack>> ingredients =
                recipeLayout.getFluidStacks().getGuiIngredients();

        for (IGuiIngredient<FluidStack> ingredient : ingredients.values()) {
            if (ingredient.isInput() != wantInputs) {
                continue;
            }

            FluidStack fluidStack = getDisplayedOrFirst(ingredient);
            if (fluidStack == null) {
                continue;
            }

            ItemStack bucket = toFilterBucket(fluidStack);
            if (bucket.isEmpty()) {
                continue;
            }

            mergeByFluid(merged, bucket);
        }

        return new Result(target, merged);
    }

    private static FluidStack getDisplayedOrFirst(IGuiIngredient<FluidStack> ingredient) {
        FluidStack displayed = ingredient.getDisplayedIngredient();

        if (displayed != null) {
            return displayed.copy();
        }

        for (FluidStack candidate : ingredient.getAllIngredients()) {
            if (candidate != null) {
                return candidate.copy();
            }
        }

        return null;
    }

    private static ItemStack toFilterBucket(FluidStack fluidStack) {
        FluidStack copy = fluidStack.copy();

        // A bucket filter is only an identity reference. Make sure there is
        // enough fluid for normal bucket conversion even if JEI shows <1000mb.
        if (copy.amount < 1000) {
            copy.amount = 1000;
        }

        ItemStack bucket = FluidTools.convertFluidToBucket(copy);
        if (!bucket.isEmpty()) {
            bucket.setCount(1);
        }

        return bucket;
    }

    private static void mergeByFluid(List<ItemStack> merged, ItemStack bucket) {
        FluidStack fluid = FluidTools.convertBucketToFluid(bucket);
        if (fluid == null) {
            return;
        }

        for (ItemStack existing : merged) {
            FluidStack existingFluid = FluidTools.convertBucketToFluid(existing);
            if (existingFluid != null && existingFluid.isFluidEqual(fluid)) {
                return;
            }
        }

        ItemStack copy = bucket.copy();
        copy.setCount(1);
        merged.add(copy);
    }
}