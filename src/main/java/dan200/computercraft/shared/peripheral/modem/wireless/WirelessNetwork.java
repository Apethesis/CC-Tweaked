/*
 * This file is part of ComputerCraft - http://www.computercraft.info
 * Copyright Daniel Ratcliffe, 2011-2022. Do not distribute without permission.
 * Send enquiries to dratcliffe@gmail.com
 */
package dan200.computercraft.shared.peripheral.modem.wireless;

import dan200.computercraft.api.network.IPacketNetwork;
import dan200.computercraft.api.network.IPacketReceiver;
import dan200.computercraft.api.network.Packet;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class WirelessNetwork implements IPacketNetwork {
    // TODO: Move this to ServerContext.
    private static @Nullable WirelessNetwork universalNetwork = null;

    public static WirelessNetwork getUniversal() {
        if (universalNetwork == null) universalNetwork = new WirelessNetwork();
        return universalNetwork;
    }

    public static void resetNetworks() {
        universalNetwork = null;
    }

    private final Set<IPacketReceiver> receivers = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @Override
    public void addReceiver(IPacketReceiver receiver) {
        Objects.requireNonNull(receiver, "device cannot be null");
        receivers.add(receiver);
    }

    @Override
    public void removeReceiver(IPacketReceiver receiver) {
        Objects.requireNonNull(receiver, "device cannot be null");
        receivers.remove(receiver);
    }

    @Override
    public void transmitSameDimension(Packet packet, double range) {
        Objects.requireNonNull(packet, "packet cannot be null");
        for (var device : receivers) tryTransmit(device, packet, range, false);
    }

    @Override
    public void transmitInterdimensional(Packet packet) {
        Objects.requireNonNull(packet, "packet cannot be null");
        for (var device : receivers) tryTransmit(device, packet, 0, true);
    }

    private static void tryTransmit(IPacketReceiver receiver, Packet packet, double range, boolean interdimensional) {
        var sender = packet.sender();
        if (receiver.getLevel() == sender.getLevel()) {
            var receiveRange = Math.max(range, receiver.getRange()); // Ensure range is symmetrical
            var distanceSq = receiver.getPosition().distanceToSqr(sender.getPosition());
            if (interdimensional || receiver.isInterdimensional() || distanceSq <= receiveRange * receiveRange) {
                receiver.receiveSameDimension(packet, Math.sqrt(distanceSq));
            }
        } else {
            if (interdimensional || receiver.isInterdimensional()) {
                receiver.receiveDifferentDimension(packet);
            }
        }
    }

    @Override
    public boolean isWireless() {
        return true;
    }
}
