/*
 * This file is part of the public ComputerCraft API - http://www.computercraft.info
 * Copyright Daniel Ratcliffe, 2011-2022. This API may be redistributed unmodified and in full only.
 * For help using the API, and posting your mods, visit the forums at computercraft.info.
 */
package dan200.computercraft.api.lua;

import dan200.computercraft.api.peripheral.GenericPeripheral;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.resources.ResourceLocation;

/**
 * A generic source of {@link LuaFunction} functions.
 * <p>
 * Unlike normal objects ({@link IDynamicLuaObject} or {@link IPeripheral}), methods do not target this object but
 * instead are defined as {@code static} and accept their target as the first parameter. This allows you to inject
 * methods onto objects you do not own, as well as declaring methods for a specific "trait" (for instance, a Forge
 * capability or Fabric block lookup interface).
 * <p>
 * Currently the "generic peripheral" system is incompatible with normal peripherals. Peripherals explicitly provided
 * by capabilities/the block lookup API take priority. Block entities which use this system are given a peripheral name
 * determined by their id, rather than any peripheral provider, though additional types may be provided by overriding
 * {@link GenericPeripheral#getType()}.
 * <p>
 * For example, the main CC: Tweaked mod defines a generic source for inventories, which works on {@code IItemHandler}s:
 *
 * <pre>{@code
 * public class InventoryMethods implements GenericSource {
 *     \@LuaFunction( mainThread = true )
 *     public static int size(IItemHandler inventory) {
 *         return inventory.getSlots();
 *     }
 *
 *     // ...
 * }
 * }</pre>
 * <p>
 * New capabilities or block lookups (those not built into Forge/Fabric) must be explicitly registered using the
 * loader-specific API.
 *
 * @see dan200.computercraft.api.ComputerCraftAPI#registerGenericSource(GenericSource)
 */
public interface GenericSource {
    /**
     * A unique identifier for this generic source.
     * <p>
     * This is currently unused, but may be used in the future to allow disabling specific sources. It is recommended
     * to return an identifier using your mod's ID.
     *
     * @return This source's identifier.
     */
    ResourceLocation id();
}
