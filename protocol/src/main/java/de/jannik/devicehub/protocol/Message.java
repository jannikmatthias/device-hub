package de.jannik.devicehub.protocol;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.time.Instant;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = Message.Hello.class,       name = "hello"),
        @JsonSubTypes.Type(value = Message.Status.class,      name = "status"),
        @JsonSubTypes.Type(value = Message.Measurement.class, name = "measurement"),
        @JsonSubTypes.Type(value = Message.Command.class,     name = "command")
})
public sealed interface Message {

    record Hello(String deviceId) implements Message {}
    record Status(String deviceId, long intervalMs) implements Message {}
    record Measurement(String deviceId, double value, Instant timestamp) implements Message {}
    record Command(String name, long intervalMs) implements Message {}
}