package mcjty.xnet.compat.jei;

import mezz.jei.api.gui.IGuiIngredient;
import mezz.jei.api.gui.IRecipeLayout;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class XNetJeiItemFilterCollector {

    private static final int MAX_FILTER_STACK_COUNT = 4096;

    public enum Mode {
        NORMAL,
        ADVANCED
    }

    public enum Target {
        INPUTS,
        OUTPUTS
    }

    public static final class Result {
        private final Mode mode;
        private final Target target;
        private final List<ItemStack> filters;
        private final boolean needsMeta;
        private final boolean needsNbt;

        private Result(Mode mode, Target target, List<ItemStack> filters, boolean needsMeta, boolean needsNbt) {
            this.mode = mode;
            this.target = target;
            this.filters = Collections.unmodifiableList(filters);
            this.needsMeta = needsMeta;
            this.needsNbt = needsNbt;
        }

        public boolean isAdvanced() {
            return mode == Mode.ADVANCED;
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

        public boolean needsMeta() {
            return needsMeta;
        }

        public boolean needsNbt() {
            return needsNbt;
        }
    }

    private XNetJeiItemFilterCollector() {
    }

    public static Result collect(IRecipeLayout recipeLayout, Mode mode, Target target) {
        List<ItemStack> merged = new ArrayList<>();
        boolean needsMeta = false;
        boolean needsNbt = false;

        boolean wantInputs = target == Target.INPUTS;

        Map<Integer, ? extends IGuiIngredient<ItemStack>> ingredients =
                recipeLayout.getItemStacks().getGuiIngredients();

        for (IGuiIngredient<ItemStack> ingredient : ingredients.values()) {
            if (ingredient.isInput() != wantInputs) {
                continue;
            }

            ItemStack stack = getDisplayedOrFirst(ingredient);
            if (stack.isEmpty()) {
                continue;
            }

            if (mode == Mode.ADVANCED) {
                if (stack.hasTagCompound()) {
                    needsNbt = true;
                }

                if (ingredientNeedsMeta(ingredient, stack)) {
                    needsMeta = true;
                }

                stack.setCount(clamp(stack.getCount()));
                mergeAdvanced(merged, stack);
            } else {
                stack.setCount(1);
                mergeNormal(merged, stack);
            }
        }

        return new Result(mode, target, merged, needsMeta, needsNbt);
    }

    private static ItemStack getDisplayedOrFirst(IGuiIngredient<ItemStack> ingredient) {
        ItemStack displayed = ingredient.getDisplayedIngredient();

        if (displayed != null && !displayed.isEmpty()) {
            return displayed.copy();
        }

        for (ItemStack candidate : ingredient.getAllIngredients()) {
            if (candidate != null && !candidate.isEmpty()) {
                return candidate.copy();
            }
        }

        return ItemStack.EMPTY;
    }

    private static void mergeNormal(List<ItemStack> merged, ItemStack stack) {
        for (ItemStack existing : merged) {
            if (sameExactFilterTarget(existing, stack)) {
                existing.setCount(1);
                return;
            }
        }

        ItemStack copy = stack.copy();
        copy.setCount(1);
        merged.add(copy);
    }

    private static void mergeAdvanced(List<ItemStack> merged, ItemStack stack) {
        for (ItemStack existing : merged) {
            if (sameExactFilterTarget(existing, stack)) {
                existing.setCount(clamp(existing.getCount() + stack.getCount()));
                return;
            }
        }

        ItemStack copy = stack.copy();
        copy.setCount(clamp(copy.getCount()));
        merged.add(copy);
    }

    private static boolean sameExactFilterTarget(ItemStack a, ItemStack b) {
        return ItemStack.areItemsEqual(a, b)
                && ItemStack.areItemStackTagsEqual(a, b);
    }

    private static boolean ingredientNeedsMeta(IGuiIngredient<ItemStack> ingredient, ItemStack selected) {
        if (selected.isEmpty()) {
            return false;
        }

        if (!selected.getItem().getHasSubtypes()) {
            return false;
        }

        int selectedMeta = selected.getMetadata();

        if (selectedMeta == OreDictionary.WILDCARD_VALUE) {
            return false;
        }

        boolean sawAlternative = false;

        for (ItemStack alternative : ingredient.getAllIngredients()) {
            if (alternative == null || alternative.isEmpty()) {
                continue;
            }

            sawAlternative = true;

            if (alternative.getItem() == selected.getItem()) {
                int alternativeMeta = alternative.getMetadata();

                if (alternativeMeta == OreDictionary.WILDCARD_VALUE || alternativeMeta != selectedMeta) {
                    return false;
                }
            }
        }

        return sawAlternative;
    }

    private static int clamp(int count) {
        if (count <= 0) {
            return 1;
        }
        return Math.min(count, MAX_FILTER_STACK_COUNT);
    }
}