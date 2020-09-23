import java.awt.*;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import static java.util.concurrent.TimeUnit.*;

import javafx.util.Pair;

import javax.swing.*;

public class Node extends Thread {
    private static int getRandomNumberInRange(int min, int max) {

        if (min >= max) {
            throw new IllegalArgumentException("max must be greater than min");
        }

        Random r = new Random();
        return r.nextInt((max - min) + 1) + min;
    }



    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    HashSet<Integer> neighbourIds=new HashSet<>();
     int nodeID;
    HashMap<Integer, Integer> linkCost; // id, linkcost
    HashMap<Integer, Integer> linkBandwith; // id, linkbandwith
    int[][] distanceTable; //[i,j] is cost to node i via j
    int[] bottleneckBandwithTable;
    int[] distanceVector; // shortest distance to any given node
    private int numNodes;
    ArrayList<Integer> dynamicLinks;
    Socket s;
    ServerSocket ss;
    ObjectInputStream ois;
    ObjectOutputStream oos;
    boolean changed = true;
    ArrayList<ClientHandler> ClientPeers = new ArrayList<>();
    ArrayList<ClientHandler> ServerPeers = new ArrayList<>();

    HashMap<String, Pair<Integer, Integer>> forwardingTable;

    private int port;

    JTextArea textArea;

    Instant instant = Instant.now();
    long start = instant.getEpochSecond();

    boolean converged = false;


    public Node(int nodeID, HashMap<Integer, Integer> linkCost, HashMap<Integer, Integer> linkBandwidth, int numNodes,ArrayList<Integer> dynamicLinks) {
        this.nodeID = nodeID;
        this.linkCost = linkCost;
        this.linkBandwith = linkBandwidth;
        this.numNodes = numNodes;
        this.dynamicLinks=dynamicLinks;
        distanceTable = new int[numNodes][numNodes];
        for (int i = 0; i < numNodes; i++) {
            for (int j = 0; j < numNodes; j++) {
                distanceTable[i][j] = 999; // set everything to inf
            }
        }
        distanceTable[nodeID][nodeID] = 0;
        distanceVector = new int[numNodes];
        for (int i = 0; i < numNodes; i++) {
            distanceVector[i] = 999;
        }
        linkCost.forEach((k, v) -> {
            distanceTable[k][k] = v;
            distanceVector[k] = v;
        });
        distanceVector[nodeID] = 0;


        bottleneckBandwithTable = new int[numNodes];

        port = 12345 + nodeID;


        JFrame frame = new JFrame("Node " + nodeID);
        JPanel p = new JPanel();

        frame.setLocation(new Point(300*nodeID, 300*nodeID));
        frame.setPreferredSize(new Dimension(1000,200));
        frame.setMaximumSize(new Dimension(1000,200));
        frame.setMinimumSize(new Dimension(1000,200));
        //frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        p.setLayout(new BoxLayout(p,1));

        //JPanel mainPanel = new JPanel();
        textArea = new JTextArea();
        p.add(textArea);
        frame.add(p);

        //mainPanel.setLayout(new BorderLayout());
        //frame.getContentPane().add(mainPanel);

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setLocationByPlatform(true);
        frame.setVisible(true);


    }

    public void setConverged(boolean converged) {
        this.converged = converged;
    }

    @Override
    public void run() {
        try {
            ss = new ServerSocket(port);
        } catch (Exception e) {
            System.out.println(e);
        }

        scheduleOperation();

    }

    public synchronized void scheduleOperation() {
        final Runnable updater = new Runnable() {
            public void run() {
                try {
                    sendReceiveMessages();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }
        };
        final ScheduledFuture<?> updaterHandle = scheduler.scheduleAtFixedRate(updater, 4, 3, SECONDS);
    }

    protected void sendReceiveMessages() throws IOException, ClassNotFoundException {
        // System.out.print("schedule works.");

        //change dynamic links if necessary
        for (int neighbourId: dynamicLinks){
            boolean willChange = getRandomNumberInRange(1,2)==1;
            if (willChange){
                changed=true;
                int newCost=getRandomNumberInRange(1,10);
                //System.out.println("In node "+nodeID+ "new random cost is "+newCost);
                linkCost.put(neighbourId,newCost);
                //update distance vector
                distanceTable[neighbourId][neighbourId]=newCost;
                distanceVector[neighbourId]=Arrays.stream(distanceTable[neighbourId]).min().getAsInt();
            }
        }


        //send update to all peers
        //System.out.println("sending updates " + nodeID);
        sendUpdate();

        //receive update from all peers
        //System.out.println("getting updates " + nodeID);
        recieveMessages();

    }

    public void establishConnections() throws IOException {
        // first connect to smaller id neighbors as client


        linkCost.forEach((k, v) -> {
            if (k < nodeID) {
                try {
                    Socket socket = new Socket("localhost", 12345 + k);
                    ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                    ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

                    Thread t = new ClientHandler(socket, in, out, nodeID, k, this);
                    ClientPeers.add((ClientHandler) t);
                    //System.out.println("added " + t);

                    t.start();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                //ConnectionHandler conn = new ConnectionHandler(12345+k,nodeID,k,this);

            }

        });

        // then wait for higher id neighbors as server
        linkCost.forEach((k, v) -> {
            if (k > nodeID) {
                try {
                    Socket s = ss.accept();
                    ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream());
                    ObjectInputStream in = new ObjectInputStream(s.getInputStream());
                    // create a new thread object
                    Thread t = new ClientHandler(s, in, out, k, nodeID, this);
                    //System.out.println("added "+t);
                    ServerPeers.add((ClientHandler) t);
                    // Invoking the start() method
                    t.start();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });


    }

    public void receiveUpdate(Message m) {
        //changed = false;
        this.neighbourIds.add(m.senderNodeID);
        /*
        for (int i = 0; i < numNodes; i++) {
            if (m.senderDistanceVector[i] + distanceVector[m.senderNodeID] < distanceVector[i]) {
                changed = true;
                distanceVector[i] = m.senderDistanceVector[i] + distanceVector[m.senderNodeID];
                // distanceTable[i][m.senderNodeID] = m.senderDistanceVector[i] + distanceVector[m.senderNodeID];
            }
        }

         */
        for (int i = 0; i < numNodes; i++) {
            if (i != nodeID)
                distanceTable[i][m.senderNodeID] = m.senderDistanceVector[i] + linkCost.get(m.senderNodeID);
        }
        for (int i=0; i<numNodes; i++){
            int temp= Arrays.stream(distanceTable[i]).min().getAsInt();
            if (temp != distanceVector[i]){
                distanceVector[i] = temp;
                changed=true;
            }
        }


        //System.out.println(nodeID + ": " + Arrays.toString(distanceVector));
        //System.out.println(nodeID + ": " + Arrays.deepToString(distanceTable));
    }

    public void recieveMessages() throws IOException, ClassNotFoundException {
        changed = false;
        for (ClientHandler connectionHandler : ClientPeers) {
            connectionHandler.receiveMessage();
        }
        for (ClientHandler clientHandler : ServerPeers) {
            clientHandler.receiveMessage();
        }

        // after having received messages, update the window
        instant = Instant.now();
        Long time = instant.getEpochSecond() - start;
        textArea.setText(String.format("In node %d at time %d\nLink costs: %s\nDistance vector: %s\nDistance table: %s\n", nodeID, time, linkCost,
                Arrays.toString(distanceVector), Arrays.deepToString(distanceTable)));
        if (converged){
            textArea.setText(String.format("In node %d at time %d, converged\nLink costs: %s\nDistance vector: %s\nDistance table: %s\nForwarding table: %s",
                    nodeID, time, linkCost, Arrays.toString(distanceVector), Arrays.deepToString(distanceTable), forwardingTable));
        }

        //textArea.setText("in node " + nodeID  + "at time t = " + time + "\n" + "distance vector is:" + Arrays.toString(distanceVector));
    }

    public boolean sendUpdate() throws IOException {
        for (ClientHandler connectionHandler : ClientPeers) {
            Message msg;
            if (changed) {
                msg = new Message(nodeID, connectionHandler.serverID, distanceVector, true);
            } else {
                msg = new Message(nodeID, connectionHandler.serverID, null, false);
            }
            connectionHandler.sendMessage(msg);
            //System.out.println(msg);
        }

        for (ClientHandler clientHandler : ServerPeers) {
            Message msg;
            if (changed) {
                msg = new Message(nodeID, clientHandler.clientID, distanceVector, true);
            } else {
                msg = new Message(nodeID, clientHandler.clientID, null, false);
            }

            clientHandler.sendMessage(msg);
            //System.out.println(msg);
        }
        return changed;
    }

    public HashMap<String, Pair<Integer, Integer>> getForwardingTable() {
        return forwardingTable;
    }

}