package de.sperker.websocket.conversational.server;

import de.sperker.websocket.conversational.business.CliBoundary;
import de.sperker.websocket.conversational.business.SocketBoundary;
import de.sperker.websocket.conversational.CustomSpringConfigurator;
import de.sperker.websocket.conversational.model.AppSession;
import de.sperker.websocket.conversational.model.User;
import org.springframework.beans.factory.annotation.Autowired;

import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@ServerEndpoint(value = "/ws", configurator = CustomSpringConfigurator.class)
public class WebsocketEndpoint implements SocketBoundary {

    private Map<String, AppSession> sessionIndex = new HashMap();

    @Autowired
    private CliBoundary cliBoundary;

    public Map<String, AppSession> getSessionIndex(){
        return sessionIndex;
    }


    @OnOpen
    public void onOpen(Session session) {
        String clientId = session.getId();
        session.getUserProperties().putIfAbsent("clientId", clientId);
        AppSession appSession = new AppSession(session, new User(clientId, ""), new Date());
        sessionIndex.put(clientId, appSession);
        this.cliBoundary.printMessage(String.format("[%s]: connected", clientId));
    }

    private void removeClient(String clientId) {
        sessionIndex.remove(clientId);
        this.cliBoundary.printMessage(String.format("[%s]: disconnected", clientId));
    }

    private void updateActiveTime(String clientId){
        sessionIndex.get(clientId).setLastActive(new Date());
    }

    @OnMessage
    public void handleMessage(Session session, String inboundMessage) {
        String clientId = (String) session.getUserProperties().get("clientId");
        this.updateActiveTime(clientId);

        sendMessageToClientsExceptOne(inboundMessage, clientId);

        this.cliBoundary.printMessage(String.format("[%s]: %s", clientId, inboundMessage));
    }

    @OnClose
    public void onClose(Session session) {
        String clientId = (String) session.getUserProperties().get("clientId");
        removeClient(clientId);
        this.cliBoundary.printMessage(String.format("[%s]: closing connection", clientId));
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        String clientId = (String) session.getUserProperties().get("clientId");
        removeClient(clientId);
        this.cliBoundary.printMessage(String.format("[%s]: error [%s]", clientId, throwable.toString()));
    }

    @Override
    public Set<String> getClients() {
        return this.sessionIndex.keySet();
    }

    @Override
    public void sendMessageToClients(String message) {
        this.sessionIndex.forEach((clientId, appSession) -> {
            Session session = appSession.getSession();
            try {
                session.getBasicRemote().sendText(message);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private void sendMessageToClientsExceptOne(String message, String senderClientId) {
        this.sessionIndex.forEach((clientId, appSession) -> {
            Session session = appSession.getSession();
            if(!clientId.equals(senderClientId)) {
                try {
                    session.getBasicRemote().sendText(message);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    @Override
    public void sendMessageToClient(String clientId, String message) throws IOException {
        this.sessionIndex.get(clientId).getSession().getBasicRemote().sendText(message);
    }
}