# Device-Hub

Ein kleiner Device-Hub: Simulierte Geräte halten eine dauerhafte WebSocket-Verbindung zum Backend,
schicken regelmäßig ihren Status und einen Messwert und können vom Backend Befehle empfangen.
Ein Dashboard im Browser zeigt alle Geräte live (online/offline, letzter Messwert) und kann das
Messintervall eines Geräts ändern.

## Voraussetzungen

- Java 21 oder neuer
- Kein lokales Maven nötig, der Maven Wrapper (`mvnw` / `mvnw.cmd`) liegt im Repo

## Starten

Alle Befehle im Projektverzeichnis.

**1. Einmalig bauen**:

```bash
./mvnw install -DskipTests
```

**2. Backend starten** (Port 8080):

```bash
./mvnw -pl backend spring-boot:run
```

**3. Geräte-Simulator starten** (in einem zweiten Terminal):

```bash
./mvnw -pl devices exec:java
```

Standardmäßig starten drei Geräte (`sensor-1` bis `sensor-3`) gegen `ws://localhost:8080/ws/devices`.
Anzahl und Backend-URL lassen sich übergeben:

```bash
./mvnw -pl devices exec:java -Dexec.args="5 ws://localhost:8080/ws/devices"
```

**4. Dashboard öffnen:** <http://localhost:8080>

Über den Button „Intervall ändern“ bekommt das Gerät einen `setInterval`-Befehl (POST-Request). Alternativ per HTTP
(siehe auch [`request.http`](request.http)):

```bash
curl -X POST -H "Content-Type: application/json" -d '{"intervalMs":1000}' http://localhost:8080/devices/sensor-1/commands/set-interval
```

### Ausfall eines Geräts simulieren

`FailedDeviceSimulator` startet ein Gerät `sensor-fail`, das nach 10 Sekunden keine Nachrichten mehr
schickt, die Verbindung aber offen lässt. Nach dem Heartbeat-Timeout (30 s) erscheint es im
Dashboard als offline. Das Backend schließt daraufhin die Verbindung und der Simulator beendet sich.

```bash
./mvnw -pl devices exec:java "-Dexec.mainClass=de.jannik.devicehub.devices.FailedDeviceSimulator"
```

### Tests

```bash
./mvnw test
```

## Architektur

```
                                    +== backend ===========================================================+
                                    :                                                                      :
+-----------------+                 :  +--------------------------+               +---------------------+  :
| SimulatedDevice |                 :  | DeviceWebSocketHandler   |               | DeviceController    |  :
|                 |  hello, status, :  | WS /ws/devices           | sendCommand() | POST /devices/      |  : POST (Button)
| (Modul devices) |  measurement    :  |                          |<------------- | {id}/commands/      |<------------+
|                 | ------------------>| Session <-> deviceId     |               | set-interval        |  :          |
|                 | <------------------|                          |               +---------------------+  :          |
+-----------------+  command        :  +--------------------------+                                        :          |
                                    :     |                     ^                                          :          |
                                    :     | setLastMeasurement()| DeviceTimedOutEvent                      :          |
                                    :     | setIntervalMs()     | -> Session schließen                     :          |
                                    :     | setIsOnline()       |                                          :    +------------+
                                    :     v                     |                                          :    | Browser    |
                                    :  +--------------------------+                                        :    | index.html |
                                    :  | DeviceRegistry           |  DeviceChangedEvent                    :    |            |
                                    :  | Zustand aller Geräte     |-------------------------+              :    +------------+
                                    :  | Heartbeat-Check          |                         |              :          ^
                                    :  +--------------------------+                         |              :          |
                                    :                                                       v              :          |
                                    :                                             +---------------------+  :          |
                                    :                                             | DashboardController |  :          |
                                    :                                             | GET /devices        |-------------+
                                    :                                             | GET /devices/stream |  : JSON + SSE
                                    :                                             +---------------------+  :
                                    :                                                                      :
                                    +======================================================================+
```


**`DeviceWebSocketHandler`** empfängt alle Gerätenachrichten (`hello`, `status`, `measurement`)
  über den WebSocket und gibt sie an die `DeviceRegistry` weiter. Er merkt sich anhand der Hello Nachricht, welche Session zu
  welcher Geräte-ID gehört, und schickt darüber Befehle an die Geräte.

**`DeviceRegistry`** hält den Zustand aller Geräte (`DeviceState`, unveränderliche Records in einer
  `ConcurrentHashMap`) und prüft per `@Scheduled`, ob ein Gerät innerhalb der letzten 30 Sekunden 
  eine Nachricht gesendet hat, wenn nicht, wird ein `DeviceTimedOutEvent` gemeldet und der Handler schließt daraufhin die Session.
  Jede Änderung wird als `DeviceChangedEvent` an den DashboardController gesendet. 

**`DashboardController`** ist für das Dashboard zuständig. `GET /devices` liefert eine
  Momentaufnahme, `GET /devices/stream` schickt jede Änderung (bei `DeviceChangedEvent`) als Server-Sent Event an den Browser.

**`DeviceController`** nimmt den Befehl vom Button entgegen und lässt ihn über den Handler an das
  Gerät senden. Antworten: `202` = gesendet, `400` = Intervall < 500 ms, `404` = Gerät nicht verbunden.

Das Projekt ist ein Maven-Multi-Modul-Build:

| Modul      | Inhalt |
|------------|--------|
| `protocol` | Gemeinsame Nachrichtentypen (`Message`, sealed interface mit Records), von Backend und Geräten genutzt |
| `devices`  | Geräte-Simulator auf Basis des JDK-`HttpClient`-WebSockets, ohne Spring |
| `backend`  | Spring Boot: WebSocket-Endpunkt für Geräte, REST-API für Befehle, SSE-Stream und statisches Dashboard |

Für das Dashboard wird SSE statt eines zweiten WebSockets verwendet: Die Daten fließen nur in eine
Richtung, und `EventSource` verbindet sich im Browser von selbst neu.

## Protokoll

Alle Nachrichten sind JSON-Textframes. Das Feld `type` bestimmt den Nachrichtentyp.

**Gerät → Backend**

```json
{"type":"hello","deviceId":"sensor-1"}
```
Erste Nachricht nach dem Verbindungsaufbau. Damit wird die Session dem Gerät zugeordnet.

```json
{"type":"status","deviceId":"sensor-1","intervalMs":3000}
```
Aktuelles Messintervall. Wird alle 10 s und nach jeder Intervalländerung gesendet.

```json
{"type":"measurement","deviceId":"sensor-1","value":20.13,"timestamp":"2026-11-09T12:00:00Z"}
```
Simulierter Messwert, einmal pro Messintervall.

**Backend → Gerät**

```json
{"type":"command","name":"setInterval","intervalMs":1000}
```
Ändert das Messintervall. Das Gerät antwortet mit einer neuen `status`-Nachricht.

