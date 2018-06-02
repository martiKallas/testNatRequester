import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;


public class Main {
    static String USERNAME = "receivertester";
    static String PASSWORD = "123";
    static String FRIENDNAME = "marti";
    static UserData user;
    static int REQ_LISTEN_REFRESH = 5*1000;
    static int MAX_REQUEST_DIFF = 10*1000;
    static ConnectionManager manager;
    static int port = 9000;
    static private ArrayList<ChatRequest> chatRequests = new ArrayList<>();
    static private ArrayList<PeerConnection> connections = new ArrayList<>();

    //Should only ever be called from checkRequests
    static private void removeStaleRequests(){
        //TODO:
    }

    static private int getDateDifference(String dateFormat){
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMDD_HHmmss");
        try {
            Date input = format.parse(dateFormat);
            Date current = Calendar.getInstance(TimeZone.getTimeZone("America/Los_Angeles")).getTime();
            long diff = current.getTime() - input.getTime();
            //System.out.println("Difference: " + diff);
            if (diff < MAX_REQUEST_DIFF){
                //System.out.println("Request found less than diff: " + diff);
                return (int)diff;
            }
            return -1;
        }
        catch(ParseException e){
            e.printStackTrace();
            //TODO: is there a better way to handle this?
            return -1;
        }
    }

    static private boolean requestsContains(ChatRequest req){
        if (chatRequests.size() == 0) return false;
        for (ChatRequest current : chatRequests){
            boolean equal = true;
            if (!current.requestingUser.equals(req.requestingUser)) equal = false;
            if (equal && !current.targetUser.equals(req.targetUser)) equal = false;
            if (equal && !current.date.equals(req.date)) equal = false;
            if (equal) return true;
        }
        return false;
    }
    static void attemptConnection(ChatRequest req) {
        System.out.println("Attempting to connect to " + req.requestingIPaddress + ":" + req.requestingPort);
        try {
            PeerConnection connection = new PeerConnection(user, req);
            connection.connectNatPunch(port);
        }
        catch(IOException e){
            e.printStackTrace();
        }
    }

    static private int handleRequests(ArrayList<ChatRequest> newRequests){
        for(ChatRequest req : newRequests) {
            int check = getDateDifference(req.date);
            boolean contains = requestsContains(req);
            if (check > 0 && !contains) {
                chatRequests.add(req);
                attemptConnection(req);
                port = manager.getNextSocket();
                if (port == -1){
                    System.out.println("error finding port");
                }
            }//within timeframe - accept/reject
        }
        return 0;
    }

    public static void main(String[] args) {
        user = new UserData();
        OkClient client = new OkClient();
        user.username = USERNAME;
        user.password = PASSWORD;

        if (args.length == 1){
            FRIENDNAME = args[0];
        }

        else if (args.length == 3){
            user.username = args[0];
            user.password = args[1];
            FRIENDNAME = args[2];
        }

        else if (args.length != 0){
            System.out.println("Usage 1: no args");
            System.out.println("   " + USERNAME + " with password " + PASSWORD + " connects to " + FRIENDNAME);
            System.out.println("Usage 2: <friend_name>");
            System.out.println("   " + USERNAME + " with password " + PASSWORD + " connects to <friend_name>");
            System.out.println("Usage 3: <user_name> <password> <friend_name>");
            System.out.println("   <user_name> with password <password> connects to <friend_name>");
            return;
        }

        try {
            //logon
            client.logon(user);
        }
        catch(IOException e){
            e.printStackTrace();
        }

        try {
            manager = ConnectionManager.getConnectionManager(user);
        }
        catch(IOException e){
            e.printStackTrace();
            return;
        }
        if (manager == null) return;
        int loopCount = 0;
        port = manager.getNextSocket();
        if (port == -1){
            System.out.println("error finding port");
        }
        while(true) {
            //find socket and ping STUN server
            ArrayList<ChatRequest> newRequests = new ArrayList<>();
            if (loopCount > 5) {
                removeStaleRequests();
                System.out.println("Still here");
                loopCount = 0;
            }
            newRequests.clear();
            try {
                ChatRequest req = client.makeChatRequest(user, FRIENDNAME);
                PeerConnection peer = new PeerConnection(user, req);
                peer.connectNatPunch(port);
            }
            catch (IOException e) {
                e.printStackTrace();
                //TODO: figure out how to handle this gracefully
                break;
            }
            loopCount++;
            //always sleep
            try {
                Thread.sleep(REQ_LISTEN_REFRESH);
            }
            catch (InterruptedException e) {
                e.printStackTrace();
                break;
            }
        }
    }
}
