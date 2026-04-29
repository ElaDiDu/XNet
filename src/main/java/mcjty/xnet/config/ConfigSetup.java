package mcjty.xnet.config;

import com.google.common.collect.Lists;
import mcjty.lib.thirteen.ConfigSpec;
import mcjty.xnet.XNet;
import mcjty.xnet.blocks.wireless.TileEntityWirelessRouter;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.FMLLog;
import org.apache.logging.log4j.Level;

import java.io.File;
import java.util.List;

public class ConfigSetup {
    public static final String CATEGORY_GENERAL = "general";

    public static ConfigSpec.IntValue controllerMaxRF;
    public static ConfigSpec.IntValue controllerRfPerTick;

    public static ConfigSpec.IntValue wirelessRouterMaxRF;
    public static ConfigSpec.IntValue wirelessRouterRfPerTick;
    public static ConfigSpec.IntValue wirelessRouterRfPerChannel[] = new ConfigSpec.IntValue[3];

    public static ConfigSpec.IntValue maxRfConnector;
    public static ConfigSpec.IntValue maxRfAdvancedConnector;

    public static ConfigSpec.IntValue maxRfRateNormal;
    public static ConfigSpec.IntValue maxRfRateAdvanced;

    public static final int MAX_TRANSFER_UNLIMITED = Integer.MAX_VALUE;

    public static ConfigSpec.IntValue maxItemTransferNormal;
    public static ConfigSpec.IntValue maxItemTransferAdvanced;
    public static ConfigSpec.IntValue maxFluidTransferNormal;
    public static ConfigSpec.IntValue maxFluidTransferAdvanced;

    // Cached primitive values. No allocation, no GC, no per transfer look-up.
    public static int maxItemTransferNormalCached = MAX_TRANSFER_UNLIMITED;
    public static int maxItemTransferAdvancedCached = MAX_TRANSFER_UNLIMITED;
    public static int maxFluidTransferNormalCached = MAX_TRANSFER_UNLIMITED;
    public static int maxFluidTransferAdvancedCached = MAX_TRANSFER_UNLIMITED;

    // Fast-path booleans.
    // In the default case, these are false, so channel code exits immediately.
    public static boolean itemTransferCapsEnabled = false;
    public static boolean fluidTransferCapsEnabled = false;

    public static ConfigSpec.IntValue controllerRFT;          // RF per tick that the controller uses all the time
    public static ConfigSpec.IntValue controllerChannelRFT;   // RF Per tick per enabled channel
    public static ConfigSpec.IntValue controllerOperationRFT; // RF Per tick per operation

    public static ConfigSpec.IntValue maxPublishedChannels;    // Maximum number of published channels on a routing network

    public static ConfigSpec.IntValue antennaTier1Range;
    public static ConfigSpec.IntValue antennaTier2Range;

    public static ConfigSpec.BooleanValue showNonFacadedCablesWhileSneaking;

    private static String[] unsidedBlocksAr = new String[] {
            "minecraft:chest",
            "minecraft:trapped_chest",
            "rftools:modular_storage",
            "rftools:storage_scanner",
            "rftools:pearl_injector",
    };
    public static ConfigSpec.ConfigValue<List<? extends String>> unsidedBlocks;

    private static final ConfigSpec.Builder SERVER_BUILDER = new ConfigSpec.Builder();
    private static final ConfigSpec.Builder CLIENT_BUILDER = new ConfigSpec.Builder();

    static {
        SERVER_BUILDER.comment("General settings").push(CATEGORY_GENERAL);
        CLIENT_BUILDER.comment("General settings").push(CATEGORY_GENERAL);

        unsidedBlocks = SERVER_BUILDER
                .comment("This is a list of blocks that XNet considers to be 'unsided' meaning that it doesn't matter from what side you access things. This is currently only used to help with pasting channels")
                .defineList("unsidedBlocks", Lists.newArrayList(unsidedBlocksAr), s -> s instanceof String);

        controllerMaxRF = SERVER_BUILDER
                .comment("Maximum RF the controller can store")
                .defineInRange("controllerMaxRF", 100000, 1, 1000000000);
        controllerRfPerTick = SERVER_BUILDER
                .comment("Maximum RF the controller can receive per tick")
                .defineInRange("controllerRfPerTick", 1000, 1, 1000000000);
        wirelessRouterMaxRF = SERVER_BUILDER
                .comment("Maximum RF the wireless router can store")
                .defineInRange("wirelessRouterMaxRF", 100000, 1, 1000000000);
        wirelessRouterRfPerTick = SERVER_BUILDER
                .comment("Maximum RF the wireless router can receive per tick")
                .defineInRange("wirelessRouterRfPerTick", 5000, 1, 1000000000);

        wirelessRouterRfPerChannel[TileEntityWirelessRouter.TIER_1] = SERVER_BUILDER
                .comment("Maximum RF per tick the wireless router (tier 1) needs to publish a channel")
                .defineInRange("wireless1RfPerChannel", 20, 0, 1000000000);
        wirelessRouterRfPerChannel[TileEntityWirelessRouter.TIER_2] = SERVER_BUILDER
                .comment("Maximum RF per tick the wireless router (tier 2) needs to publish a channel")
                .defineInRange("wireless2RfPerChannel", 50, 0, 1000000000);
        wirelessRouterRfPerChannel[TileEntityWirelessRouter.TIER_INF] = SERVER_BUILDER
                .comment("Maximum RF per tick the wireless router (infinite tier) needs to publish a channel")
                .defineInRange("wirelessInfRfPerChannel", 200, 0, 1000000000);

        maxRfConnector = SERVER_BUILDER
                .comment("Maximum RF the normal connector can store")
                .defineInRange("maxRfConnector", 50000, 1, 1000000000);
        maxRfAdvancedConnector = SERVER_BUILDER
                .comment("Maximum RF the advanced connector can store")
                .defineInRange("maxRfAdvancedConnector", 500000, 1, 1000000000);
        maxRfRateNormal = SERVER_BUILDER
                .comment("Maximum RF/rate that a normal connector can input or output")
                .defineInRange("maxRfRateNormal", 10000, 1, 1000000000);
        maxRfRateAdvanced = SERVER_BUILDER
                .comment("Maximum RF/rate that an advanced connector can input or output")
                .defineInRange("maxRfRateAdvanced", 100000, 1, 1000000000);
        maxItemTransferNormal = SERVER_BUILDER
                .comment("Global maximum number of items transferred per operation by a normal item connector. Applies to all item transfer modes, including Highest. Default is unlimited.")
                .defineInRange("maxItemTransferNormal", MAX_TRANSFER_UNLIMITED, 1, MAX_TRANSFER_UNLIMITED);

        maxItemTransferAdvanced = SERVER_BUILDER
                .comment("Global maximum number of items transferred per operation by an advanced item connector. Applies to all item transfer modes, including Highest. Default is unlimited.")
                .defineInRange("maxItemTransferAdvanced", MAX_TRANSFER_UNLIMITED, 1, MAX_TRANSFER_UNLIMITED);

        maxFluidTransferNormal = SERVER_BUILDER
                .comment("Global maximum amount of fluid in mB transferred per operation by a normal fluid connector. Applies to all fluid transfer modes, including Highest. Default is unlimited.")
                .defineInRange("maxFluidTransferNormal", MAX_TRANSFER_UNLIMITED, 1, MAX_TRANSFER_UNLIMITED);

        maxFluidTransferAdvanced = SERVER_BUILDER
                .comment("Global maximum amount of fluid in mB transferred per operation by an advanced fluid connector. Applies to all fluid transfer modes, including Highest. Default is unlimited.")
                .defineInRange("maxFluidTransferAdvanced", MAX_TRANSFER_UNLIMITED, 1, MAX_TRANSFER_UNLIMITED);
        maxPublishedChannels = SERVER_BUILDER
                .comment("Maximum number of published channels that a routing channel can support")
                .defineInRange("maxPublishedChannels", 32, 1, 1000000000);

        controllerRFT = SERVER_BUILDER
                .comment("Power usage for the controller regardless of what it is doing")
                .defineInRange("controllerRFPerTick", 0, 0, 1000000000);
        controllerChannelRFT = SERVER_BUILDER
                .comment("Power usage for the controller per active channel")
                .defineInRange("controllerChannelRFT", 1, 0, 1000000000);
        controllerOperationRFT = SERVER_BUILDER
                .comment("Power usage for the controller per operation performed by one of the channels")
                .defineInRange("controllerOperationRFT", 2, 0, 1000000000);
        showNonFacadedCablesWhileSneaking = CLIENT_BUILDER
                .comment("If true then cables are also shown when sneaking even if they are not in a facade")
                .define("showNonFacadedCablesWhileSneaking", true);

        antennaTier1Range = SERVER_BUILDER
                .comment("Range for a tier 1 antenna")
                .defineInRange("antennaTier1Range", 100, 0, 1000000000);
        antennaTier2Range = SERVER_BUILDER
                .comment("Range for a tier 2 antenna")
                .defineInRange("antennaTier2Range", 500, 0, 1000000000);

        SERVER_BUILDER.pop();
        CLIENT_BUILDER.pop();
    }

    public static ConfigSpec SERVER_CONFIG;
    public static ConfigSpec CLIENT_CONFIG;
    public static Configuration mainConfig;

    public static void syncCachedValues() {
        maxItemTransferNormalCached = maxItemTransferNormal.get();
        maxItemTransferAdvancedCached = maxItemTransferAdvanced.get();
        maxFluidTransferNormalCached = maxFluidTransferNormal.get();
        maxFluidTransferAdvancedCached = maxFluidTransferAdvanced.get();

        itemTransferCapsEnabled =
                maxItemTransferNormalCached != MAX_TRANSFER_UNLIMITED ||
                        maxItemTransferAdvancedCached != MAX_TRANSFER_UNLIMITED;

        fluidTransferCapsEnabled =
                maxFluidTransferNormalCached != MAX_TRANSFER_UNLIMITED ||
                        maxFluidTransferAdvancedCached != MAX_TRANSFER_UNLIMITED;
    }

    public static void init() {
        mainConfig = new Configuration(new File(XNet.setup.getModConfigDir().getPath() + File.separator + "xnet", "xnet.cfg"));
        Configuration cfg = mainConfig;
        try {
            cfg.load();
            SERVER_CONFIG = SERVER_BUILDER.build(mainConfig);
            CLIENT_CONFIG = CLIENT_BUILDER.build(mainConfig);
            syncCachedValues();
        } catch (Exception e1) {
            FMLLog.log(Level.ERROR, e1, "Problem loading config file!");
        }
    }

    public static void postInit() {
        if (mainConfig.hasChanged()) {
            mainConfig.save();
        }
    }
}
