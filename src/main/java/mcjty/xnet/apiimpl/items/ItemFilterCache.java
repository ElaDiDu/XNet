package mcjty.xnet.apiimpl.items;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import mcjty.lib.varia.ItemStackList;
import mcjty.xnet.compat.ForestrySupport;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.oredict.OreDictionary;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class ItemFilterCache {
    private boolean matchDamage = true;
    private boolean oredictMode = false;
    private boolean blacklistMode = true;
    private boolean nbtMode = false;
    private ItemStackList stacks;
    private Set<Integer> oredictMatches = new HashSet<>();
    private HashMap<ItemStack, Set<Integer>> oredictPer = new HashMap<>();

    public ItemFilterCache(boolean matchDamage, boolean oredictMode, boolean blacklistMode, boolean nbtMode, @Nonnull ItemStackList stacks) {
        this.matchDamage = matchDamage;
        this.oredictMode = oredictMode;
        this.blacklistMode = blacklistMode;
        this.nbtMode = nbtMode;
        this.stacks = stacks;
        if (oredictMode)
        {
            for (ItemStack s : stacks)
            {
                int[] oreIDs = OreDictionary.getOreIDs(s);
                for (int id : oreIDs)
                {
                    oredictMatches.add(id);
                }
                oredictPer.put(s, Sets.newHashSet(Arrays.stream(oreIDs).iterator()));
            }
        }
    }

    public boolean match(ItemStack stack) {
        if (!stack.isEmpty()) {
            boolean match = false;

            if (oredictMode) {
                int[] oreIDs = OreDictionary.getOreIDs(stack);
                if (oreIDs.length == 0) {
                    match = itemMatches(stack);
                } else {
                    for (int id : oreIDs) {
                        if (oredictMatches.contains(id)) {
                            match = true;
                            break;
                        }
                    }
                }
            } else {
                match = itemMatches(stack);
            }
            return match != blacklistMode;
        }
        return false;
    }

    private boolean itemMatches(ItemStack stack) {
        if (stacks != null) {
            int forestryFlags = ForestrySupport.Tag.GEN.getFlag() | ForestrySupport.Tag.IS_ANALYZED.getFlag();
            ItemStack cleanedStack = null;
            if(nbtMode && ForestrySupport.isLoaded() && ForestrySupport.isBreedable(stack)) {
                cleanedStack = ForestrySupport.sanitize(stack, forestryFlags);
            }
            for (ItemStack itemStack : stacks) {
                if (matchDamage && itemStack.getMetadata() != stack.getMetadata()) {
                    continue;
                }
                if (!itemStack.getItem().equals(stack.getItem())) {
                    continue;
                }
                if (nbtMode) {
                    if((cleanedStack != null) && ForestrySupport.isBreedable(itemStack)) {
                        ItemStack cleanedItemStack = ForestrySupport.sanitize(itemStack, forestryFlags);
                        if(!ItemStack.areItemStackTagsEqual(cleanedItemStack, cleanedStack)) {
                    		continue;
                    	}
                    }
                    else if(!ItemStack.areItemStackTagsEqual(itemStack, stack)) {
                        continue;
                    }
                }
                return true;
            }
        }
        return false;
    }


    public boolean itemMatchesFilterItem(ItemStack first, ItemStack second)
    {
        if (!oredictMode)
            return specificItemMatchesNoOre(first, second);


        Set<Integer> oreIDs = oredictPer.getOrDefault(first, ImmutableSet.of());
        if (oreIDs.isEmpty())
            return specificItemMatchesNoOre(first, second);

        for (int ore : OreDictionary.getOreIDs(second))
        {
            if (oreIDs.contains(ore))
                return true;
        }

        return false;
    }

    private boolean specificItemMatchesNoOre(ItemStack first, ItemStack second)
    {

        int forestryFlags = ForestrySupport.Tag.GEN.getFlag() | ForestrySupport.Tag.IS_ANALYZED.getFlag();
        ItemStack cleanedStack = null;
        if (nbtMode && ForestrySupport.isLoaded() && ForestrySupport.isBreedable(first))
            cleanedStack = ForestrySupport.sanitize(first, forestryFlags);

        if (matchDamage && second.getMetadata() != first.getMetadata())
            return false;

        if (!second.getItem().equals(first.getItem()))
            return false;

        if (nbtMode)
        {
            if ((cleanedStack != null) && ForestrySupport.isBreedable(second))
            {
                ItemStack cleanedItemStack = ForestrySupport.sanitize(second, forestryFlags);
                if (!ItemStack.areItemStackTagsEqual(cleanedItemStack, cleanedStack))
                    return false;

            } else if (!ItemStack.areItemStackTagsEqual(second, first))
            {
                return false;
            }
        }
        return true;
    }

    public int itemsNeededToSatisfyFilter(IItemHandler handler, ItemStack stack)
    {
        if (stacks == null)
            return Integer.MAX_VALUE;

        int needed = Integer.MAX_VALUE;
        int cnt = 0;
        boolean found = false;
        for (ItemStack filterStack : stacks)
        {
            if (itemMatchesFilterItem(filterStack, stack))
            {
                needed = filterStack.getCount();
                // Needed for oredict cache, matching filter is transitive anyways
                stack = filterStack;
                found = true;
                break;
            }
        }
        if (!found)
            return Integer.MAX_VALUE;

        for (int i = 0 ; i < handler.getSlots() ; i++)
        {
            ItemStack s = handler.getStackInSlot(i);
            if (s.isEmpty())
                continue;

            if (itemMatchesFilterItem(stack, s))
                cnt += s.getCount();
        }

        return Math.max(needed - cnt, 0);

    }
}
