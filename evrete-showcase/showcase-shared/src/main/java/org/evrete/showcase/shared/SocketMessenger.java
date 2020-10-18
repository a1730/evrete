package org.evrete.showcase.shared;

import javax.websocket.Session;
import java.io.IOException;

public class SocketMessenger {
    private final Session session;
    private int delay;

    public SocketMessenger(Session session) {
        this.session = session;
    }

    private void send(String text) throws IOException {
        session.getBasicRemote().sendText(text);
    }

    public void setDelay(int delay) {
        this.delay = delay;
    }

    public <T extends JsonMessage> void send(T message) throws IOException {
        send(Utils.toJson(message));
    }

    public <T extends JsonMessage> void sendDelayed(T message) throws IOException {
        Utils.delay(delay);
        send(message);
    }

    public void send(Exception e) throws IOException {
        send(Message.error(e.getMessage()));
    }
}