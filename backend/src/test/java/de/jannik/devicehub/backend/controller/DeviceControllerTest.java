package de.jannik.devicehub.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.jannik.devicehub.backend.websocket.DeviceWebSocketHandler;
import de.jannik.devicehub.protocol.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DeviceControllerTest {

    private DeviceWebSocketHandler handler;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        handler = mock(DeviceWebSocketHandler.class);
        mvc = MockMvcBuilders.standaloneSetup(new DeviceController(handler)).build();
    }

    @Test
    void minimumIntervalIsSentToDevice() throws Exception {
        when(handler.sendCommand(any(), any())).thenReturn(true);

        setInterval("sensor-1", "{\"intervalMs\":" + DeviceController.MIN_INTERVAL_MS + "}")
                .andExpect(status().isAccepted());

        verify(handler).sendCommand("sensor-1",
                new Message.Command("setInterval", DeviceController.MIN_INTERVAL_MS));
    }

    @Test
    void intervalBelowMinimumIsRejectedWithoutSendingCommand() throws Exception {
        setInterval("sensor-1", "{\"intervalMs\":" + (DeviceController.MIN_INTERVAL_MS - 1) + "}")
                .andExpect(status().isBadRequest());

        verifyNoInteractions(handler);
    }

    @Test
    void missingIntervalIsRejectedWithoutSendingCommand() throws Exception {
        setInterval("sensor-1", "{}")
                .andExpect(status().isBadRequest());

        verifyNoInteractions(handler);
    }

    @Test
    void commandToDisconnectedDeviceReturns404() throws Exception {
        when(handler.sendCommand(any(), any())).thenReturn(false);

        setInterval("sensor-1", "{\"intervalMs\":1000}")
                .andExpect(status().isNotFound());
    }

    private ResultActions setInterval(String deviceId, String body) throws Exception {
        return mvc.perform(post("/devices/{id}/commands/set-interval", deviceId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
