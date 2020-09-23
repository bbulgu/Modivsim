import java.io.*;
import java.net.Socket;

public class ClientHandler extends Thread {
    ObjectOutputStream out;
    ObjectInputStream in;
    final Socket s;
    int clientID;
    int serverID;
    Node clientNode;


    // Constructor
    public ClientHandler(Socket s,ObjectInputStream in, ObjectOutputStream out, int clientID, int serverID, Node clientNode) throws IOException {
        this.s = s;
        this.clientID = clientID;
        this.serverID = serverID;
        this.clientNode = clientNode;

        this.out = out;
        this.in = in;


        //System.out.println("created server " + serverID + " conn to " + clientID);
        //System.out.println("got msg " + dis.readUTF());
    }

    public void sendMessage(Message msg) throws IOException {
        //System.out.println("in clienthandler writing " + msg);
        out.writeObject(msg);
        out.flush();
        out.reset();
    }

    public void receiveMessage() throws IOException, ClassNotFoundException {
        Message m = (Message) in.readObject();
        //System.out.println("got message " + m);
        if (m.changed){
            clientNode.receiveUpdate(m);
        }
        //System.out.println("recieved " + m);
    }

}
