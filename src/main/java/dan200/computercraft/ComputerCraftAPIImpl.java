/*
 * This file is part of ComputerCraft - http://www.computercraft.info
 * Copyright Daniel Ratcliffe, 2011-2022. Do not distribute without permission.
 * Send enquiries to dratcliffe@gmail.com
 */
package dan200.computercraft;

import com.google.auto.service.AutoService;
import dan200.computercraft.api.ComputerCraftAPI;
import dan200.computercraft.api.detail.BlockReference;
import dan200.computercraft.api.detail.DetailRegistry;
import dan200.computercraft.api.detail.IDetailProvider;
import dan200.computercraft.api.filesystem.IMount;
import dan200.computercraft.api.filesystem.IWritableMount;
import dan200.computercraft.api.lua.GenericSource;
import dan200.computercraft.api.lua.ILuaAPIFactory;
import dan200.computercraft.api.media.IMediaProvider;
import dan200.computercraft.api.network.IPacketNetwork;
import dan200.computercraft.api.network.wired.IWiredElement;
import dan200.computercraft.api.network.wired.IWiredNode;
import dan200.computercraft.api.peripheral.IPeripheralProvider;
import dan200.computercraft.api.redstone.IBundledRedstoneProvider;
import dan200.computercraft.api.turtle.TurtleRefuelHandler;
import dan200.computercraft.core.apis.ApiFactories;
import dan200.computercraft.core.asm.GenericMethod;
import dan200.computercraft.core.filesystem.FileMount;
import dan200.computercraft.impl.ComputerCraftAPIForgeService;
import dan200.computercraft.impl.ComputerCraftAPIService;
import dan200.computercraft.impl.TurtleRefuelHandlers;
import dan200.computercraft.impl.detail.DetailRegistryImpl;
import dan200.computercraft.shared.BundledRedstone;
import dan200.computercraft.shared.MediaProviders;
import dan200.computercraft.shared.Peripherals;
import dan200.computercraft.shared.computer.core.ResourceMount;
import dan200.computercraft.shared.computer.core.ServerContext;
import dan200.computercraft.shared.peripheral.generic.GenericPeripheralProvider;
import dan200.computercraft.shared.peripheral.generic.data.BlockData;
import dan200.computercraft.shared.peripheral.generic.data.FluidData;
import dan200.computercraft.shared.peripheral.generic.data.ItemData;
import dan200.computercraft.shared.peripheral.modem.wireless.WirelessNetwork;
import dan200.computercraft.shared.wired.WiredNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.server.ServerLifecycleHooks;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import static dan200.computercraft.shared.Capabilities.CAPABILITY_WIRED_ELEMENT;

@AutoService(ComputerCraftAPIService.class)
public final class ComputerCraftAPIImpl implements ComputerCraftAPIForgeService {
    private final DetailRegistry<ItemStack> itemStackDetails = new DetailRegistryImpl<>(ItemData::fillBasic);
    private final DetailRegistry<BlockReference> blockDetails = new DetailRegistryImpl<>(BlockData::fillBasic);
    private final DetailRegistry<FluidStack> fluidStackDetails = new DetailRegistryImpl<>(FluidData::fillBasic);

    private @Nullable String version;

    public static @Nullable InputStream getResourceFile(MinecraftServer server, String domain, String subPath) {
        var manager = server.getResourceManager();
        var resource = manager.getResource(new ResourceLocation(domain, subPath)).orElse(null);
        if (resource == null) return null;
        try {
            return resource.open();
        } catch (IOException ignored) {
            return null;
        }
    }

    @Override
    public String getInstalledVersion() {
        if (version != null) return version;
        return version = ModList.get().getModContainerById(ComputerCraftAPI.MOD_ID)
            .map(x -> x.getModInfo().getVersion().toString())
            .orElse("unknown");
    }

    @Override
    public int createUniqueNumberedSaveDir(Level world, String parentSubPath) {
        var server = world.getServer();
        if (server == null) throw new IllegalArgumentException("Cannot find server from provided level");
        return ServerContext.get(server).getNextId(parentSubPath);
    }

    @Override
    public @Nullable IWritableMount createSaveDirMount(Level world, String subPath, long capacity) {
        var server = world.getServer();
        if (server == null) throw new IllegalArgumentException("Cannot find server from provided level");

        try {
            return new FileMount(new File(ServerContext.get(server).storageDir().toFile(), subPath), capacity);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public @Nullable IMount createResourceMount(String domain, String subPath) {
        var manager = ServerLifecycleHooks.getCurrentServer().getResourceManager();
        var mount = ResourceMount.get(domain, subPath, manager);
        return mount.exists("") ? mount : null;
    }

    @Override
    public void registerPeripheralProvider(IPeripheralProvider provider) {
        Peripherals.register(provider);
    }

    @Override
    public void registerGenericSource(GenericSource source) {
        GenericMethod.register(source);
    }

    @Override
    public void registerGenericCapability(Capability<?> capability) {
        GenericPeripheralProvider.addCapability(capability);
    }

    @Override
    public void registerBundledRedstoneProvider(IBundledRedstoneProvider provider) {
        BundledRedstone.register(provider);
    }

    @Override
    public int getBundledRedstoneOutput(Level world, BlockPos pos, Direction side) {
        return BundledRedstone.getDefaultOutput(world, pos, side);
    }

    @Override
    public void registerMediaProvider(IMediaProvider provider) {
        MediaProviders.register(provider);
    }

    @Override
    public IPacketNetwork getWirelessNetwork() {
        return WirelessNetwork.getUniversal();
    }

    @Override
    public void registerAPIFactory(ILuaAPIFactory factory) {
        ApiFactories.register(factory);
    }

    @Override
    @Deprecated
    @SuppressWarnings("unchecked")
    public <T> void registerDetailProvider(Class<T> type, IDetailProvider<T> provider) {
        if (type == ItemStack.class) {
            itemStackDetails.addProvider((IDetailProvider<ItemStack>) provider);
        } else if (type == BlockReference.class) {
            blockDetails.addProvider((IDetailProvider<BlockReference>) provider);
        } else if (type == FluidStack.class) {
            itemStackDetails.addProvider((IDetailProvider<ItemStack>) provider);
        } else {
            throw new IllegalArgumentException("Unknown detail provider " + type);
        }
    }

    @Override
    public IWiredNode createWiredNodeForElement(IWiredElement element) {
        return new WiredNode(element);
    }

    @Override
    public LazyOptional<IWiredElement> getWiredElementAt(BlockGetter world, BlockPos pos, Direction side) {
        var tile = world.getBlockEntity(pos);
        return tile == null ? LazyOptional.empty() : tile.getCapability(CAPABILITY_WIRED_ELEMENT, side);
    }

    @Override
    public void registerRefuelHandler(TurtleRefuelHandler handler) {
        TurtleRefuelHandlers.register(handler);
    }

    @Override
    public DetailRegistry<ItemStack> getItemStackDetailRegistry() {
        return itemStackDetails;
    }

    @Override
    public DetailRegistry<BlockReference> getBlockInWorldDetailRegistry() {
        return blockDetails;
    }

    @Override
    public DetailRegistry<FluidStack> getFluidStackDetailRegistry() {
        return fluidStackDetails;
    }
}
