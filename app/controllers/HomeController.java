package controllers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import controllers.MyWebSocketActor;
import org.kurento.client.*;
import org.kurento.jsonrpc.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.mvc.*;

import views.html.*;
import akka.actor.*;
import play.libs.F.*;
import play.mvc.WebSocket;
import akka.stream.*;
import play.mvc.LegacyWebSocket;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This controller contains an action to handle HTTP requests
 * to the application's home page.
 */



public class HomeController extends Controller {

    /**
     * An action that renders an HTML page with a welcome message.
     * The configuration in the <code>routes</code> file means that
     * this method will be called when the application receives a
     * <code>GET</code> request with a path of <code>/</code>.
     */
    private static final Logger log = LoggerFactory.getLogger(HomeController.class);
    private static final Gson gson = new GsonBuilder().create();

    private final ConcurrentHashMap<Integer, UserSession> viewers = new ConcurrentHashMap<>();

    private KurentoClient kurento;

    private MediaPipeline pipeline;
    private UserSession presenterUserSession;

    public Result index() {
        return ok(index.render());
    }

    public LegacyWebSocket<String> socket() {

        return WebSocket.whenReady((in, out) -> {
            // For each event received on the socket,
            in.onMessage( message ->{
                JsonNode jsonMessage = null;
                try {
                    jsonMessage = new ObjectMapper().readTree(message.toString());

                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                try {
                    handleTextMessage(jsonMessage,out);
                    // handleTextMessage(jsonMessage,out);
                } catch (Exception e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            });

            // When the socket is closed.
            in.onClose(() -> System.out.println("Disconnected"));

        });
    }



    public void handleTextMessage(JsonNode jsonMessage, WebSocket.Out<String> out) throws Exception {
       // JsonObject jsonMessage = gson.fromJson(jsonMessage.getPayload(), JsonObject.class);
       // UserSession user = registry.getBySession(out);
       // out.write("testing the presenter");
      //  log.debug("Incoming message from session '{}': {}", session.getId(), jsonMessage);
        kurento = KurentoClient.create("ws://192.168.1.10:8888/kurento");
        switch (jsonMessage.get("id").asText()) {
            case "presenter":
                try {
                    presenter(out, jsonMessage);
                } catch (Throwable t) {
                    handleErrorResponse(t, out, "presenterResponse");
                }
                break;
            case "viewer":
                try {
                   viewer(out, jsonMessage);
                } catch (Throwable t) {
                   // handleErrorResponse(t, session, "viewerResponse");
                }
                break;
            case "onIceCandidate": {
                JsonNode candidate = jsonMessage.get("candidate");

                UserSession user = null;
                if (presenterUserSession != null) {
                    if (presenterUserSession.getSession() == out) {
                        user = presenterUserSession;
                    } else {
                        user = viewers.get(out.hashCode());
                    }
                }
                if (user != null) {
                    IceCandidate cand =
                            new IceCandidate(candidate.get("candidate").asText(), candidate.get("sdpMid")
                                    .asText(), candidate.get("sdpMLineIndex").asInt());
                    user.addCandidate(cand);
                }
                break;
            }
            case "stop":
                //stop(session);
                break;
            default:
                break;
        }
    }

    private void handleErrorResponse(Throwable throwable, WebSocket.Out<String> out, String responseId)
            throws IOException {
        stop(out);
        log.error(throwable.getMessage(), throwable);
        JsonObject response = new JsonObject();
        response.addProperty("id", responseId);
        response.addProperty("response", "rejected");
        response.addProperty("message", throwable.getMessage());
        out.write(response.toString());
    }

    private synchronized void stop(WebSocket.Out<String> out) throws IOException {
       //String sessionId = session.getId();
        if (presenterUserSession != null && presenterUserSession.getSession().equals(out)) {
            for (UserSession viewer : viewers.values()) {
                JsonObject response = new JsonObject();
                response.addProperty("id", "stopCommunication");
                viewer.sendMessage(response.toString());
            }

            log.info("Releasing media pipeline");
            if (pipeline != null) {
                pipeline.release();
            }
            pipeline = null;
            presenterUserSession = null;
        } else if (viewers.containsKey(out)) {
            if (viewers.get(out).getWebRtcEndpoint() != null) {
                viewers.get(out).getWebRtcEndpoint().release();
            }
            viewers.remove(out);
        }
    }


    private synchronized void presenter(WebSocket.Out<String> out, JsonNode jsonMessage)
            throws Exception {
        if (presenterUserSession == null) {
            presenterUserSession = new UserSession(out,"presenter");

            pipeline = kurento.createMediaPipeline();
            presenterUserSession.setWebRtcEndpoint(new WebRtcEndpoint.Builder(pipeline).build());

           // WebRtcEndpoint webRtcEndpoint = new WebRtcEndpoint.Builder(webRtcEndpointExist.getMediaPipeline()).build();
            WebRtcEndpoint presenterWebRtc = presenterUserSession.getWebRtcEndpoint();

            presenterWebRtc.addIceCandidateFoundListener(new EventListener<IceCandidateFoundEvent>() {

                public void onEvent(IceCandidateFoundEvent event) {
                    JsonObject response = new JsonObject();
                    response.addProperty("id", "iceCandidate");
                    response.add("candidate", JsonUtils.toJsonObject(event.getCandidate()));
                    try {
                        synchronized (presenterUserSession) {
                            presenterUserSession.sendMessage(response.toString());
                        }
                    } catch (Exception e) {
                        log.debug(e.getMessage());
                    }
                }
            });

            String sdpOffer = jsonMessage.get("sdpOffer").asText();
            String sdpAnswer = presenterWebRtc.processOffer(sdpOffer);

            JsonObject response = new JsonObject();
            response.addProperty("id", "presenterResponse");
            response.addProperty("response", "accepted");
            response.addProperty("sdpAnswer", sdpAnswer);

            synchronized (out) {
                out.write(response.toString());
            }
            presenterWebRtc.gatherCandidates();

        } else {
            JsonObject response = new JsonObject();
            response.addProperty("id", "presenterResponse");
            response.addProperty("response", "rejected");
            response.addProperty("message",
                    "Another user is currently acting as sender. Try again later ...");
            out.write(response.toString());
        }
    }

    private synchronized void viewer(WebSocket.Out<String> out, JsonNode jsonMessage)
            throws IOException {
        if (presenterUserSession == null || presenterUserSession.getWebRtcEndpoint() == null) {
            JsonObject response = new JsonObject();
            response.addProperty("id", "viewerResponse");
            response.addProperty("response", "rejected");
            response.addProperty("message",
                    "No active sender now. Become sender or . Try again later ...");
            out.write(response.toString());
        } else {
            if (viewers.containsKey(out.hashCode())) {
                JsonObject response = new JsonObject();
                response.addProperty("id", "viewerResponse");
                response.addProperty("response", "rejected");
                response.addProperty("message", "You are already viewing in this session. "
                        + "Use a different browser to add additional viewers.");
                out.write(response.toString());
                return;
            }
            UserSession viewer = new UserSession(out,"view");
            viewers.put(out.hashCode(), viewer);

            WebRtcEndpoint nextWebRtc = new WebRtcEndpoint.Builder(pipeline).build();

            nextWebRtc.addIceCandidateFoundListener(new EventListener<IceCandidateFoundEvent>() {

                @Override
                public void onEvent(IceCandidateFoundEvent event) {
                    JsonObject response = new JsonObject();
                    response.addProperty("id", "iceCandidate");
                    response.add("candidate", JsonUtils.toJsonObject(event.getCandidate()));
                    try {
                        synchronized (viewer) {
                            viewer.sendMessage(response.toString());
                        }
                    } catch (Exception e) {
                        log.debug(e.getMessage());
                    }
                }
            });

            viewer.setWebRtcEndpoint(nextWebRtc);
            presenterUserSession.getWebRtcEndpoint().connect(nextWebRtc);
            String sdpOffer = jsonMessage.get("sdpOffer").asText();
            String sdpAnswer = nextWebRtc.processOffer(sdpOffer);

            JsonObject response = new JsonObject();
            response.addProperty("id", "viewerResponse");
            response.addProperty("response", "accepted");
            response.addProperty("sdpAnswer", sdpAnswer);

            synchronized (viewer) {
                viewer.sendMessage(response.toString());
            }
            nextWebRtc.gatherCandidates();
        }
    }





}


