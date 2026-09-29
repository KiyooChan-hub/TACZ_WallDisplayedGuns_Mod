package dev.kiyo.wallgun.mixin;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(ServerGamePacketListenerImpl.class)
public interface LoadingConnectionAccess {
    @Accessor("clientIsFloating") void wallgun$clientFloating(boolean value);
    @Accessor("clientVehicleIsFloating") void wallgun$vehicleFloating(boolean value);
    @Accessor("aboveGroundTickCount") void wallgun$groundTicks(int value);
    @Accessor("aboveGroundVehicleTickCount") void wallgun$vehicleGroundTicks(int value);
}
