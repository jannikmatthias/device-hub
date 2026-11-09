package de.jannik.devicehub.backend.events;

import de.jannik.devicehub.backend.registry.DeviceState;

/** Wird veröffentlicht, wenn sich der Status eines Geräts ändert */
public record DeviceChangedEvent(DeviceState state) {}
