package mcjty.xnet.modules.cables.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record ConnectorData(String name, byte enabled) {

    public static final ConnectorData EMPTY = new ConnectorData("", (byte)0x3f);

    public static final Codec<ConnectorData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("name").forGetter(ConnectorData::name),
            Codec.BYTE.fieldOf("enabled").forGetter(ConnectorData::enabled)
    ).apply(instance, ConnectorData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConnectorData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ConnectorData::name,
            ByteBufCodecs.BYTE, ConnectorData::enabled,
            ConnectorData::new
    );

    public ConnectorData withEnabled(byte enabled) {
        return new ConnectorData(name, enabled);
    }

    public ConnectorData withName(String name) {
        return new ConnectorData(name, enabled);
    }
}
