package dev.neurath.core;

import java.util.UUID;

/**
 * Explicit transfer or cross-dimension portal link between two stations (e.g. Overworld <-> Nether Hub).
 */
public final class TransferLink {
    public String id;
    public String fromStationId;
    public String toStationId;
    public String label = "Transfer";
    public boolean crossDimension;

    public TransferLink() {
        this.id = UUID.randomUUID().toString();
    }

    public TransferLink(String fromStationId, String toStationId, String label, boolean crossDimension) {
        this.id = UUID.randomUUID().toString();
        this.fromStationId = fromStationId;
        this.toStationId = toStationId;
        this.label = (label == null || label.isBlank()) ? (crossDimension ? "Portal Link" : "Transfer") : label;
        this.crossDimension = crossDimension;
    }

    public boolean connects(String a, String b) {
        return (fromStationId != null && toStationId != null)
                && ((fromStationId.equals(a) && toStationId.equals(b))
                || (fromStationId.equals(b) && toStationId.equals(a)));
    }
}
