import java.io.Serializable;

public class NetworkMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    private final MessageType type;
    private final Serializable payload;

    public NetworkMessage(MessageType type, Serializable payload) {
        this.type = type;
        this.payload = payload;
    }

    public MessageType getType() {
        return type;
    }

    public Serializable getPayload() {
        return payload;
    }

    @Override
    public String toString() {
        return "NetworkMessage(" + type + (payload == null ? "" : ", " + payload) + ")";
    }
}
