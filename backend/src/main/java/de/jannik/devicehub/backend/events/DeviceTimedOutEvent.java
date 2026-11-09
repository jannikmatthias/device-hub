package de.jannik.devicehub.backend.events;

/** Wird veröffentlicht, wenn ein Gerät wegen ausbleibender Nachrichten offline gesetzt wurde. */
public record DeviceTimedOutEvent(String deviceId) {}
