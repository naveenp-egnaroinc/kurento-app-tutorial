package controllers;
import akka.actor.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import controllers.HomeController;
import java.io.IOException;

public class MyWebSocketActor extends UntypedActor {

    public static Props props(ActorRef out) {
        return Props.create(MyWebSocketActor.class, out);
    }

    private final ActorRef out;

    public MyWebSocketActor(ActorRef out) {
        this.out = out;
    }

    public void onReceive(Object message) throws Exception {
        if (message instanceof String) {
            System.out.println(message.toString());
            JsonNode jsonMessage = null;
            try {
                jsonMessage = new ObjectMapper().readTree(message.toString());
               // handleTextMessage(jsonMessage,out);
            } catch (IOException e) {
                e.printStackTrace();
            }
            out.tell("I received your message: " + jsonMessage.get("text"), self());
        }
    }
}