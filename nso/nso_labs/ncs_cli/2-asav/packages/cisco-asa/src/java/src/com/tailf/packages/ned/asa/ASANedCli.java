package com.tailf.packages.ned.asa;

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.security.MessageDigest;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.Random;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.EnumSet;

import org.apache.log4j.Logger;
import ch.ethz.ssh2.Connection;

import java.lang.reflect.Method;

import com.tailf.conf.*;

import com.tailf.maapi.Maapi;
import com.tailf.maapi.MaapiSchemas.CSNode;
import com.tailf.maapi.MaapiUserSessionFlag;
import com.tailf.maapi.MaapiCrypto;
import com.tailf.maapi.MaapiException;
import com.tailf.maapi.MaapiFlag;

import com.tailf.ncs.ResourceManager;
import com.tailf.ncs.annotations.Resource;
import com.tailf.ncs.annotations.ResourceType;
import com.tailf.ncs.annotations.Scope;
import com.tailf.ncs.ns.Ncs;

import com.tailf.ned.NedCapability;
import com.tailf.ned.NedCliBase;
import com.tailf.ned.NedCliBaseTemplate;
import com.tailf.ned.NedCmd;
import com.tailf.ned.NedException;
import com.tailf.ned.NedExpectResult;
import com.tailf.ned.NedMux;
import com.tailf.ned.NedTTL;
import com.tailf.ned.NedTracer;
import com.tailf.ned.NedWorker;
import com.tailf.ned.NedWorker.TransactionIdMode;

import com.tailf.ned.SSHSessionException;
import com.tailf.ned.SSHSession;
import com.tailf.ned.SSHConnection;
import com.tailf.ned.TelnetSession;
import com.tailf.ned.CliSession;

import com.tailf.packages.ned.nedcom.Schema;
import com.tailf.packages.ned.nedcom.NedComCliBase;

import com.tailf.cdb.Cdb;
import com.tailf.cdb.CdbDBType;
import com.tailf.cdb.CdbSession;

import com.tailf.navu.NavuAction;
import com.tailf.navu.NavuContainer;
import com.tailf.navu.NavuContext;
import com.tailf.navu.NavuException;
import com.tailf.navu.NavuLeaf;
import com.tailf.navu.NavuList;
import com.tailf.navu.NavuNode;


/**
 * This class implements NED interface for cisco asa routers
 *
 */
@SuppressWarnings("deprecation")
public class ASANedCli extends NedComCliBase {
    public static Logger LOGGER  = Logger.getLogger(ASANedCli.class);

    @Resource(type=ResourceType.MAAPI, scope=Scope.INSTANCE)
    public Maapi mm;

    @Resource(type=ResourceType.CDB, scope=Scope.INSTANCE)
    public Cdb cdb;
    public CdbSession cdbOper = null;
    private int thr = -1;

    private String DATE = "2017-10-06";
    private String VERSION = "5.5.4";

    private MetaDataModify metaData;
    private NedSecrets secrets;
    private String lastTransformedConfig = null;
    private String lastGetConfig = null;
    private MaapiCrypto mCrypto;

    private String asaversion = "unknown";
    private String asamodel   = "unknown";
    private String asaname    = "asa";
    private String asaserial  = "unknown";
    private boolean haveSerialNumber = false;

    private boolean showRaw = false;
    private boolean showFixed = false;

    private final static String privexec_prompt, prompt;
    private final static Pattern[] plw, ec, ec2, config_prompt;

    // CONTEXT CONFIG
    private boolean haveContext = false;
    private Connection adminConn = null;
    private SSHSession adminSession = null;
    /**
     *  The isAdminContext property is set to true if connection is established to admin context
     *  of multi-context device.  It should be examined before executing any commands exclusive
     *  to admin context, such as 'changeto system'
     *
     *  Note, that this check was not implemented throughout the package.
     */
    private boolean isAdminContext = false;
    /**
     *  The configUrl property is set for user contexts on multi-context device.  It is used
     *  for more command when startup configuration needs to be retrived via admin context.
     */
    private String configUrl = null;

    // NED-SETTINGS
    private String nedSettingLevel = "";
    private String deviceProfile = "cisco-asa";
    private String transActionIdMethod;
    private String writeMemoryMode;
    private String contextName;
    private boolean logVerbose;
    private String adminDeviceName = null;
    private String adminDeviceMethod;
    private int adminDeviceNumberOfRetries;
    private int adminDeviceTimeBetweenRetry;
    private boolean turboParserEnable;
    private boolean useStartupConfig;
    private boolean autoConfigUrlFileDelete;
    private ArrayList<String[]> autoPrompts = new ArrayList<String[]>();
    private ArrayList<String> contextList = new ArrayList<String>();

    // STATIC
    private static Schema schema;
    static {
        // start of input, > 0 non-# and ' ', one #, >= 0 ' ', eol
        privexec_prompt = "\\A[^\\# ]+#[ ]?$";

        prompt = "\\A\\S*#";

        plw = new Pattern[] {
            Pattern.compile("Continue\\?\\[confirm\\]"),
            Pattern.compile("\\A.*\\(cfg\\)#"),
            Pattern.compile("\\A.*\\(config\\)#"),
            Pattern.compile("\\A.*\\(.*\\)#"),
            Pattern.compile("\\A\\S*#"),
            Pattern.compile("\\?[ ]?\\[yes/no\\]")
        };

        config_prompt = new Pattern[] {
            Pattern.compile("\\A\\S*\\(config\\)#"),
            Pattern.compile("\\A.*\\(.*\\)#")
        };

        ec = new Pattern[] {
            Pattern.compile("Do you want to kill that session and continue"),
            Pattern.compile("\\A\\S*\\(config\\)#"),
            Pattern.compile("\\A.*\\(.*\\)#"),
            Pattern.compile("Aborted.*\n"),
            Pattern.compile("Error.*\n"),
            Pattern.compile("syntax error.*\n"),
            Pattern.compile("error:.*\n")
        };

        ec2 = new Pattern[] {
            Pattern.compile("\\A.*\\(cfg\\)#"),
            Pattern.compile("\\A.*\\(config\\)#"),
            Pattern.compile("\\A.*\\(.*\\)#"),
            Pattern.compile("Aborted.*\n"),
            Pattern.compile("Error.*\n"),
            Pattern.compile("syntax error.*\n"),
            Pattern.compile("error:.*\n")
        };

        schema = loadSchema(ASANedCli.class);
        if (schema != null) {
            schema.config.matchRelaxed = true;
            schema.config.matchEnumsIgnoreCase = true;
            schema.config.matchEnumsPartial = true;
        }
    }

    public ASANedCli() {
        super();
        try {
            ResourceManager.registerResources(this);
        } catch (Exception e) {
            LOGGER.error("Error injecting Resources", e);
        }
    }

    private void enableDevice(NedWorker worker, CliSession tsession, String enablePw)
        throws Exception {
        NedExpectResult res;

        res = tsession.expect(new String[] {
                "\\A[Ll]ogin:",
                "\\A[Uu]sername:",
                "\\A[Pp]assword:",
                "\\A\\S.*>",
                privexec_prompt},
            worker);
        if (res.getHit() < 3)
            throw new NedException("Authentication failed");
        if (res.getHit() == 3) {
            tsession.print("enable\n");
            res = tsession.expect(new String[] {"[Pp]assword:", prompt}, worker);
            if (res.getHit() == 0) {
                if (enablePw == null || enablePw.isEmpty())
                    enablePw = "";
                traceInfo(worker, "Sending enable password (NOT LOGGED)");
                if (trace)
                    tsession.setTracer(null);
                tsession.print(enablePw+"\n"); // enter password here
                if (trace)
                    tsession.setTracer(worker);
                try {
                    res = tsession.expect(new String[] {"\\A\\S*>", prompt}, worker);
                    if (res.getHit() == 0)
                        throw new NedException("Secondary password authentication failed");
                } catch (Exception e) {
                    throw new NedException("Secondary password authentication failed");
                }
            }
        }
    }

    private void sleep(NedWorker worker, long milliseconds, boolean log) {
        if (log)
            traceVerbose(worker, "Sleeping " + milliseconds + " milliseconds");
        try {
            Thread.sleep(milliseconds);
            if (log)
                traceVerbose(worker, "Woke up from sleep");
        } catch (InterruptedException e) {
            System.err.println("sleep interrupted");
        }
    }

    /**setupContext
     *
     *  For multi-context mode:
     *
     *  Set isAdminContext if we connected to the admin context (hence can 'changeto system')
     *
     *  If NED setting cisco-asa/admin-device/name is specified:
     *    - if NED setting cisco-asa-context-name is also specified, confirm that we are connected
     *      to context with that name (exception if we connected to a different context)
     *      if cisco-asa-context-name is not specified, set contextName to the name of context we
     *      are connected to
     *    - get configUrl file name and store it for future usage
     */
    private void setupContext(NedWorker worker)
        throws Exception {

        logInfo(worker, "Using security context mode : multiple");
        haveContext = true;

        // Get context info
        String showContext = (contextName != null) ? print_line_exec(worker, "show context "+ contextName)
            : print_line_exec(worker, "show context");

        // Define if we are connected to an admin context (and will be able to switch to system context)
        if (contextName == null && adminDeviceName == null) {
            if (showContext.indexOf("\n*") != -1) {
                isAdminContext = true;
                logInfo(worker, "Found admin context on multiple context mode device");
            }
        }

        else
        {
            // Context Name   Class Interfaces Mode URL
            // <NAME> <CLASS> <INT_LIST> <MODE> <URL>
            String tokens[] = showContext.trim().split("[ \r\n]+");
            if (tokens.length > 10) {
                // Autoderive context name if not set by user via NED settings
                if (contextName == null)
                    contextName = tokens[6].trim();
                // Verify that context name shown is what expected
                if (tokens[6].trim().equals(contextName) == false)
                    throw new NedException("Wrong context found '"+tokens[6].trim()+"' expected '"
                                           +contextName+"', check cisco-asa-context-name config");
                // Set config url for future file retrival
                configUrl = tokens[10].trim();
                logInfo(worker, "Found valid context : '"+contextName+"' with config url '" + configUrl + "'");
            } else {
                throw new NedException("Failed to parse 'show context' output:\n" + showContext);
            }
        }

        if (isAdminContext) {
            // Set pager 0 for system context
            changeto_context(worker, "system");
            print_line_exec(worker, "terminal pager 0");
        }
    }

    @Override
    public void setupTelnet(NedWorker worker)
        throws Exception {
        TelnetSession tsession;
        NedExpectResult res;

        traceInfo(worker, "TELNET connecting to host: "+ip.getHostAddress()+":"+port);
        if (trace)
            tsession = new TelnetSession(worker, ruser, readTimeout, worker, this);
        else
            tsession = new TelnetSession(worker, ruser, readTimeout, null, this);

        traceInfo(worker, "TELNET waiting for login prompt");
        this.session = tsession;
        try {
            res = session.expect(new String[] {"[Ll]ogin:", "[Nn]ame:",
                                               "[Pp]assword:"}, worker);
        } catch (Throwable e) {
            throw new NedException("No login prompt");
        }

        if (res.getHit() < 2) {
            traceInfo(worker, "TELNET sending username");
            session.println(ruser);
            traceInfo(worker, "TELNET waiting for password prompt");
            try {
                session.expect(new String[] {"[Pp]assword:"}, worker);
            } catch (Throwable e) {
                throw new NedException("No password prompt, got: "+res.getText());
            }
        }

        traceInfo(worker, "TELNET sending password");
        if (trace)
            session.setTracer(null);
        session.println(pass);
        if (trace)
            session.setTracer(worker);
    }

    @Override
    public void setupSSH(NedWorker worker)
        throws Exception {

        traceInfo(worker, "SSH connecting to host: "+ip.getHostAddress()+":"+port);
        connection = new SSHConnection(worker);

        connection.connect(null, 0, connectTimeout);

        if (!connection.isAuthenticationComplete()) {
            traceInfo(worker, "SSH autentication failed");
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("auth connect failed ");
            }
            worker.connectError(NedWorker.CONNECT_BADAUTH, "Auth failed");
            return;
        }

        traceInfo(worker, "SSH initializing session");
        if (trace)
            session = new SSHSession(connection, readTimeout, worker, this, 200, 24);
        else
            session = new SSHSession(connection, readTimeout, null, this, 200, 24);
    }

    private String getDeviceSetting(NedWorker worker, String path)
        throws Exception {
        String p = "/ncs:devices/device{"+adminDeviceName+"}/"+path;
        try {
            if (mm.exists(thr, p)) {
                return ConfValue.getStringByValue(p, mm.getElem(thr, p));
            }
        } catch (MaapiException ignore) {}
        return null;
    }

    private String getAuthGroupSetting(NedWorker worker, String authgroup, String path)
        throws Exception {
        String p = "/ncs:devices/authgroups/group{"+authgroup+"}/default-map/"+path;
        try {
            if (mm.exists(thr, p)) {
                return ConfValue.getStringByValue(p, mm.getElem(thr, p));
            }
        } catch (MaapiException ignore) {}
        return null;
    }

    private void closeAdminSSH(NedWorker worker)
        throws Exception {
        try {
            print_line_exec(adminSession, worker, "quit");
        } catch (Exception ignore) { }
        adminConn.close();
        adminSession.close();
        adminSession = null;
        logInfo(worker, "admin-device: SSH closed");
    }

    private void setupAdminSSH(NedWorker worker)
        throws Exception {

        // Get device address, port and authgroup
        String aaddress = getDeviceSetting(worker, "address");
        String aport = getDeviceSetting(worker, "port");
        if (aport == null)
            aport = "22";
        String aauthgroup = getDeviceSetting(worker, "authgroup");
        if (aaddress == null || aauthgroup == null)
            throw new Exception("admin-device: SSH config error :: incomplete "+adminDeviceName+" device");

        // Get authgroup name, password and secondary-password
        String aname = getAuthGroupSetting(worker, aauthgroup, "remote-name");
        String apassword = getAuthGroupSetting(worker, aauthgroup, "remote-password");
        if (aname == null || apassword == null)
            throw new Exception("admin-device: SSH config error :: incomplete "+aauthgroup+" authgroup");
        String asecpass = getAuthGroupSetting(worker, aauthgroup, "remote-secondary-password");
        if (!asecpass.isEmpty())
            asecpass =  mCrypto.decrypt(asecpass);

        // SSH connect [optionally retry several times, waiting for available session]
        traceInfo(worker, "admin-device: SSH connecting to host: "+aaddress+":"+aport);
        for (int i = adminDeviceNumberOfRetries; i >= 0; i--) {
            try {
                adminConn = new Connection(aaddress, Integer.parseInt(aport));
                adminConn.connect(null, 0, connectTimeout);
                traceVerbose(worker, "admin-device: SSH connected to "+adminDeviceName);
                break;
            }
            catch (Exception e) {
                String failmsg = "admin-device: SSH failed to connect to "+adminDeviceName+" :: "+e.getMessage();
                if (i == 0) {
                    throw new Exception(failmsg);
                } else {
                    traceVerbose(worker, failmsg);
                    traceVerbose(worker, "retrying in "+adminDeviceTimeBetweenRetry+" seconds");
                    adminConn = null;
                    worker.setTimeout(readTimeout + adminDeviceTimeBetweenRetry * 1000);
                    sleep(worker, adminDeviceTimeBetweenRetry * 1000, true);
                }
            }
        }

        // Authenticate with password
        adminConn.authenticateWithPassword(aname, mCrypto.decrypt(apassword));
        if (!adminConn.isAuthenticationComplete()) {
            throw new Exception("admin-device: SSH authentication failed");
        }

        // Create SSHSession
        traceInfo(worker, "admin-device: SSH initializing session");
        if (trace)
            adminSession = new SSHSession(adminConn, readTimeout, worker, this, 200, 24);
        else
            adminSession = new SSHSession(adminConn, readTimeout, null, this, 200, 24);

        // Enable device
        enableDevice(worker, adminSession,  asecpass);
        logInfo(worker, "admin-device: SSH logged in");

        // Init session
        print_line_exec(adminSession, worker, "terminal pager 0");
        String version = print_line_exec(adminSession, worker, "show version");
        if (!version.contains("Cisco Adaptive Security Appliance"))
            throw new Exception("admin-device: SSH unknown device");

        // Change to system context
        String reply = print_line_exec(adminSession, worker, "changeto system");
        if (reply.contains("ERROR: ") || reply.contains("Command not valid "))
            throw new Exception("admin-device: SSH : failed to changeto system context");
    }


    /**
     * NED cisco-asa contructor
     * @param device_id       - configured device name
     * @param ip              - ip address to device
     * @param port            - configured port to device
     * @param proto           - protocol (either ssh or telnet)
     * @param ruser           - remote user id
     * @param pass            - remote password
     * @param secpass         - secondary password (not used)
     * @param trace           - trace mode enabled/disabled
     * @param connectTimeout  - connect timeout (msec)
     * @param readTimeout     - read timeout (msec)
     * @param writeTimeout    - write timeout (msec)
     * @param mux             -
     * @param worker          - worker context
     */
    public ASANedCli(String device_id,
               InetAddress ip,
               int port,
               String proto,  // ssh or telnet
               String ruser,
               String pass,
               String secpass,
               boolean trace,
               int connectTimeout, // msec
               int readTimeout,    // msec
               int writeTimeout,   // msec
               NedMux mux,
               NedWorker worker) {

        super(device_id, ip, port, proto, ruser, pass, secpass,
              trace, connectTimeout, readTimeout, writeTimeout, mux,
              worker);

        NedTracer tracer;
        if (trace)
            tracer = worker;
        else
            tracer = null;

        // LOG NCS version, NED version, date and timeouts
        logInfo(worker, "NED VERSION: cisco-asa "+VERSION+" "+ DATE);

        //
        // Init NCS resources and open maapi read session
        //
        try {
            ResourceManager.registerResources(this);
        } catch (Exception e) {
            LOGGER.error("Error injecting Resources", e);
        }

        //
        // Open maapi read session
        //
        try {
            mm.setUserSession(1);
            thr = mm.startTrans(Conf.DB_RUNNING, Conf.MODE_READ);
        } catch (Exception e) {
            logError(worker, "Error initializing CDB read session :: ", e);
            return;
        }

        //
        // Read ned-settings
        //
        try {
            readNedSettings(worker);
        }
        catch (Exception e) {
            logError(worker, "failed to read cisco-asa ned-settings", e);
            worker.error(NedWorker.CONNECT_CONNECTION_REFUSED, e.getMessage());
            return;
        }

        //
        // Connect to device
        //
        try {
            logInfo(worker, "connect-timeout "+connectTimeout+" read-timeout "
                    +readTimeout+" write-timeout "+writeTimeout);
            worker.setTimeout(connectTimeout);
            try {
                if (proto.equals("ssh")) {
                    setupSSH(worker);
                } else {
                    setupTelnet(worker);
                }
            }
            catch (Exception e) {
                LOGGER.error("connect failed ",  e);
                worker.connectError(NedWorker.CONNECT_CONNECTION_REFUSED, e.getMessage());
                return;
            }
        }
        catch (NedException e) {
            LOGGER.error("connect response failed ",  e);
            return;
        }

        //
        // Login on device
        //
        try {

            // enable device
            enableDevice(worker, session, secpass);

            // Set terminal settings (context = admin for multi-context)
            print_line_exec(worker, "terminal pager 0");

            // Issue show version to check device/os type
            traceInfo(worker, "Requesting version string");
            String version = print_line_exec(worker, "show version");

            /* look for version string */
            try {
                traceInfo(worker, "Inspecting version string");

                if (version.contains("Cisco Adaptive Security Appliance")) {
                    // found ASA
                    int b, e;

                    //
                    // NETSIM
                    //
                    if (version.contains("NETSIM")) {
                        traceInfo(worker, "Found NETSIM device");
                        asamodel   = "NETSIM";
                        asaversion = "NED " + VERSION;

                        // Show CONFD&NED version used by NETSIM in ned trace
                        print_line_exec(worker, "show confd-state version");
                        print_line_exec(worker, "show confd-state loaded-data-models "+
                                       "data-model tailf-ned-cisco-asa");

                        // Set NETSIM terminal settings
                        print_line_exec(worker, "terminal length 0");
                        print_line_exec(worker, "terminal width 0");
                    }

                    //
                    // Real device
                    //
                    else {
                        traceInfo(worker, "Found real device");

                        // Optionally setup context settings
                        String mode = print_line_exec(worker, "show mode");
                        if (mode.contains("multiple")) {
                            setupContext(worker);
                        }

                        // Real device terminal settings
                        print_line_exec(worker, "terminal no monitor");

                        // Verify terminal width
                        String terminal = print_line_exec(worker, "show terminal");
                        Pattern pattern = Pattern.compile("Width = (\\d+)");
                        Matcher matcher = pattern.matcher(terminal);
                        if (matcher.find()) {
                            int width = Integer.parseInt(matcher.group(1));
                            if (width > 0 && width < 511) {
                                worker.error(NedCmd.CONNECT_CLI, ": Device terminal width ("
                                             +width+") too low, please pre-configure to 0 or 511");
                                return;
                            }
                        }
                    }

                    // Get version
                    b = version.indexOf("Software Version");
                    if (b > 0) {
                        e = version.indexOf("\n", b);
                        if (e > 0) {
                            asaversion = version.substring(b+17,e).trim();
                        }
                    }

                    // Get model
                    b = version.indexOf("\nHardware: ");
                    if (b > 0) {
                        asamodel = version.substring(b+11);
                        if ((e = asamodel.indexOf(",")) > 0)
                            asamodel = asamodel.substring(0,e);
                        if ((e = asamodel.indexOf("\n")) > 0)
                            asamodel = asamodel.substring(0,e);
                        asamodel = asamodel.trim();
                    }

                    // Get serial
                    if (asamodel.equals("NETSIM"))
                        asaserial = device_id;
                    else {
                        Pattern pattern = Pattern.compile("Serial Number:\\s+(\\S+)");
                        Matcher matcher = pattern.matcher(version);
                        if (matcher.find()) {
                            asaserial = matcher.group(1);
                        } else {
                            // user context, look up serial from inventory
                            String inventory = print_line_exec(worker, "show inv 0");
                            pattern = Pattern.compile("SN:\\s+(\\S+)");
                            matcher = pattern.matcher(inventory);
                            if (matcher.find())
                                asaserial = matcher.group(1);
                        }
                    }

                    //
                    // Setup NED
                    //
                    setupASANed(worker);

                } else {
                    worker.error(NedCmd.CONNECT_CLI, "unknown device");
                }
            } catch (Exception e) {
                new NedException("Failed to read device version string");
            }
        }
        catch (SSHSessionException e) {
            worker.error(NedCmd.CONNECT_CLI, e.getMessage());
        }
        catch (IOException e) {
            worker.error(NedCmd.CONNECT_CLI, e.getMessage());
        }
        catch (Exception e) {
            worker.error(NedCmd.CONNECT_CLI, e.getMessage());
        }
    }


    /**
     * Constructor used for init no-connect, i.e instantiate the NED in
     * off-line mode.
     *
     * @param device_id - Device id
     * @param mux       - NED Mux
     * @param worker    - NED Worker
     */
    public ASANedCli(String device_id, NedMux mux, NedWorker worker) {

        this();
        this.device_id = device_id;

        trace = true;
        if (trace) {
            tracer = worker;
        } else {
            tracer = null;
        }

        logInfo(worker, "*NSO BUILD VERSION: "+nsoCapabilityProps.getProperty("nso-version"));
        logInfo(worker, "*NED VERSION: cisco-asa "+VERSION+" "+DATE);

        // Note: Init NCS resources? (get error if connected, disconnect and dry-run)

        //
        // Open maapi read session
        //
        try {
            mm.setUserSession(1);
            thr = mm.startTrans(Conf.DB_RUNNING, Conf.MODE_READ);
        } catch (Exception e) {
            logError(worker, "Error initializing CDB read session :: ", e);
            return;
        }

        // Read ned-settings
        try {
            readNedSettings(worker);
        } catch (Exception e) {
            worker.error(NedCmd.CONNECT_CLI, "noconnect failed to read ned-settings: "+e.getMessage());
            return;
        }

        // Set platform data - asamodel, asaversion and asapolice
        try {
            asaversion = getPlatformData(thr, "version");
            asamodel   = getPlatformData(thr, "model");
            asaserial  = getPlatformData(thr, "serial-number");
        } catch (Exception e) {
            worker.error(NedCmd.CONNECT_CLI, "noconnect failed to read platform data: "+e.getMessage());
            return;
        }

        // Setup NED
        try {
            setupASANed(worker);
        } catch (Exception e) {
            worker.error(NedCmd.CONNECT_CLI, "noconnect failed to setup NED: "+e.getMessage());
        }
    }

    public NedCliBase initNoConnect(String device_id,
                                    NedMux mux,
                                    NedWorker worker) {
        logDebug(null, "initNoConnect("+device_id+")");
        return new ASANedCli(device_id, mux, worker);
    }

    private void setupASANed(NedWorker worker)
        throws Exception {

        logInfo(worker, "DEVICE:"
                + " name="+asaname+" model="+asamodel+ " version="+asaversion
                + " serial="+asaserial);

        // capas
        NedCapability capas[] = new NedCapability[2];
        capas[0] = new NedCapability(
                                     "",
                                     "http://cisco.com/ned/asa",
                                     "tailf-ned-cisco-asa",
                                     "",
                                     DATE,
                                     "");
        // Trim default values
        capas[1] = new NedCapability(
                                     "urn:ietf:params:netconf:capability:" +
                                     "with-defaults:1.0?basic-mode=trim",
                                     "urn:ietf:params:netconf:capability:" +
                                     "with-defaults:1.0",
                                     "",
                                     "",
                                     "",
                                     "");

        // statscapas
        NedCapability statscapas[] = new NedCapability[1];
        statscapas[0] = new NedCapability(
                                          "",
                                          "http://cisco.com/ned/asa-stats",
                                          "tailf-ned-cisco-asa-stats",
                                          "",
                                          DATE,
                                          "");

        setConnectionData(capas,
                          statscapas,
                          true,
                          TransactionIdMode.UNIQUE_STRING);

        // Add capabilities
        // Add stats capabilities

        /*
         * On NSO 4.0 and later, do register device model and
         * os version.
         */
        if (Conf.LIBVSN >= 0x6000000) {
            ArrayList<ConfXMLParam> xmlParam = new ArrayList<ConfXMLParam>();
            xmlParam.add(new ConfXMLParamStart("ncs", "platform"));
            xmlParam.add(new ConfXMLParamValue("ncs", "name", new ConfBuf(asaname)));
            xmlParam.add(new ConfXMLParamValue("ncs", "version", new ConfBuf(asaversion)));
            xmlParam.add(new ConfXMLParamValue("ncs", "model", new ConfBuf(asamodel)));
            if (haveSerialNumber)
                xmlParam.add(new ConfXMLParamValue("ncs", "serial-number", new ConfBuf(asaserial)));
            xmlParam.add(new ConfXMLParamStop("ncs", "platform"));
            ConfXMLParam[] platformData = xmlParam.toArray(new ConfXMLParam[xmlParam.size()]);
            Method method = this.getClass().getMethod("setPlatformData", new Class[]{ConfXMLParam[].class});
            method.invoke(this, new Object[]{platformData});
        }

        // Start operational session
        cdbOper = cdb.startSession(CdbDBType.CDB_OPERATIONAL);

        // Create utility classes used by ASA NED
        metaData = new MetaDataModify(device_id, asamodel, trace, logVerbose, autoConfigUrlFileDelete);
        secrets = new NedSecrets(cdbOper, device_id, trace, logVerbose);
        mCrypto = new MaapiCrypto(mm);
    }

    private void traceVerbose(NedWorker worker, String info) {
        if (logVerbose && trace) {
            worker.trace("-- " + info + "\n", "out", device_id);
        }
    }

    private void traceInfo(NedWorker worker, String info) {
        if (trace)
            worker.trace("-- " + info + "\n", "out", device_id);
    }

    public void trace(NedWorker worker, String msg, String direction) {
        if (trace) {
            worker.trace("-- "+msg+" --\n", direction, device_id);
        }
    }

    public void reconnect(NedWorker worker) {
        // all capas and transmode already set in constructor
        // nothing needs to be done
    }

    /**
     * Displays YANG modules covered by the class
     */
    public String [] modules() {
        return new String[] { "tailf-ned-cisco-asa" };
    }

    /**
     * Display NED identity
     */
    public String identity() {
        return "asa-id:cisco-asa";
    }

    private boolean isDevice() {
        return !asamodel.equals("NETSIM");
    }
    private boolean isNetsim() {
        return asamodel.equals("NETSIM");
    }

    private String getPlatformData(int thr, String leaf)
        throws Exception {

        // Look up in devices device platform
        String p = "/ncs:devices/device{"+device_id+"}/platform/" + leaf;
        try {
            if (mm.exists(thr, p)) {
                return ConfValue.getStringByValue(p, mm.getElem(thr, p));
            }
        } catch (MaapiException ignore) {}

        return "unknown";
    }

    private String getNedSetting(NedWorker worker, String path)
       throws Exception {
        String val = null;
        nedSettingLevel = "";

        // Global
        String p = "/ncs:devices/ncs:global-settings/ncs:ned-settings/"+path;
        try {
            if (mm.exists(thr, p)) {
                val = ConfValue.getStringByValue(p, mm.getElem(thr, p));
                nedSettingLevel = " [G]";
            }
        } catch (MaapiException ignore) {
        }

        // Profile
        p = "/ncs:devices/ncs:profiles/profile{"+deviceProfile+"}/ncs:ned-settings/"+path;
        try {
            if (mm.exists(thr, p)) {
                val = ConfValue.getStringByValue(p, mm.getElem(thr, p));
                nedSettingLevel = " [P]";
            }
        } catch (MaapiException ignore) {
        }

        // Device
        p = "/ncs:devices/device{"+device_id+"}/ned-settings/"+path;
        if (mm.exists(thr, p)) {
            val = ConfValue.getStringByValue(p, mm.getElem(thr, p));
            nedSettingLevel = " [D]";
        }

        return val;
    }

    private String getNedSettingString(NedWorker worker, String path, String defaultValue)
       throws Exception {
        String value = defaultValue;
        String setting = getNedSetting(worker, path);
        if (setting != null)
            value = setting;
        traceInfo(worker, path + " = " + value + nedSettingLevel);
        return value;
    }

    private boolean getNedSettingBoolean(NedWorker worker, String path, boolean defaultValue)
       throws Exception {
        boolean value = defaultValue;
        String setting = getNedSetting(worker, path);
        if (setting != null)
            value = setting.equals("true") ? true : false;
        traceInfo(worker, path + " = " + value + nedSettingLevel);
        return value;
    }

    private int getNedSettingInt(NedWorker worker, String path, int defaultValue)
       throws Exception {
        int value = defaultValue;
        String setting = getNedSetting(worker, path);
        if (setting != null)
            value = Integer.parseInt(setting);
        traceInfo(worker, path + " = " + value + nedSettingLevel);
        return value;
    }

    /**
     * Simple utility to extract the relevant ned-settings from CDB
     * @param worker
     * @param th
     */
    private void readNedSettings(NedWorker worker)
        throws Exception {

        // Get device profile
        String p = "/ncs:devices/device{"+device_id+"}/device-profile";
        try {
            if (mm.exists(thr, p)) {
                deviceProfile = ConfValue.getStringByValue(p, mm.getElem(thr, p));
            }
        } catch (MaapiException ignore) { }
        traceInfo(worker, "device-profile = " + deviceProfile);

        // Check if have device platform serial-number
        try {
            mm.exists(thr, "/ncs:devices/device{"+device_id+"}/platform/serial-number");
            haveSerialNumber = true;
            traceInfo(worker, "devices device platform serial-number = "+haveSerialNumber);
        } catch (MaapiException ignore) { }

        //
        // Read ned-settings
        //
        traceInfo(worker, "NED-SETTINGS: ([G]lobal | [P]rofile | [D]evice)");

        // Base roots
        NavuContext context = new NavuContext(mm, thr);
        NavuContainer deviceSettings= new NavuContainer(context)
            .container(Ncs.hash)
            .container(Ncs._devices_)
            .list(Ncs._device_)
            .elem(new ConfKey(new ConfBuf(device_id)));

        NavuContainer globalSettings = new NavuContainer(context)
            .container(Ncs.hash)
            .container(Ncs._devices_)
            .container("ncs", "global-settings");

        NavuContainer profileSettings = new NavuContainer(context)
            .container(Ncs.hash)
            .container(Ncs._devices_)
            .container("ncs", "profiles")
            .list(Ncs._profile_)
            .elem(new ConfKey(new ConfBuf("cisco-asa")));

        NavuContainer[] settings = {globalSettings,
                                    profileSettings,
                                    deviceSettings };

        /*
         * Get auto-prompts
         */

        for (NavuContainer s : settings ) {
            if (s == null)
                continue;
            NavuList prompts = s.container("ncs", "ned-settings")
                .list("asa-meta", "cisco-asa-auto-prompts");
            for (NavuContainer entry : prompts.elements()) {
                String[] newEntry  = new String[3];
                newEntry[0] = entry.leaf("id").valueAsString();
                newEntry[1] = entry.leaf("question").valueAsString();
                newEntry[2] = entry.leaf("answer").valueAsString();
                traceInfo(worker, "cisco-asa-auto-prompts "+newEntry[0]
                          + " q \"" +newEntry[1]+"\""
                          + " a \"" +newEntry[2]+"\"");
                autoPrompts.add(newEntry);
            }
        }

        // cisco-asa-transaction-id-method
        transActionIdMethod = getNedSettingString(worker, "cisco-asa-transaction-id-method", "config-hash");

        // cisco-asa-write-memory-setting
        writeMemoryMode = getNedSettingString(worker, "cisco-asa-write-memory-setting", "on-commit");

        // cisco-asa get-device-config-settings use-startup-config
        useStartupConfig = getNedSettingBoolean(worker, "cisco-asa/get-device-config-settings/use-startup-config", false);
        if (writeMemoryMode.equals("disabled") && useStartupConfig)
            throw new NedException("ERROR: use-startup-config can't be used in combination with cisco-asa-write-memory-setting = disabled");

        // cisco-asa-log-verbose
        logVerbose = getNedSettingBoolean(worker, "cisco-asa-log-verbose", false);

        // cisco-asa-context-name
        contextName = getNedSettingString(worker, "cisco-asa-context-name", null);

        // cisco-asa admin-device name
        adminDeviceName = getNedSettingString(worker, "cisco-asa/admin-device/name", null);
        if (adminDeviceName != null) {
            if (contextName != null && useStartupConfig == false)
                throw new NedException("ERROR: use-startup-config must be true with context-name and admin-device");
            // cisco-asa admin-device method
            adminDeviceMethod = getNedSettingString(worker, "cisco-asa/admin-device/method", "ssh");
            if (adminDeviceMethod.equals("ssh")) {
                // cisco-asa admin-device number-of-retries
                adminDeviceNumberOfRetries = getNedSettingInt(worker, "cisco-asa/admin-device/number-of-retries", 0);
                // cisco-asa admin-device time-between-retry
                adminDeviceTimeBetweenRetry = getNedSettingInt(worker, "cisco-asa/admin-device/time-between-retry", 1);
            }
        }

        // cisco-asa-context-list *
        if (contextName == null && adminDeviceName == null) {
            for (NavuContainer s : settings ) {
                if (s == null)
                    continue;
                NavuList contexts = s.container("ncs", "ned-settings")
                    .list("asa-meta", "cisco-asa-context-list");
                for (NavuContainer entry : contexts.elements()) {
                    traceInfo(worker, "cisco-asa-context-list \""
                              +entry.leaf("name").valueAsString()+"\"");
                    contextList.add(entry.leaf("name").valueAsString());
                }
            }
        }

        // cisco-asa extended-parser
        turboParserEnable = "turbo-mode".equals(getNedSettingString(worker, "cisco-asa/extended-parser", "disabled"));

        // cisco-asa auto context-config-url-file-delete
        autoConfigUrlFileDelete = getNedSettingBoolean(worker, "cisco-asa/auto/context-config-url-file-delete", true);

        // Done reading ned-settings
        traceInfo(worker, "");
    }

    private int isCliError(NedWorker worker, int cmd, String reply, String line) {
        int n;
        String trimmed = line.trim();  // Parachute
        String replyT = reply.trim();

        // Retry
        if (reply.toLowerCase().contains("wait for it to complete"))
            return 60;
        if (reply.contains("Command Failed. Configuration in progress..."))
            return 10; // config-url
        if (reply.toLowerCase().contains("object is being used"))
            return 1;

        // The following warnings is an error -> abort transaction
        String[] warningfail = {
            "WARNING: IP address .* and netmask .* inconsistent"
        };

        // The following strings treated as warnings -> ignore
        String[] errignore = {
            "Warning: \\S+.*",
            "WARNING: \\S+.*",
            "AAA: Warning",
            "name length exceeded the recommended length of .* characters",
            "A profile is deemed incomplete until it has .* statements",
            "Interface description was set by failover and cannot be changed",
            "Specified (\\S+) (\\S+) does not exist"

        };

        // The following strings is an error -> abort transaction
        // NOTE: Alphabetically sorted.
        String[] errfail = {
            "aborted",
            "addresses overlap with existing localpool range",
            "a .* already exists for network",
            "bad mask",
            "being used",
            "cannot apply",
            "cannot be deleted",
            "cannot configure",
            "cannot have local-as same as bgp as number",
            "cannot negate",
            "cannot redistribute",
            "command is depreceated",
            "command rejected",
            "configuration not accepted",
            "configure .* first",
            "create .* first",
            "disable .* first",
            "does not exist.",
            "does not support .* configurations",
            "duplicate name",
            "enable .* globally before configuring",
            "entry already running and cannot be modified",
            "error",
            "exceeded",
            "failed",
            "first configure the",
            "has already been assigned to",
            "hash values can not exceed 255",
            "illegal hostname",
            ".* is being un/configured in sub-mode, cannot remove it",
            "in use, cannot",
            "incomplete",
            "inconsistent address.*mask",
            "incorrect .* configured",
            "interface .* already configured as default ",
            "is configured as .* already",
            "interface.* combination tied to .* already",

            "invalid",
            "not valid",
            "not a valid ",
            "is not logically valid",

            "is not permitted",
            "is not running",
            "is not supported",
            "is used by",
            "local-as allowed only for ebgp peers",
            "may not be configured",
            "must be configured first",
            "must be enabled first",
            "must be disabled first",
            "must be greater than",
            "must be removed first",
            "must configure ip address for",
            "must enable .* routing first",
            "must specify a .* port as the next hop interface",
            "network: ip address/mask .* doesn't pair",
            "no existing configuration binding the default",
            "no such",
            "no authentication servers found",
            "not allowed",
            "not added",
            "not configured",
            "not defined",
            "not enough memory",
            "not found",
            "not supported",
            "peer* combination tied to .* already",
            "please configure .* before configuring",
            "please remove .*",
            "please 'shutdown' this interface before trying to delete it",
            "previously established ldp sessions may not have",
            "protocol not in this image",
            "range already exists",
            "routing not enabled",
            "setting rekey authentication rejected",
            "should be greater than",
            "should be in range",
            "specify .* command.* first",
            "sum total of .* exceeds 100 percent",
            "table is full",
            "unable to add",
            "unable to set_.* for ",
            "unable to populate",
            "vpn routing instance .* does not exist",
        };

        // Special cases ugly patches
        if (trimmed.startsWith("no ip address ")
            && reply.contains("Invalid address")) {
            // Happens when IP addresses already deleted on interface
            traceInfo(worker, "Ignoring '"+line+"' command");
            return 0;
        }
        if (trimmed.equals("no duplex")
            && reply.contains("Invalid input detected at")) {
            // Happens when 'no media-type' deletes duplex config on device
            traceInfo(worker, "Ignoring '"+line+"' command");
            return 0;
        }
        if (trimmed.startsWith("delete /noconfirm ")
            && reply.contains("No such file or directory")) {
            // Happens when auto-deleting context config-url
            traceInfo(worker, "Ignoring delete of non-existing context config-url file");
            return 0;
        }
        if (trimmed.startsWith("no ip address")
            && reply.contains("Invalid input detected at")) {
            // Happens for cli-show-no annotation
            traceInfo(worker, "Ignoring '"+line+"' (cli-show-no)");
            return 0;
        }

        // Fail on these warnings:
        for (n = 0; n < warningfail.length; n++) {
            if (findString(warningfail[n], reply) >= 0) {
                traceInfo(worker, "ERROR - matched warningfail: "+replyT);
                return -1;
            }
        }

        // Ignore warnings/info:
        for (n = 0; n < errignore.length; n++) {
            if (findString(errignore[n], reply) >= 0) {
                traceInfo(worker, "Ignoring warning: "+replyT);
                return 0;
            }
        }

        // Fail on these errors:
        for (n = 0; n < errfail.length; n++) {
            if (findString(errfail[n], reply.toLowerCase()) >= 0) {
                // Ignore all new errors when rollbacking due to abort/revert
                if (cmd == NedCmd.ABORT_CLI || cmd == NedCmd.REVERT_CLI) {
                    traceInfo(worker, "Ignoring abort/revert ERROR: "+replyT);
                    return 0;
                }
                traceInfo(worker, "ERROR - matched errfail: "+replyT);
                return -1;
            }
        }

        // Success
        return 0;
    }

    private String decryptPassword(NedWorker worker, String line) {
        Pattern pattern = Pattern.compile("(\\s\\$4\\$[^\\s]*)"); // " $4$<key>"
        Matcher match   = pattern.matcher(line);
        while (match.find()) {
            try {
                String password  = line.substring(match.start() + 1, match.end());
                String decrypted = mCrypto.decrypt(password);
                traceVerbose(worker, "DECRYPTED MAAPI password: "+password);
                line = line.substring(0, match.start()+1)
                    + decrypted
                    + line.substring(match.end(), line.length());
            } catch (MaapiException e) {
                traceInfo(worker, "mCrypto.decrypt() exception ERROR: "+ e.getMessage());
            }
            match = pattern.matcher(line);
        }
        return line;
    }

    private boolean changeto_context(NedWorker worker, String context)
        throws NedException, IOException, SSHSessionException, ApplyException {
        return changeto_context(session, worker, context);
    }

    private boolean changeto_context(CliSession tsession, NedWorker worker, String context)
        throws NedException, IOException, SSHSessionException, ApplyException {

        if (context.equals("system")) {
            tsession.print("changeto system\n");
            tsession.expect("changeto system", worker);
        } else {
            tsession.print("changeto context "+context+"\n");
            tsession.expect("changeto context "+context, worker);
        }

        String reply = tsession.expect(privexec_prompt, worker);
        if (reply.contains("ERROR: ") || reply.contains("Command not valid ")) {
            logInfo(worker, "ERROR - failed to changeto context '"+ context+"' : " + reply);
            return false;
        }

        if (!context.equals("system") && !context.equals("admin")) {
            // Note: system and admin contexts got pager set at login
            print_line_exec(worker, "terminal pager 0");
        }

        return true;
    }

    private String print_line_exec(CliSession tsession, NedWorker worker, String line)
        throws NedException, IOException, SSHSessionException, ApplyException {
        // Send command and wait for echo
        tsession.print(line + "\n");
        tsession.expect(new String[] { Pattern.quote(line) }, worker);
        // Return command output
        return tsession.expect(privexec_prompt, worker);
    }

    private String print_line_exec(NedWorker worker, String line)
        throws NedException, IOException, SSHSessionException, ApplyException {
        return print_line_exec(session, worker, line);
    }

    private void print_line_wait_oper(NedWorker worker, int cmd, String line)
        throws NedException, IOException, SSHSessionException, ApplyException {
        NedExpectResult res;

        traceVerbose(worker, "SENDING_OPER: '"+line+"'");

        // Send line and wait for echo
        session.print(line+"\n");
        session.expect(new String[] { Pattern.quote(line) }, worker);

        // Wait for prompt
        res = session.expect(new String[] {
                "Overwrite the previous NVRAM configuration\\?\\[confirm\\]",
                privexec_prompt},
            worker);

        if (res.getHit() == 0) {
            // Confirm question with "y" and wait for prompt again
            session.print("y");
            res = session.expect(new String[] {".*#"}, worker);
        }

        // Check for errors
        String lines[] = res.getText().split("\n|\r");
        for (int i = 0 ; i < lines.length ; i++) {
            if (lines[i].toLowerCase().contains("error")
                || lines[i].toLowerCase().contains("failed")) {
                throw new ApplyException(line, lines[i], true, false);
            }
        }
    }

    private static String findLine(String buf, String search) {
        int i = buf.indexOf(search);
        if (i >= 0) {
            int nl = buf.indexOf("\n", i+1);
            if (nl >= 0)
                return buf.substring(i,nl);
            else
                return buf.substring(i);
        }
        return null;
    }

    private String stringInsertCtrlV(String line) {
        StringBuilder result = new StringBuilder();
        StringCharacterIterator iterator =
            new StringCharacterIterator(line);
        char c1 = iterator.current();

        while (c1 != CharacterIterator.DONE) {
            if (c1 == '?')  {
                result.append("\u0016");
            }
            result.append(c1);
            c1 = iterator.next();
        }
        return result.toString();
    }

    private void expect_echo(String line, NedWorker worker)
            throws IOException, SSHSessionException {

        // Wait for echo, character by character due to escape sequence
        //traceVerbose(worker, "Waiting for echo of: '"+line+"'");
        if (trace)
            session.setTracer(null);
        for (int n = 0; n < line.length(); n++) {
            String c = line.substring(n, n+1);
            session.expect(new String[] { Pattern.quote(c) }, worker);
        }
        if (trace)
            session.setTracer(worker);
        traceVerbose(worker, line);
    }

    private boolean print_line_wait(NedWorker worker, int cmd, String line,
                                    int retrying, String meta)
        throws NedException, IOException, SSHSessionException, ApplyException {
        String orgLine = line;

        traceVerbose(worker, "SENDING: '"+line+"'");

        // password -> may be maapi encrypted
        boolean decrypted = false;
        if (meta != null && (meta.contains(" :: secret-password") ||
                             meta.contains(" :: support-encrypted-password"))) {
            String decryptedLine = decryptPassword(worker, line);
            if (!decryptedLine.equals(line)) {
                decrypted = true;
                if (trace) {
                    worker.trace("*" + orgLine + "\n\n", "out", device_id);
                    session.setTracer(null);
                }
                line = decryptedLine;
            }
            // password -> must prepend '?' with CTRL-V
            session.print(stringInsertCtrlV(line) + "\n");
        }

        // all other lines -> send normal
        else {
            session.print(line+"\n");
        }

        // Wait for echo
        if (line.length() > 500) {
            traceVerbose(worker, "Waiting for echo of long line = "+line.length()+" characters");
            expect_echo(line, worker);
        } else {
            session.expect(new String[] {
                    Pattern.quote(line),
                    ".*INFO: ASAv platform license state is Licensed.*"},
                worker);
        }

        // Enable tracing if disabled due to sending decrypted clear text passwords
        if (decrypted) {
            if (trace) {
                session.setTracer(worker);
                worker.trace("*" + orgLine + "\n", "out", device_id);  // simulated echo
            }
            line = orgLine;
        }

        // Wait for prompt
        NedExpectResult res = session.expect(plw, worker);
        if (res.getHit() == 0 || res.getHit() == 5) {
            // Received: "Continue?[confirm] or [yes/no]"
            if (isCliError(worker, cmd, res.getText(), line) < 0) {
                throw new ApplyException(line, res.getText(), false, false);
            }
            // Send reply and wait for prompt
            if (res.getHit() == 0) {
                session.print("c");
            } else {
                session.print("yes\n");
            }
            res = session.expect(plw, worker);
        }

        // Verify sub-mode
        boolean isAtTop;
        if (res.getHit() == 1 || res.getHit() == 2)
            isAtTop = true;
        else if (res.getHit() == 3)
            isAtTop = false;
        else {
            throw new ApplyException(line, "exited from config mode", false, false);
        }

        // When initializing new contexts, ignore all errors from init script
        String reply = res.getText();
        if (haveContext && contextName == null && line.contains("config-url ")) {
            String lines[] = reply.split("\n|\r");
            for (int i = 0 ; i < lines.length ; i++) {
                if (lines[i].contains("Creating context with default config")) {
                    return isAtTop;
                }
                if (lines[i].contains("INFO: Context ")
                    && lines[i].contains(" was created")) {
                    return isAtTop;
                }
            }
        }

        // sla monitor patch (need to remove schedule before modifying)
        String match;
        if (reply.contains("Entry already running and cannot be modified")
            && (match = getMatch(line, "sla monitor (\\d+)")) != null) {
            String show = print_line_exec(worker, "show run sla monitor " + match);
            String schedule = findLine(show, "sla monitor schedule " + match);
            if (schedule != null) {
                print_line_wait(worker, cmd, "no " + schedule, 0, null);
                traceInfo(worker, "Adding delayed config: " + stringQuote(schedule));
                delayedConfig += schedule + "\n";
                return print_line_wait(worker, cmd, line, retrying, null);
            }
        }

        //
        // Check device reply [ < 0 = ERROR, 0 = success, > 0 retries ]
        //
        int error = isCliError(worker, cmd, reply, line);
        if (error == 0) {
            return isAtTop;
        }
        else if (error < 0) {
            throw new ExtendedApplyException(line, reply, isAtTop, true);
        }

        //
        // Retrying
        //

        // Already tried enough, give up
        if (++retrying > error) {
            throw new ExtendedApplyException(line, reply, isAtTop, true);
        }

        // Sleep a second (max 60 times)
        if (retrying == 0) {
            worker.setTimeout(10*60*1000);
        }
        try {
            Thread.sleep(1*1000);
        } catch (InterruptedException e) {
            traceVerbose(worker, "sleep interrupted");
        }

        // Retry line once more
        logInfo(worker, "Retrying, attempt #" + retrying);
        return print_line_wait(worker, cmd, line, retrying, meta);
    }

    private boolean enterConfig(NedWorker worker, int cmd, boolean changetoSystem)
        throws NedException, IOException, SSHSessionException {
        NedExpectResult res = null;

        // Enter config mode
        session.print("config t\n");
        res = session.expect(ec, worker);
        if (res.getHit() > 2) {
            worker.error(cmd, res.getText());
            return false;
        } else if (res.getHit() == 0) {
            session.print("yes\n");
            res = session.expect(ec2, worker);
            if (res.getHit() > 2) {
                worker.error(cmd, res.getText());
                return false;
            }
        }

        // Multi-context admin - always start in system (context)
        if (changetoSystem && haveContext && contextName == null) {
            session.print("changeto system\n");
            session.expect("changeto system", worker);
            res = session.expect(config_prompt, worker);
            if (res.getText().contains("ERROR: ") || res.getText().contains("Command not valid ")) {
                worker.error(cmd, "Failed to enter system context : " + res.getText());
                return false;
            }
        }

        return true;
    }

    private void moveToTopConfig(NedWorker worker)
        throws IOException, SSHSessionException {
        NedExpectResult res;

        traceVerbose(worker, "moveToTopConfig()");

        while (true) {
            session.print("exit \n");
            session.expect("exit", worker);
            res = session.expect(config_prompt);
            traceVerbose(worker, "Matched pattern["+res.getHit()+"] '"+res.getMatch()+"'");
            if (res.getHit() == 0)
                return;
        }
    }

    private void exitConfig(NedWorker worker)
        throws IOException, SSHSessionException {
        NedExpectResult res;
        Pattern[] cprompt = new Pattern[] {
            Pattern.compile("\\A\\S*\\(config\\)#"),
            Pattern.compile("\\A.*\\(.*\\)#"),
            Pattern.compile("\\A\\S*#")
        };

        // Send ENTER to begin by checking our mode
        traceVerbose(worker, "exitConfig() - sending newline");
        session.print("\n");

        // Then keep sending exit until we leave config mode
        while (true) {
            res = session.expect(cprompt, worker);
            if (res.getHit() == 2)
                return;
            session.print("exit\n");
            session.expect("exit", worker);
        }
    }

    private void sendBackspaces(NedWorker worker, String cmd)
        throws Exception {
        if (cmd.length() <= 1)
            return;
        String buf = "";
        for (int i = 0; i < cmd.length() - 1; i++)
            buf += "\u0008"; // back space
        traceVerbose(worker, "Sending " + (cmd.length()-1) + " backspace(s)");
        session.print(buf);
    }

    private void exitPrompting(NedWorker worker) throws IOException, SSHSessionException {
        traceVerbose(worker, "Sending CTRL-C and CTRL-Z");
        session.print("\u0003");
        session.print("\u001a");
    }

    private String modifyBanner(NedWorker worker, String data)
        throws NedException {

        return data;
    }

    private String reorderData(NedWorker worker, String data) {
        String lines[] = data.split("\n");

        //
        // Pass 1 - reorder top mode config
        //
        StringBuilder middle = new StringBuilder();
        StringBuilder first = new StringBuilder();
        StringBuilder last = new StringBuilder();
        for (int n = 0; n < lines.length; n++) {

            // Reverse order of line vty deletes [RT24125]
            if (lines[n].startsWith("no failover group ")) {
                traceVerbose(worker, "DIFFPATCH: moved '"+lines[n]+"' last (reversed)");
                last.insert(0, lines[n]+"\n");
            }

            // Default case
            else {
                middle.append(lines[n]+"\n");
            }
        }
        data = "\n" + first.toString() + middle.toString() + last.toString();

        return data;
    }

    private void aclListToTrace(NedWorker worker, ArrayList<String> aclList, String pfx) {
        for (int i = 1; i < aclList.size(); i++) {
            traceVerbose(worker, " "+pfx+"ACL: ["+i+"] = "+aclList.get(i));
        }
    }

    private String aclKey(String line) {
        int i;
        if (line.indexOf(" remark") >= 0)
            return line;
        if ((i = line.indexOf(" log")) > 0)
            line = line.substring(0, i);
        if ((i = line.indexOf(" inactive")) > 0)
            line = line.substring(0, i);
        return line.trim();
    }

    private int aclIndexOf(NedWorker worker, ArrayList<String> aclList, String line) {
        String key = aclKey(line);
        //traceVerbose(worker, "ACL: Looking for '"+key+"'");
        for (int i = 1; i < aclList.size(); i++) {
            String keyx = aclKey(aclList.get(i));
            //traceVerbose(worker, "ACL: Comparing '"+keyx+"' at index="+i);
            if (key.equals(keyx))
                return i;
        }
        return -1;
    }

    private boolean aclEquals(ArrayList<String> aclList, int index, String line) {
        String linex = aclList.get(index);
        return linex.equals(line);
    }

    private ArrayList<String> aclListGet(NedWorker worker, String contextName, String name, String fullname, int th)
        throws NedException {
        String path = "/ncs:devices/device{"+device_id+"}/config/asa:access-list/access-list-id{"+name+"}";

        if (contextName != null)
            path = path.replace("asa:", "asa:changeto/context{"+contextName+"}/");
        try {
            NavuContainer root;
            NavuContext context = new NavuContext(mm, th);
            try {
                ConfPath cp = new ConfPath(path);
                root = (NavuContainer)new NavuContainer(context).getNavuNode(cp);
            } catch (Exception e) {
                traceVerbose(worker, "access-list "+fullname+" does not exist");
                return null;
            }
            if (root == null || !root.exists()) {
                traceVerbose(worker, "access-list "+fullname+" not found");
                return null;
            }
            NavuList list = root.list("asa", "rule");
            if (list == null || list.isEmpty()) {
                traceInfo(worker, "WARNING access-list "+fullname+" is empty");
                return null;
            }

            ArrayList<String> aclList = new ArrayList<String>();
            aclList.add("access-list "+name+" ");
            for (NavuContainer entry : list.elements()) {
                String val = "\"" + entry.leaf("asa", "id").valueAsString().trim() + "\"";
                if (entry.leaf("asa", "log").exists()) {
                    val += " log";
                    try {
                        NavuLeaf leaf = entry.leaf("asa", "level");
                        if (leaf != null && leaf.exists())
                            val += " " + leaf.valueAsString().trim();
                    } catch (Exception ignore) { } // patch for NSO null-exception with 'when' statement
                    try {
                        NavuLeaf leaf = entry.leaf("asa", "interval");
                        if (leaf != null && leaf.exists())
                            val += " interval " + leaf.valueAsString().trim();
                    } catch (Exception ignore) { } // patch for NSO null-exception with 'when' statement
                }
                if (entry.leaf("asa", "inactive").exists())
                    val += " inactive";
                aclList.add(val);
            }
            return aclList;
        } catch (Exception e) {
            throw new NedException("aclListGet : "+e.getMessage());
        }
    }

    private String aclGetName(String line, String nextline, String name) {
        line = line.trim();
        nextline = nextline.trim();
        if (line.startsWith("! insert") || line.startsWith("! move"))
            line = nextline;
        if (name == null) {
            if (line.startsWith("access-list ") || line.startsWith("no access-list "))
                return getMatch(line, "access-list (\\S+)");
        }
        else {
            if (line.startsWith("access-list "+name+" "))
                return name;
            if (line.startsWith("no access-list "+name+" "))
                return name;
        }
        return null;
    }

    private boolean aclNextIsSame(String name, String[] lines, int n) {
        if (n + 1 >= lines.length)
            return false;
        String line = lines[n+1];
        String nextline = (n + 2 < lines.length) ? lines[n+2] : "";
        if (aclGetName(line, nextline, name) == null)
            return false;
        return true;
    }

    private String aclCmdTransform(String line) {
        line = line.replace(" informational", "");
        line = line.replace(" interval 300", "");
        if (isDevice()) {
            if (!line.startsWith("no ")
                && line.contains("\"extended ") && !line.contains(" log")) {
                if (line.endsWith(" inactive"))
                    line = line.replace(" inactive", " log disable inactive");
                else
                    line += " log disable";
            }
            line = line.replace("\"", "");
        }
        return line;
    }

    private void aclCmdAdd(String line, StringBuilder sb) {
        line = aclCmdTransform(line);
        sb.append(line+"\n");
    }

    private void aclCmdAdd(String line, ArrayList<String> cmdList) {
        line = aclCmdTransform(line);
        cmdList.add(line);
    }

    /*
     * modifyACL
     */
    private String modifyACL(NedWorker worker, String data, int fromTh, int toTh)
        throws NedException {
        String name, fullname;

        String[] lines = data.split("\n");
        StringBuilder sb = new StringBuilder();
        String context = null;
        for (int n = 0; n < lines.length; n++) {
            String line = lines[n];
            String nextline = (n + 1 < lines.length) ? lines[n+1] : "";

            if (line.startsWith("changeto context "))
                context = getMatch(line, "changeto context[ ]+(\\S+)");
            else if (context != null && line.equals("!"))
                context = null;

            if ((name = aclGetName(line, nextline, null)) == null) {
                sb.append(line+"\n");
                continue;
            }
            fullname = name;
            if (context != null)
                fullname = "context{"+context+"}/"+name;

            // Found access-list entry, look up from trans in CDB
            ArrayList<String> aclList = aclListGet(worker, context, name, fullname, fromTh);

            //
            // New access-list
            //
            if (aclList == null || aclList.size() == 1) {
                traceVerbose(worker, "ACL: creating access-list "+fullname);
                for (; n < lines.length; n++) {
                    line = lines[n];
                    if (line.startsWith("! insert") || line.startsWith("! move"))
                        continue;  // Trim, not needed

                    traceVerbose(worker, "ACL: adding '"+line+"'");
                    aclCmdAdd(line, sb);

                    // Check if next entry same access-list
                    if (aclNextIsSame(name, lines, n) == false)
                        break;
                }
                continue;
            }

            //
            // NETSIM - Resetting access-list
            //
            if (isNetsim()) {
                // Delete access-list on device
                sb.append("no access-list "+name+"\n");

                // Get current (to-transaction) access-list
                aclList = aclListGet(worker, context, name, fullname, toTh);
                if (aclList != null) {
                    traceVerbose(worker, "ACL: resetting access-list "+fullname+", new size="+(aclList.size()-1));
                    aclListToTrace(worker, aclList, "to");
                    for (int i = 1; i < aclList.size(); i++) {
                        sb.append("access-list "+name+" "+aclList.get(i)+"\n");
                    }
                } else {
                    traceVerbose(worker, "ACL: deleting access-list "+fullname);
                }

                // Flush all ACL command(s) from NSO
                for (; n < lines.length; n++) {
                    line = lines[n];
                    nextline = (n + 1 < lines.length) ? lines[n+1] : "";
                    if (aclGetName(line, nextline, name) == null) {
                        n--; // Look at this line again in top loop
                        break;
                    }
                }
                continue;
            }

            //
            // DEVICE - Existing access-list, apply changes
            //
            int fromsize = aclList.size() - 1;
            traceVerbose(worker, "ACL: modifying access-list "+fullname+" size="+fromsize);
            aclListToTrace(worker, aclList, "from");
            int index = -1;
            ArrayList<String> cmdList = new ArrayList<String>();
            for (; n < lines.length; n++) {
                line = lines[n];
                String trimmed = line.trim();
                nextline = (n + 1 < lines.length) ? lines[n+1] : "";
                if (aclGetName(line, nextline, name) == null) {
                    n--; // Look at this again in top loop
                    break;
                }

                // ! insert after|before <line>
                // ! move after|before <line>
                if (trimmed.startsWith("! ")) {
                    n = n + 1;
                    String[] group = getMatches(worker, line, "! (move|insert) (after|before) (.*)");
                    if (Integer.parseInt(group[0]) != 3)
                        throw new NedException("ACL: ERROR malformed obu: "+nextline);
                    String rule = nextline.trim().replace("access-list "+name+" ", "");

                    // If moving a rule, first remove it from ACL cache and generate no-command
                    if (group[1].equals("move")) {
                        traceVerbose(worker, "ACL: moving '"+nextline+"' "+group[2]+" '"+group[3]+"'");
                        index = aclIndexOf(worker, aclList, rule);
                        if (index == -1)
                            throw new NedException("ACL: ERROR finding rule to move: '"+nextline+"'");
                        aclList.remove(rule);
                        aclCmdAdd("no "+nextline, cmdList);
                    } else {
                        traceVerbose(worker, "ACL: inserting '"+nextline+"' "+group[2]+" '"+group[3]+"'");
                    }

                    // Now look up where to insert it
                    index = aclIndexOf(worker, aclList, group[3]);
                    if (index == -1)
                        throw new NedException("ACL: ERROR finding rule for: '"+line+"'");
                    if (group[2].contains("after"))
                        index++;
                    // Update the internal ACL cache and add device command
                    aclList.add(index, rule);
                    aclCmdAdd("access-list "+name+" line "+index+" "+rule, cmdList);
                    index = -1; // Reset index to add next entry last
                }

                // no access-list <name> <rule>
                else if (trimmed.startsWith("no access-list ")) {
                    String rule = trimmed.replace("no access-list "+name+" ", "");
                    index = aclIndexOf(worker, aclList, rule);
                    if (index == -1)
                        throw new NedException("ACL: ERROR finding '"+line+"' to delete");
                    traceVerbose(worker, "ACL: deleting '"+line+"'");
                    aclList.remove(rule);
                    aclCmdAdd(line, cmdList);
                    index = -1; // Only nextline is inserted/moved, next one added last
                }

                // access-list <name> <rule>
                else {
                    String rule = trimmed.replace("access-list "+name+" ", "");
                    int current = aclIndexOf(worker, aclList, rule);
                    if (current != -1 && aclEquals(aclList, current, rule)) {
                        traceVerbose(worker, "ACL: ignoring duplicate '"+line+"' (index="+index+")");
                        index = current + 1;
                        continue;
                    } else if (current != -1) {
                        traceVerbose(worker, "ACL: updating '"+line+"'");
                        index = current + 1;
                        aclList.set(current, rule);
                        aclCmdAdd(line, cmdList);
                    } else if (index != -1) {
                        traceVerbose(worker, "ACL: adding '"+line+"' at index="+index);
                        aclList.add(index, rule);
                        aclCmdAdd("access-list "+name+" line "+index+" "+rule, cmdList);
                        index++;
                    } else {
                        traceVerbose(worker, "ACL: adding '"+line+"'");
                        aclList.add(rule);
                        aclCmdAdd(line, cmdList);
                    }
                }
            }
            aclListToTrace(worker, aclList, "to");

            // Assert that list is not unnecessarily temporarily deleted
            int size = fromsize;
            for (int c = 0; c < cmdList.size() - 1; c++) {
                line = cmdList.get(c);
                nextline = cmdList.get(c+1);
                size += (line.startsWith("access-list ") ? 1 : -1);
                if (size > 0)
                    continue;

                // This line must be a delete and the next must be an add
                if (nextline.startsWith("no "))
                    throw new NedException("ACL: ERROR malformed line: '"+nextline+"'");

                // Trim rule delete before create to avoid removing locked access-list
                if (aclKey(line.substring(3)).equals(aclKey(nextline))) {
                    traceInfo(worker, "ACL: stripping '"+line+"'");
                    cmdList.remove(c);
                    continue;
                }

                // Put the create before the delete to avoid removing locked access-list
                traceInfo(worker, "ACL: swapping '"+line+"' and '"+nextline+"'");
                size++;
                cmdList.set(c++, nextline);
                cmdList.set(c, line);
            }

            // Add access-list lines to string buffer for output to device
            for (int c = 0; c < cmdList.size(); c++) {
                sb.append(cmdList.get(c)+"\n");
            }
        }
        data = sb.toString();

        // TODO: WARN for duplicate access-list lines on device because not supported
        return data;
    }

    private String modifyData(NedWorker worker, String data)
        throws NedException, IOException, SSHSessionException, ApplyException {

        // Attach to CDB
        int fromTh;
        int toTh;
        try {
            fromTh = worker.getFromTransactionId();
            toTh = worker.getToTransactionId();
            mm.attach(fromTh, 0, worker.getUsid());
            mm.attach(toTh, 0);
        } catch (Exception e) {
            throw new NedException("ERROR : failed to attach to CDB", e);
        }

        //
        // Scan meta-data and modify data
        //
        data = metaData.modifyData(worker, data, mm, fromTh, toTh);

        //
        // Modify ACL - inject line numbers and log disable
        //
        data = modifyACL(worker, data, fromTh, toTh);

        //
        // Reorder data
        //
        data = reorderData(worker, data);

        // Detach from CDB
        try {
            mm.detach(fromTh);
            mm.detach(toTh);
        } catch (Exception e) {
            throw new NedException("ERROR : failed to detach from CDB", e);
        }

        //
        // LINE-BY-LINE - applyConfig
        //
        StringBuilder buffer = new StringBuilder();
        String toptag = "";
        String meta = "";
        String lines[] = data.split("\n");
        String match;
        data = null; // to avoid modifying it by accident
        for (int n = 0; n < lines.length; n++) {
            String line = lines[n];
            String trimmed = line.trim();
            if (trimmed.isEmpty())
                continue;
            String cmd = trimmed.startsWith("no ") ? trimmed.substring(3) : trimmed;
            String nextline = (n + 1 < lines.length) ? lines[n+1] : "";

            // Update meta-data
            if (trimmed.startsWith("! meta-data :: ")) {
                meta += (lines[n] + "\n");
                buffer.append(lines[n]+"\n");
                continue;
            }

            // Update toptag
            if (Character.isLetter(lines[n].charAt(0))) {
                toptag = trimmed;
            }

            //
            // context
            //
            if (cmd.startsWith("context ")
                && haveContext && contextName == null
                && (match = getMatch(trimmed, "context (\\S+)")) != null) {
                // Check cisco-asa-context-list if may access context
                if (!isSupportedContext(match)) {
                    traceInfo(worker, "context '"+match+"' not in cisco-asa-context-list");
                    throw new ApplyException("context '"+match
                                             +"' not in cisco-asa-context-list",true,false);
                }
            }

            //
            // no anyconnect-custom-data * * <value(s)>
            //
            if (trimmed.startsWith("no anyconnect-custom-data ")
                     && (match = getMatch(trimmed, "(no anyconnect-custom-data \\S+ \\S+) .*")) != null) {
                traceVerbose(worker, "transformed => '"+trimmed+"' to '"+match+"'");
                buffer.append(match+"\n");
                while (n+1 < lines.length && lines[n+1].trim().startsWith(match)) {
                    // Strip additional delete lines of same leaf-list
                    traceVerbose(worker, "transformed => stripped '"+lines[++n].trim()+"'");
                }
                continue;
            }

            //
            // NETSIM
            //
            if (isNetsim()) {
                meta = "";
                if (trimmed.equals("changeto system"))
                    continue;
                buffer.append(lines[n]+"\n");
                continue;
            }


            //
            // READ DEVICE BELOW
            //
            String output = null;

            //
            // pager - redisable pager in system mode
            //
            if (trimmed.startsWith("pager lines ")) {
                traceVerbose(worker, "transformed => redisabled terminal pager");
                buffer.append(lines[n]+"\n");
                buffer.append("terminal pager 0\n");
                continue;
            }

            //
            // tcp-map / tcp-options range
            //
            if (toptag.startsWith("tcp-map ") && trimmed.contains("tcp-options range ")) {
                output = lines[n].replaceAll("range (\\d+)", "range $1 $1");
            }

            //
            // banner exec|login|motd
            //
            else if (trimmed.startsWith("banner ")) {
                Pattern p = Pattern.compile("banner (\\S+)[ ]+(.*)");
                Matcher m = p.matcher(trimmed);
                if (m.find()) {
                    String name = m.group(1);
                    String message = stringDequote(m.group(2));
                    message = message.replaceAll("\r\n", "\nbanner "+name+" ");
                    String banner = "no banner "+name+"\nbanner "+name+" "+message;
                    traceVerbose(worker, "transformed => dequoted banner "+name);
                    lines[n] = banner;
                }
            }

            //
            // crypto ca certificate chain * / certificate *
            //
            else if (toptag.startsWith("crypto ca certificate chain ")
                     && lines[n].startsWith(" certificate ")
                     && nextline.trim().startsWith("\"")) {
                // Add certificate line and dequote certificate
                traceVerbose(worker, "output => dequoted '"+trimmed+"'");
                buffer.append(lines[n++]+"\n");
                lines[n] = stringDequote(lines[n].trim()); // note: prompt shows after each line
            }

            //
            // icmp <nameif> <permit|deny rule> -> icmp <permit|deny rule> <nameif>
            //
            else if (getMatch(line, "(?:no )?icmp (\\S+) (?:permit|deny) ") != null) {
                output = line.replaceAll("icmp (\\S+) (.+)", "icmp $2 $1");
            }

            // no monitor-interface
            else if (line.contains("no monitor-interface ")
                     && !line.contains("service-module")) {
                output = line.replaceAll("no monitor-interface (\\S+) disable",
                                         "monitor-interface $1");
                output = output.replaceAll("no monitor-interface (\\S+) enable",
                                           "no monitor-interface $1");
            }

            // monitor-interface
            else if (trimmed.startsWith("monitor-interface ")
                     && !line.contains("service-module")) {
                output = line.replaceAll("monitor-interface (\\S+) disable",
                                         "no monitor-interface $1");
                output = output.replaceAll(" enable", "");
            }

            // no disable passive-interface
            else if (trimmed.startsWith("no disable passive-interface ")) {
                output = line.replace("no disable passive-interface ",
                                      "passive-interface ");
            }

            // disable passive-interface
            else if (trimmed.startsWith("disable passive-interface ")) {
                output = line.replace("disable passive-interface ",
                                      "no passive-interface ");
            }

            // no no-list (generic trick for no-lists)
            else if (line.matches("^\\s*no .* no-list .*$")
                     && !line.contains(" service-module")) {
                output = line.replaceAll("no (.*) no-list (.*)", "$1 $2");
            }

            // no-list (generic trick for no-lists)
            else if (line.contains("no-list ")
                     && !line.contains(" service-module")) {
                output = "no " + line.replace("no-list ", "");
            }

            // noconfirm delete's:
            else if (trimmed.startsWith("no crypto ca trustpoint")
                     || line.matches("^\\s*no context (\\S+)\\s*$")) {
                output = line + " noconfirm";
            }

            // Ignore delete of certificates
            else if (trimmed.startsWith("no crypto ca certificate chain ")) {
                output = "!ignored on device: " + line;
            }

            // Ignore delete of changeto context
            else if (line.startsWith("no changeto context ")) {
                output = "!ignored on device: " + line;
            }

            // fragment, reorder
            else if (trimmed.matches("^(?:no )?fragment \\S+ \\S+ .*$")) {
                output = line.replaceFirst("fragment (\\S+) (\\S+) (\\S+)",
                                           "fragment $2 $3 $1");
            }

            //
            // Transform lines[n] -> XXX
            //
            if (output != null && !output.equals(lines[n])) {
                if (output.isEmpty())
                    traceVerbose(worker, "transformed => stripped '"+trimmed+"'");
                else
                    traceVerbose(worker, "transformed => '"+trimmed+"' to '"+output.trim()+"'");
                lines[n] = output;
            }

            // Append to buffer
            if (lines[n] != null && !lines[n].isEmpty()) {
                buffer.append(lines[n]+"\n");
            }
            meta = "";
        }

        data = "\n" + buffer.toString();
        return data;
    }


    /**
     * applyConfig
     *
     */
    private String delayedConfig;
    public void applyConfig(NedWorker worker, int cmd, String data)
        throws NedException, IOException, SSHSessionException, ApplyException {

        // Clear cached config
        delayedConfig = "";
        lastGetConfig = null;
        lastTransformedConfig = null;

        logInfo(worker, "APPLY-CONFIG TRANSFORMING");

        // Modify data
        data = modifyData(worker, data);
        traceVerbose(worker, "\nAPPLY_AFTER:\n"+data);

        // Enter config mode
        if (!enterConfig(worker, cmd, true)) {
            throw new NedException("applyConfig() :: Failed to enter config mode");
        }

        // Send all lines to the device, one at a time
        logInfo(worker, "APPLY-CONFIG SENDING");
        String lines[] = data.split("\n");
        String meta = "";
        long lastTime = System.currentTimeMillis();
        boolean isAtTop = true;
        try {
            // Apply one line at a time
            for (int i = 0; i < lines.length; i++) {
                String trimmed = lines[i].trim();
                if (trimmed.isEmpty())
                    continue;

                // Ignore sending meta-data to device, cache it temporarily
                if (trimmed.startsWith("! meta-data :: ")) {
                    meta += (trimmed + "\n");
                    continue;
                }
                // Ignore all other comments
                else if (trimmed.startsWith("!")) {
                    continue;
                }

                // Update timeout
                long time = System.currentTimeMillis();
                if ((time - lastTime) > (0.8 * writeTimeout)) {
                    lastTime = time;
                    worker.setTimeout(writeTimeout);
                }

                // Send line to device
                isAtTop = print_line_wait(worker, cmd, trimmed, 0, meta);
                meta = "";
            }

            // Make sure we have exited from all submodes
            if (!isAtTop)
                moveToTopConfig(worker);

            // Apply delayed config (simple commands, can't change mode, cant be modified)
            String delayedLines[] = delayedConfig.trim().split("\n");
            for (int i = 0; i < delayedLines.length; i++) {
                if (!delayedLines[i].trim().isEmpty()) {
                    traceInfo(worker, "Sending delayed config:");
                    print_line_wait(worker, cmd, delayedLines[i].trim(), 0, null);
                }
            }
        }
        catch (ApplyException e) {
            if (!e.isAtTop)
                moveToTopConfig(worker);
            if (e.inConfigMode)
                exitConfig(worker);
            throw e;
        }

        exitConfig(worker);

        // All commands accepted by device, prepare caching of secrets and defaults
        secrets.prepare(worker, lines);

        logInfo(worker, "DONE APPLY-CONFIG");
    }

    private class ExtendedApplyException extends ApplyException {
        public ExtendedApplyException(String line, String msg,
                                      boolean isAtTop,
                                      boolean inConfigMode) {
            super("command: "+line+": "+msg, isAtTop, inConfigMode);
         }
    }

    public void revert(NedWorker worker, String data)
        throws Exception {

        if (trace)
            session.setTracer(worker);

        this.applyConfig(worker, NedCmd.REVERT_CLI, data);

        if (this.writeMemoryMode.equals("on-commit")) {
            if (haveContext && contextName == null) {
                print_line_wait_oper(worker, NedCmd.COMMIT, "changeto system");
                print_line_wait_oper(worker, NedCmd.COMMIT, "write memory all /noconfirm");
            } else {
                print_line_wait_oper(worker, NedCmd.COMMIT, "write memory");
            }
        }

        worker.revertResponse();
    }

    public void commit(NedWorker worker, int timeout)
        throws Exception {

        if (trace)
            session.setTracer(worker);

        if (this.writeMemoryMode.equals("on-commit")) {
            if (haveContext && contextName == null) {
                print_line_wait_oper(worker, NedCmd.COMMIT, "changeto system");
                print_line_wait_oper(worker, NedCmd.COMMIT, "write memory all /noconfirm");
            } else {
                print_line_wait_oper(worker, NedCmd.COMMIT, "write memory");
            }
        }

        // Cache secrets
        if (secrets.getNewEntries()) {
            traceInfo(worker, "SECRETS - caching encrypted secrets");
            String config = getConfig(worker);
            lastTransformedConfig = transformConfig(worker, false, config);
        }

        worker.commitResponse();
    }

    public void prepareDry(NedWorker worker, String data)
        throws Exception {

        if (trace && session != null)
            session.setTracer(worker);

        logInfo(worker, "PREPARE-DRY");

        // ShowRaw used in debugging, to see cli commands before modification
        if (showRaw) {
            logInfo(worker, "DONE PREPARE-DRY (raw)");
            showRaw = false;
            worker.prepareDryResponse(data);
            return;
        }

        StringBuilder sb = new StringBuilder();
        if (session == null)
            sb.append("! Generated offline\n");

        // Modify data
        data = modifyData(worker, data);
        String lines[] = data.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (!logVerbose && line.trim().startsWith("! meta-data :: "))
                continue;
            sb.append(line+"\n");
        }

        logInfo(worker, "DONE PREPARE-DRY");
        worker.prepareDryResponse(sb.toString());
    }

    private void mmFinishTrans(NedWorker worker) {
        if (thr != -1) {
            try {
                mm.finishTrans(thr);
                thr = -1;
            } catch (Exception err) {
                logError(worker, "mm.finishTrans() ERROR :: ", err);
            }
        }
    }

    public void close(NedWorker worker)
        throws NedException, IOException {
        logInfo(worker, "close(worker)");
        close();
    }

    public void close() {

        // Finish read transaction
        mmFinishTrans(null);

        // Unregister Resources
        try {
            if (cdbOper != null)
                cdbOper.endSession();
            ResourceManager.unregisterResources(this);
        } catch (Exception e) {
            LOGGER.error("ERROR unRegistering Resources", e);
        }

        LOGGER.info("CLOSE("+device_id+") ==> OK");
        super.close();
    }

    public void getTransId(NedWorker worker)
        throws Exception {
        String res = null;

        if (trace)
            session.setTracer(worker);

        //
        // Use last cached transformed config from applyConfig() secret code
        //
        if (transActionIdMethod.startsWith("config-hash") && lastTransformedConfig != null) {
            traceInfo(worker, "Using last config from SECRETS for checksum calculation");
            res = lastTransformedConfig;
            lastGetConfig = null;
            lastTransformedConfig = null;
        }

        //
        // cisco-asa-transaction-id-method config-hash-cached
        //
        else if (transActionIdMethod.equals("config-hash-cached") && lastGetConfig != null) {
            traceInfo(worker, "Using cached config from last show (sync-from)");
            res = transformConfig(worker, false, lastGetConfig);
            lastGetConfig = null;
        }

        //
        // cisco-asa-transaction-id-method show-checksum
        //
        else if (transActionIdMethod.equals("show-checksum")
                 && isDevice()
                 && (haveContext == false || contextName != null)) {
            traceInfo(worker, "Using device checksum 'Cryptochecksum'");
            res = getRunCryptochecksum(session, worker);
            if (res != null) {
                traceInfo(worker, "TransactionId = Cryptochecksum:" + res);
                worker.getTransIdResponse(res);
                return;
            }
        }

        // Get configuration to calculate checksum
        if (res == null) {
            traceInfo(worker, "Using config from device for config-hash");
            String config = getConfig(worker);
            res = transformConfig(worker, false, config);
        }
        worker.setTimeout(readTimeout);

        // Calculate checksum of running-config
        byte[] bytes = res.getBytes("UTF-8");
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] thedigest = md.digest(bytes);
        BigInteger md5Number = new BigInteger(1, thedigest);
        String md5String = md5Number.toString(16);

        traceInfo(worker, "TransactionId = " + md5String);

        worker.getTransIdResponse(md5String);
    }

    private String stringQuote(String aText) {
        StringBuilder result = new StringBuilder();
        StringCharacterIterator iterator =
            new StringCharacterIterator(aText);
        char character =  iterator.current();
        result.append("\"");
        while (character != CharacterIterator.DONE ){
            if (character == '"')
                result.append("\\\"");
            else if (character == '\\')
                result.append("\\\\");
            else if (character == '\b')
                result.append("\\b");
            else if (character == '\n')
                result.append("\\n");
            else if (character == '\r')
                result.append("\\r");
            else if (character == (char) 11) // \v
                result.append("\\v");
            else if (character == '\f')
                result.append("'\f");
            else if (character == '\t')
                result.append("\\t");
            else if (character == (char) 27) // \e
                result.append("\\e");
            else
                // The char is not a special one, add it to the result as is
                result.append(character);
            character = iterator.next();
        }
        result.append("\"");
        return result.toString();
    }

    private String stringDequote(String aText) {
        if (aText.indexOf("\"") != 0)
            return aText;

        aText = aText.substring(1,aText.length()-1);

        StringBuilder result = new StringBuilder();
        StringCharacterIterator iterator =
            new StringCharacterIterator(aText);
        char c1 = iterator.current();

        while (c1 != CharacterIterator.DONE ) {
            if (c1 == '\\') {
                char c2 = iterator.next();
                if (c2 == CharacterIterator.DONE )
                    result.append(c1);
                else if (c2 == 'b')
                    result.append('\b');
                else if (c2 == 'n')
                    result.append('\n');
                else if (c2 == 'r')
                    result.append('\r');
                else if (c2 == 'v')
                    result.append((char) 11); // \v
                else if (c2 == 'f')
                    result.append('\f');
                else if (c2 == 't')
                    result.append('\t');
                else if (c2 == 'e')
                    result.append((char) 27); // \e
                else {
                    result.append(c2);
                }
            }
            else {
                // The char is not a special one, add it to the result as is
                result.append(c1);
            }
            c1 = iterator.next();
        }
        return result.toString();
    }

    private static int indexOf(Pattern pattern, String s, int start) {
        Matcher matcher = pattern.matcher(s);
        return matcher.find(start) ? matcher.start() : -1;
    }

    private static int findString(String search, String text) {
        return indexOf(Pattern.compile(search), text, 0);
    }

    private String getMatch(String text, String regexp) {
        Pattern pattern = Pattern.compile(regexp);
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find())
            return null;
        return matcher.group(1);
    }

    private String[] getMatches(NedWorker worker, String text, String regexp) {
        Pattern pattern = Pattern.compile(regexp);
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find())
            return null;
        String[] matches = new String[matcher.groupCount()+1];
        matches[0] = ""+matcher.groupCount();
        //traceVerbose(worker, "MATCH-COUNT"+matches[0]);
        for (int i = 1; i <= matcher.groupCount(); i++) {
            matches[i] = matcher.group(i);
            //traceVerbose(worker, "MATCH-"+i+"="+matches[i]);
        }
        return matches;
    }

    private static String replaceLast(String string, String toReplace, String replacement) {
        int pos = string.lastIndexOf(toReplace);
        if (pos > -1) {
            return string.substring(0, pos)
                + replacement
                + string.substring(pos + toReplace.length(), string.length());
        } else {
            return string;
        }
    }

    private boolean isTopExit(String line) {
        if (line.startsWith("exit"))
            return true;
        if (line.startsWith("!") && line.trim().equals("!"))
            return true;
        return false;
    }

    private String linesToString(String lines[]) {
        StringBuilder string = new StringBuilder();
        for (int n = 0; n < lines.length; n++) {
            if (lines[n].isEmpty())
                continue;
            string.append(lines[n]+"\n");
        }
        return "\n" + string.toString() + "\n";
    }

    private boolean isSupportedContext(String context) {
        if (contextList.size() == 0)
            return true;
        for (int n = 0; n < contextList.size(); n++) {
            if (findString(contextList.get(n), context) >= 0) {
                return true;
            }
        }
        return false;
    }

	/**
	 * checkIfUserSessionExists  - This method checks if MAAPI has user session
     *
	 * @return
     *      true when session exists, false otherwise
	 */
    private boolean checkIfUserSessionExists() {
        int userSession = 0;

        try {
            userSession = mm.getMyUserSession();
        } catch (MaapiException e) {
            // user session does not exist
            return false;
        }
        catch (ConfException e) {
            // got a ConfException, so fail here
            return false;
        }
        catch (IOException e) {
        	// got an IOException, so fail here
            return false;
        }

        return (userSession != 0) ? true : false;
    }

    private String getRunCryptochecksum(CliSession tsession, NedWorker worker)
        throws Exception {
        String res = print_line_exec(tsession, worker, "show checksum");
        if (res.contains("ERROR: "))
            return null;
        int index = res.indexOf("Cryptochecksum:");
        if (index < 0)
            return null;
        return res.substring(index).replace(" ", "").trim();
    }

    private String getDskCryptochecksum(CliSession tsession, NedWorker worker, String config)
        throws Exception {
        if (config == null)
            config = print_line_exec(tsession, worker, "show startup-config | i ^Cryptochecksum");
        String dskCryptochecksum = findLine(config, "Cryptochecksum:");
        if (dskCryptochecksum == null)
            throw new NedException("Failed to find Cryptochecksum in startup-config");
        return dskCryptochecksum.trim();
    }

	/**
	 * getConfigFromAdmin  - This method gets configuration from admin session
     *      ASA currently does not support display of non-obfuscated running configuration
     *      for a user context. Only way to obtain non-obfuscated configuration is from
     *      system context by displaying startup configuration, which is saved as configUrl file.
     *      Consequently:
     *      - The NED setting use-startup-config must be set to true for contexts (method throws exception)
     *      - The NED setting cisco-asa-write-memory-setting should be set to on-commit.  This method does
     *        not check for this, but if configuration is not saved after modification, next attempt to display
     *        will result in exception due to non-matching checksums of running and startup configurations.
     *
     *      NED setting cisco-asa-transaction-id-method
     *          For the multi-context device where context are managed directly, value 'show-checksum' is most
     *          efficient. If in this situation the 'config-hash' is specified (which is default), the configuration
     *          needs to be obtained twice for each commit before and after for the purpose of calculating checksum.
     *          Each time configuration is obtained the session to admin context gets to be used, which is the ASA
     *          management bottle neck - only 5 SSH sessions supported by admin context, in contrast the number of
     *          user contexts can be up to 250.
     *
     *          Use of 'config-hash' allows checksum to be obtained without using admin session twice.
     *
	 * @param tsession
     *      CliSession to the user context
	 * @param worker
     *      NedWorker
	 * @return
     *      Configuration of user context retrieved from admin device
	 * @throws Exception
     *      When configuration can not be successfully obtained
	 */
    private String getConfigFromAdmin(CliSession tsession, NedWorker worker)
        throws Exception {

        // See if config is saved (by checksum comparison) to see if we can use admin-device
        String runCryptochecksum = getRunCryptochecksum(tsession, worker);
        if (runCryptochecksum == null)
            throw new NedException("Failed to get 'show checksum'");
        String dskCryptochecksum = getDskCryptochecksum(tsession, worker, null);
        if (dskCryptochecksum.equals(runCryptochecksum) == false) {
            traceInfo(worker, "WARNING: running-config not saved for context "+contextName+", can't use admin-device");
            return null;
        }

        traceInfo(worker, "admin-device: Getting "+contextName+" context config from "
                  +adminDeviceName+" using "+adminDeviceMethod);

        //
        // cisco-asa admin-device method ssh
        //
        String res;
        if (adminDeviceMethod.equals("ssh")) {
            setupAdminSSH(worker);
            res = print_line_exec(adminSession, worker, "more " + configUrl);
            closeAdminSSH(worker);
        }

        //
        // cisco-asa admin-device method maapi
        //
        else {
            boolean userSessionExists = checkIfUserSessionExists();
            if (!userSessionExists) {
                traceVerbose(worker, "admin-device: Opening new user session");
                mm.startUserSession("admin", InetAddress.getLocalHost(), "maapi",
                                    new String[] { "ncsadmin" },
                                    MaapiUserSessionFlag.PROTO_TCP);
            } else {
                traceVerbose(worker, "admin-device: Reusing existing user session");
            }

            int th = mm.startTrans(Conf.DB_RUNNING, Conf.MODE_READ);
            NavuContext ctx = new NavuContext(mm, th);
            NavuContainer root = new NavuContainer(ctx);
            String actionStr = "ncs:devices/ncs:device{%s}/live-status/asa-stats:exec/any";
            ConfPath actionConfPath = new ConfPath(actionStr, adminDeviceName);
            NavuNode actionNode = root.getNavuNode(actionConfPath);

            if (actionNode instanceof NavuAction) {
                ConfXMLParam[] input = new ConfXMLParam[2];
                input[0] = new ConfXMLParamValue("asa-stats", "context", new ConfBuf("system"));
                ConfList list = new ConfList();
                list.addElem(new ConfBuf("more " + configUrl));
                input[1] = new ConfXMLParamValue("asa-stats", "args", list);

                NavuAction action = (NavuAction) actionNode;
                ConfXMLParam[] result = null;
                traceInfo(worker, "admin-device: Dispatching action to "+adminDeviceName
                          +" to get "+contextName+" context config");
                result = action.call(input);
                mm.finishTrans(th);
                if (userSessionExists == false) {
                    traceVerbose(worker, "admin-device: Closing user session");
                    mm.endUserSession();
                }
                res = ((ConfXMLParamValue) result[0]).getValue().toString();
            } else {
                throw new NedException("admin-device: Failed to get action node '"
                                       +actionStr+"' for "+adminDeviceName);
            }
        }

        // Check configuration
        if (res == null)
            throw new NedException("admin-device: Failed to get saved configuration from '"
                                   +contextName+"' via "+adminDeviceName);
        if (res.contains("ERROR: ") || res.contains("%Error "))
            throw new NedException("admin-device: Failed to get saved configuration from '"
                                   +contextName+"' via "+adminDeviceName+" :: " + res);

        // Verify saved-config Cryptochecksum vs running
        dskCryptochecksum = getDskCryptochecksum(tsession, worker, res);
        if (dskCryptochecksum.equals(runCryptochecksum) == false)
            throw new NedException("The running-config " + runCryptochecksum
                                   + " and startup-config " + dskCryptochecksum + " checksum did not match");

        traceInfo(worker, "admin-device: Obtained "+contextName+" config from "+adminDeviceName);
        return res;
    }

    private String getConfigUsingMore(CliSession tsession, NedWorker worker, String context)
        throws Exception {

        // Get running-config Cryptochecksum
        String runCryptochecksum = "run";
        if (useStartupConfig == false) {
            if (!changeto_context(tsession, worker, context))
                return null;
            runCryptochecksum = getRunCryptochecksum(tsession, worker);
            if (runCryptochecksum == null) {
                traceInfo(worker, "ERROR: failed to get 'show checksum'");
                return null;
            }
        }

        // Change to system context
        if (!changeto_context(tsession, worker, "system"))
            throw new NedException("Failed to changeto system context");

        // Get filename for saved running-config
        String filename;
        String res = print_line_exec(tsession, worker, "show run context "+context+" | i config-url");
        if (res != null && (filename = getMatch(res.trim(), "config-url (\\S+)")) != null) {
            traceVerbose(worker, "Using config-url "+filename+" for saved running-config");
        } else {
            traceVerbose(worker, "Using context name '"+context+"' for saved running-config");
            filename = "disk0:/"+context+".cfg";
        }

        // Get saved-config using more command in system context
        res = print_line_exec(tsession, worker, "more "+filename);
        if (res.contains("ERROR: "))
            return null;

        // cisco-asa get-device-config-settings use-startup-config
        if (useStartupConfig) {
            traceInfo(worker, "use-startup-config = true, using saved config from context '"+context+"'");
            return res;
        }

        // Extract Cryptochecksum from saved config
        String dskCryptochecksum = "dsk";
        String line = findLine(res, "Cryptochecksum:");
        if (line != null)
            dskCryptochecksum = line.trim();
        traceVerbose(worker, "RUN Cryptochecksum = " + runCryptochecksum);
        traceVerbose(worker, "DSK Cryptochecksum = " + dskCryptochecksum);

        // If checksums match, we can use the config from the more command
        if (dskCryptochecksum.equals(runCryptochecksum)) {
            traceInfo(worker, "Cryptochecksum match, using saved config from context '"+context+"'");
            return res;
        }

        traceInfo(worker, "WARNING: running-config not saved for context "+context+", can't use more command");
        return null;
    }

    private String getContextConfig(NedWorker worker, String context)
        throws Exception {
        String res;

        // show fixed-config
        if (showFixed) {
            res = print_line_exec(worker, "show fixed-config");
        }

        // NETSIM
        else if (isNetsim()) {
            res = print_line_exec(worker, "show running-config");
        }

        // Multi-context user context (use admin session to get startup config)
        else if (contextName != null && adminDeviceName != null) {
            res = getConfigFromAdmin(session, worker);
        }

        // Multi-context admin showing context config (use more command)
        else if (context != null && writeMemoryMode.equals("on-commit")) {
            res = getConfigUsingMore(session, worker, context);
        }

        // System context or single mode device
        else {
            res = print_line_exec(worker, "more system:running-config");
            if (res.contains("ERROR:"))
                res = null;
        }

        // Use show running-config
        if (res == null) {
            if (context != null)
                changeto_context(worker, context);
            if (useStartupConfig)
                res = print_line_exec(worker, "show startup-config");
            else
                res = print_line_exec(worker, "show running-config");
        }

        // Reset timeout to give more time to parse output
        worker.setTimeout(readTimeout);

        // Strip beginning
        int n;
        int i = res.indexOf("Current configuration :");
        if (i >= 0) {
            n = res.indexOf("\n", i);
            res = res.substring(n+1);
        }
        i = res.indexOf("No entries found.");
        if (i >= 0) {
            n = res.indexOf("\n", i);
            res = res.substring(n+1);
        }

        // Strip 'end' and all text after
        i = res.lastIndexOf("\nend");
        if (i >= 0) {
            res = res.substring(0,i);
        }

        // Insert context name
        if (context != null && contextName == null) {
            res = "\nchangeto context "+context+"\n" + res;
        }

        return res;
    }

    private String getConfig(NedWorker worker)
        throws Exception {
        String res;

        if (haveContext && contextName == null) {
            String ctx = "";
            int i;

            // Get system (context) config
            traceVerbose(worker, "getConfig(system) - multi-context admin");

            changeto_context(worker, "system");
            res = getContextConfig(worker, null);

            // Get 'changeto' contexts config, skip non supported ones
            for (i = res.indexOf("\ncontext ");
                 i >= 0;
                 i = res.indexOf("\ncontext ", i)) {
                int nl = res.indexOf("\n", i+1);
                if (nl < 0)
                    break;
                String context = res.substring(i+9, nl).trim();
                if (isSupportedContext(context)) {
                    traceVerbose(worker, "getConfig("+context+")");
                    ctx = ctx + getContextConfig(worker, context);
                    i = i + 8; // step to next config
                    continue;
                }
                traceVerbose(worker, "getConfig() - ignoring context: "+context);
                int end = res.indexOf("\ncontext ", i+8);
                if (end < 0)
                    end = res.indexOf("\n!", i+8); // last block ends with "!"
                if (end < 0)
                    break;
                res = res.substring(0,i) + res.substring(end-1);
            }
            res = res + ctx;
        } else {
            traceVerbose(worker, "getConfig()");
            res = getContextConfig(worker, null);
        }

        return res;
    }

    private String transformContextConfig(NedWorker worker, String context, boolean convert, String res)
        throws Exception {
        int i, n;
        String match;

        logInfo(worker, "TRANSFORMING CONFIG context="+(context != null ? context : ""));

        // NETSIM - return early
        if (isNetsim() && showFixed == false) {
            return res;
        }

        //
        // Update secrets - replace encrypted secrets with cleartext if not changed
        //
        logVerbose(worker, "TRANSFORMING - updating secrets");
        res = secrets.update(worker, res, convert);


        //
        // LINE-BY-LINE GET TRANSFORMATIONS
        //

        logVerbose(worker, "TRANSFORMING - line-by-line patches");
        String lines[] = res.split("\n");
        String toptag = "";
        res = null; // to provoke crash if used below
        for (n = 0; n < lines.length; n++) {
            String input = null;
            String trimmed = lines[n].trim();
            if (trimmed.isEmpty())
                continue;

            // Update toptag
            if (isTopExit(lines[n])) {
                toptag = "";
            } else if (Character.isLetter(lines[n].charAt(0))) {
                toptag = trimmed;
            }

            //
            /// access-list
            //
            if (trimmed.startsWith("access-list ")) {
                // String quote the rule only
                Pattern pattern = Pattern.compile("(access-list \\S+ )((?:extended|webtype) [ \\S]+?)( (log|inactive).*)");
                Matcher matcher = pattern.matcher(trimmed);
                if (matcher.find()) {
                    String quoted = stringQuote(matcher.group(2).trim());
                    input = matcher.group(1) + quoted + matcher.group(3);
                    if (input.indexOf(" log disable") > 0)
                        input = input.replace(" log disable", "");
                } else {
                    Pattern pattern2 = Pattern.compile("(access-list \\S+ )((?:extended|standard|remark|ethertype|webtype) .*)");
                    Matcher matcher2 = pattern2.matcher(trimmed);
                    if (matcher2.find()) {
                        String quoted = stringQuote(matcher2.group(2).trim());
                        input = matcher2.group(1) + quoted;
                    }
                }
            }

            //
            // icmp *
            //
            else if (toptag.startsWith("icmp ") && trimmed.startsWith("icmp ")) {
                input = lines[n].replaceAll("icmp ((?:deny|permit) .+) (\\S+)", "icmp $2 $1");
            }

            //
            // fragment *
            //
            else if (toptag.startsWith("fragment ") && trimmed.startsWith("fragment ")) {
                input = lines[n].replaceAll("fragment (\\S+) (\\S+) (\\S+)",
                                            "fragment $3 $1 $2");
            }

            //
            // monitor-interface *
            // no monitor-interface *
            //
            else if (toptag.startsWith("monitor-interface ") && trimmed.startsWith("monitor-interface ")) {
                input = lines[n].replaceAll("monitor-interface (\\S+)", "monitor-interface $1 enable");
            } else if (lines[n].startsWith("no monitor-interface ")
                       && !trimmed.contains("monitor-interface service-module")) {
                input = lines[n].replaceAll("no monitor-interface (\\S+)",
                                            "monitor-interface $1 disable");
            }

            //
            // transform single lines
            //
            else if (trimmed.startsWith("no passive-interface ")) {
                input = lines[n].replace("no passive-interface ", "disable passive-interface ");
            }

            //
            // transform no-list lists/leaves
            //
            if (trimmed.startsWith("no logging message ")) {
                input = lines[n].replace("no logging message ", "logging message no-list ");
            } else if (trimmed.startsWith("no service resetoutbound interface ")) {
                input = lines[n].replace("no service resetoutbound interface ",
                                         "service resetoutbound interface no-list ");
            }

            //
            // strip single lines
            //
            else if (trimmed.equals("boot-start-marker") || trimmed.equals("boot-end-marker")) {
                input = "";
            } else if (trimmed.startsWith("alias ")) {
                input = "";
            } else if (trimmed.startsWith(":")) {
                input = "";
            } else if (trimmed.startsWith("ASA Version")) {
                input = "";
            } else if (trimmed.startsWith("hw-module")) {
                input = "";
            } else if (trimmed.startsWith("diagnostic")) {
                input = "";
            } else if (trimmed.startsWith("macro name")) {
                input = "";
            } else if (trimmed.startsWith("ntp clock-period")) {
                input = "";
            } else if (trimmed.startsWith("terminal width ")) {
                input = "";
            } else if (trimmed.startsWith("Cryptochecksum:")) {
                input = "";
            }

            //
            // Transform lines[n] -> XXX
            //
            if (input != null && !input.equals(lines[n])) {
                if (input.isEmpty())
                    traceVerbose(worker, "transformed <= stripped '"+trimmed+"'");
                else
                    traceVerbose(worker, "transformed <= '"+trimmed+"' to '"+input.trim()+"'");
                lines[n] = input;
            }

        } // for (line-by-line)

        //
        // LINE-BY-LINE GET (append: may add, delete or reorder lines)
        //
        logVerbose(worker, "TRANSFORMING - appending config");
        StringBuilder buffer = new StringBuilder();
        for (n = 0; n < lines.length; n++) {
            String input = null;
            String trimmed = lines[n].trim();
            boolean split = false;
            if (trimmed.isEmpty())
                continue;

            // Update toptag
            if (isTopExit(lines[n])) {
                toptag = "";
            } else if (Character.isLetter(lines[n].charAt(0))) {
                toptag = trimmed;
            }

            //
            // tcp-map / tcp-options range
            //
            if (toptag.startsWith("tcp-map ") && trimmed.startsWith("tcp-options range ")) {
                Pattern p = Pattern.compile("tcp-options range (\\d+) (\\d+) (\\S+)");
                Matcher m = p.matcher(trimmed);
                if (m.find()) {
                    int start = Integer.parseInt(m.group(1));
                    int end = Integer.parseInt(m.group(2));
                    if (start == end) {
                        input = " tcp-options range "+start+" "+m.group(3);
                    } else {
                        split = true;
                        for (i = start; i <= end; i++) {
                            buffer.append(" tcp-options range "+i+" "+m.group(3)+"\n");
                        }
                    }
                }
            }

            //
            // banner exec|login|motd
            //
            else if (toptag.startsWith("banner ")
                     && (match = getMatch(trimmed, "banner (\\S+)")) != null) {
                String banner = null;
                for (; n < lines.length; n++) {
                    if (!lines[n].startsWith("banner "+match)) {
                        n--; // look at this line again in outer for-loop
                        break;
                    }
                    String text = lines[n].replace("\r","").replace("banner "+match+" ", "");
                    if (banner == null)
                        banner = text;
                    else
                        banner += ("\r\n" + text); // Because last CRLF is stripped
                }
                buffer.append("banner "+match+" "+stringQuote(banner)+"\n");
                traceVerbose(worker, "transformed <= quoted 'banner "+match+"' text");
                continue;
            }

            //
            // Append line or log lines
            //
            if (split) {
                traceVerbose(worker, "transformed <= split '"+trimmed+"'");
            } else if (input != null && !input.equals(lines[n])) {
                if (input.isEmpty()) {
                    traceVerbose(worker, "transformed <= stripped '"+trimmed+"'");
                } else {
                    traceVerbose(worker, "transformed <= '"+trimmed+"' to '"+input.trim()+"'");
                    buffer.append(input+"\n");
                }
            } else {
                buffer.append(lines[n]+"\n");
            }
        }

        // Add hidden DefaultDNS server-group
        traceVerbose(worker, "transformed <= injected 'dns server-group DefaultDNS'");
        buffer.append("dns server-group DefaultDNS\n");

        // Restore single buffer
        res = "\n" + buffer.toString() + "\n";

        //
        // Look for crypto certificate(s) and quote contents
        //
        logVerbose(worker, "TRANSFORMING - quoting certificates");
        i = res.indexOf("\n certificate ");
        while (i >= 0) {
            int start = res.indexOf("\n", i+1);
            traceVerbose(worker, "transformed <= quoted cert '"+res.substring(i,start).trim()+"'");
            if (start > 0) {
                int end = res.indexOf("quit", start);
                if (end > 0) {
                    String cert = res.substring(start+1, end);
                    res = res.substring(0,start+1) + stringQuote(cert)
                        + "\n" + res.substring(end);
                }
            }
            i = res.indexOf("\n certificate ", i+14);
        }

        // Respond with updated show buffer
        return res;
    }

    private String transformConfig(NedWorker worker, boolean convert, String res)
        throws Exception {

        String lines[] = res.split("\n");
        StringBuffer data = new StringBuffer();
        StringBuffer sb = new StringBuffer();
        String context = null;
        for (int n = 0; n < lines.length; n++) {
            if (lines[n].startsWith("changeto context ")) {
                res = sb.toString();
                if (!res.trim().isEmpty()) {
                    res = transformContextConfig(worker, context, convert, res);
                    data.append(res);
                    sb = new StringBuffer();
                }
                context = getMatch(lines[n], "changeto context (\\S+)");
            }
            sb.append(lines[n]+"\n");
        }

        res = sb.toString();
        if (!res.trim().isEmpty()) {
            res = transformContextConfig(worker, context, convert, res);
            data.append(res);
        }

        res = data.toString();
        traceVerbose(worker, "\nSHOW_AFTER:\n"+res);
        return res;
    }

    public void show(NedWorker worker, String toptag)
        throws Exception {

        if (trace)
            session.setTracer(worker);

        if (!toptag.equals("interface")) {
            // only respond to first toptag since the ASA
            // cannot show different parts of the config.
            worker.showCliResponse("");
            return;
        }

        logInfo(worker, "SHOW READING CONFIG");
        lastGetConfig = getConfig(worker);
        String res = transformConfig(worker, true, lastGetConfig);
        lastTransformedConfig = null;
        logInfo(worker, "DONE SHOW");

        if (turboParserEnable) {
            traceInfo(worker, "Parsing config using turbo-mode");
            if (parseAndLoadXMLConfigStream(mm, worker, schema, res)) {
                // Turbo-parser present/succeeded, clear config to bypass CLI
                res = "";
            }
        }

        worker.showCliResponse(res);
    }

    public boolean isConnection(String device_id,
                                InetAddress ip,
                                int port,
                                String proto,  // ssh or telnet
                                String ruser,
                                String pass,
                                String secpass,
                                String keydir,
                                boolean trace,
                                int connectTimeout, // msec
                                int readTimeout,
                                int writeTimeout) {
        return ((this.device_id.equals(device_id)) &&
                (this.ip.equals(ip)) &&
                (this.port == port) &&
                (this.proto.equals(proto)) &&
                (this.ruser.equals(ruser)) &&
                (this.pass.equals(pass)) &&
                (this.secpass.equals(secpass)) &&
                (this.trace == trace) &&
                (this.connectTimeout == connectTimeout) &&
                (this.readTimeout == readTimeout) &&
                (this.writeTimeout == writeTimeout));
    }

    private String GetValue(String buf, String name) {
        int i, s0, s1;
        if ((i = buf.indexOf(name)) < 0)
            return "";
        if ((s0 = buf.indexOf(" ", i)) < 0)
            return "";
        if ((s1 = buf.indexOf(" ", s0+1)) < 0)
            return buf.substring(s0+1, buf.length());
        else
            return buf.substring(s0+1, s1);
    }

    private String ConfObjectToIfName(ConfObject kp) {
        String name = kp.toString();
        name = name.replaceAll("\\{", "");
        name = name.replaceAll("\\}", "");
        name = name.replaceAll(" ", "");
        return name;
    }

    public void showStats(NedWorker worker, int th, ConfPath path)
        throws Exception {

        mm.attach(th, -1, 1);

        traceVerbose(worker, "showStats() "+path);

        ArrayList<NedTTL> ttls = new ArrayList<NedTTL>();
        ConfObject[] kp = path.getKP();

        // show devices device <dev> live-status vpn-sessiondb
        if (path.toString().indexOf("vpn-sessiondb") > 0) {
            ConfKey x = (ConfKey) kp[1];
            ConfObject[] kos = x.elements();

            // Send show single interface command to device

            getList(worker, th, ttls);
        }

        // show devices device <dev> live-status interface
        else {
            String root =
                "/ncs:devices/device{"+device_id+"}"+
                "/live-status/asa-stats:inside-interface";

            session.println("show interface inside detail");
            String res = session.expect("\\A.*#", worker);

            String[] lines = res.split("\r|\n");
            for (int i = 0; i < lines.length; i++) {
                int j = lines[i].indexOf("line protocol is");
                if (j >= 0) {
                    if (lines[i].substring(j+17).indexOf("up") == 0) {
                        mm.setElem(th, "up", root+"/line-protocol-status");
                        ttls.add(new NedTTL(new ConfPath(
                               root+"/line-protocol-status"), 5));
                    }
                    else {
                        mm.setElem(th, "down", root+"/line-protocol-status");
                        ttls.add(new NedTTL(new ConfPath(
                               root+"/line-protocol-status"), 5));
                    }
                }
                else if (lines[i].indexOf("packets input,") >= 0) {
                    String[] tokens = lines[i].trim().split("( |\t)+");
                    mm.setElem(th, tokens[3].trim(), root+"/input-bytes");
                    ttls.add(new NedTTL(new ConfPath(root+"/input-bytes"), 5));
                }
                else if (lines[i].indexOf("packets output,") >= 0) {
                    String[] tokens = lines[i].trim().split("( |\t)+");
                    mm.setElem(th, tokens[3].trim(), root+"/output-bytes");
                    ttls.add(new NedTTL(new ConfPath(root+"/output-bytes"), 5));
                }
                else if (lines[i].indexOf("5 minute input rate") >= 0) {
                    String[] tokens = lines[i].trim().split("( |\t)+");
                    mm.setElem(th, tokens[6].trim(), root+"/input-rate");
                    ttls.add(new NedTTL(new ConfPath(root+"/input-rate"), 5));
                }
                else if (lines[i].indexOf("5 minute output rate") >= 0) {
                    String[] tokens = lines[i].trim().split("( |\t)+");
                    mm.setElem(th, tokens[6].trim(), root+"/output-rate");
                    ttls.add(new NedTTL(new ConfPath(root+"/output-rate"), 5));
                }
            }

            root =
                "/ncs:devices/device{"+device_id+"}"+
                "/live-status/asa-stats:ssl";

            session.println("show ssl mib");
            res = session.expect("\\A.*#", worker);

            lines = res.split("\r|\n");
            for (int i = 0; i < lines.length; i++) {
                if (lines[i].indexOf("alSslStatsPostDecryptOctets") >= 0) {
                    String[] tokens = lines[i].trim().split("( |\t)+");
                    mm.setElem(th, tokens[1].trim(),
                              root+"/post-decrypt-octets");
                    ttls.add(
                        new NedTTL(
                            new ConfPath(root+"/post-decrypt-octets"), 5));
                }
                else if (lines[i].indexOf("alSslStatsPostEncryptOctets") >= 0) {
                    String[] tokens = lines[i].trim().split("( |\t)+");
                    mm.setElem(th, tokens[1].trim(),
                              root+"/post-encrypt-octets");
                    ttls.add(
                        new NedTTL(
                            new ConfPath(root+"/post-encrypt-octets"), 5));
                }
            }
        }

        worker.showStatsResponse(ttls.toArray(new NedTTL[ttls.size()]));

        mm.detach(th);
    }

    public void showStatsList(NedWorker worker, int th, ConfPath path)
        throws Exception {

        traceVerbose(worker, "showStatsList() "+path);

        ArrayList<NedTTL> ttls = new ArrayList<NedTTL>();
        mm.attach(th, -1, 1);
        getList(worker, th, ttls);
        worker.showStatsListResponse(10, ttls.toArray(new NedTTL[ttls.size()]));
        mm.detach(th);
    }

    private void getList(NedWorker worker, int th, ArrayList<NedTTL> ttls)
        throws NedException, IOException, ConfException,
               SSHSessionException {

        String root =
            "/ncs:devices/device{"+device_id+"}"+
            "/live-status/asa-stats:vpn-sessiondb/anyconnect";

        mm.delete(th, root);

        session.println("show vpn-sessiondb anyconnect");
        String res = session.expect("\\A.*#", worker);

        // Parse single interface
        String[] lines = res.split("\r*\n\r*");
        String epath = null;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].indexOf("Username") == 0) {
              if (lines[i].indexOf("Index") >= 0) {
                String[] tokens = lines[i].split("(:| )+");
                epath = root+"{"+tokens[1]+" "+tokens[3]+"}";
                mm.create(th, epath);
              }
              else if (i+1 < lines.length &&
                       lines[i+1].indexOf("Index") >= 0) {
                String[] tokens1 = lines[i].split("(:| )+");
                String[] tokens2 = lines[i+1].split("(:| )+");
                epath = root+"{"+tokens1[1]+" "+tokens2[1]+"}";
                mm.create(th, epath);
              }
              else {
                  logInfo(worker, "getList() - no Index found:" + lines[i]+"\n"+
                          lines[i+1]);
              }
            }
            else if (epath == null)
                continue;
            else if (lines[i].indexOf("Login Time") == 0) {
                String[] tokens = lines[i].split("( |\t)+");
                mm.setElem(th, tokens[3].trim(), epath+"/login-time");
                ttls.add(new NedTTL(new ConfPath(epath+"/login-time"), 5));
            }
            else if (lines[i].indexOf("Duration") == 0) {
                String[] tokens = lines[i].split("( |\t)+");
                mm.setElem(th, tokens[2].trim(), epath+"/duration");
                ttls.add(new NedTTL(new ConfPath(epath+"/duration"), 5));
            }
            else if (lines[i].indexOf("Bytes Tx") == 0) {
                String[] tokens = lines[i].split("(:| |\t)+");
                mm.setElem(th, tokens[2].trim(), epath+"/tx-bytes");
                mm.setElem(th, tokens[5].trim(), epath+"/rx-bytes");
                ttls.add(new NedTTL(new ConfPath(epath+"/tx-bytes"), 5));
                ttls.add(new NedTTL(new ConfPath(epath+"/rx-bytes"), 5));
            }
            else if (lines[i].indexOf("Inactivity") == 0) {
                String[] tokens = lines[i].split(" : ");
                String inactivity = tokens[1].trim();
                mm.setElem(th, inactivity, epath+"/inactivity");

                if (inactivity.equals("0h:00m:00s"))
                    mm.setElem(th, "active", epath+"/status");
                else
                    mm.setElem(th, "inactive", epath+"/status");

                ttls.add(new NedTTL(new ConfPath(epath+"/inactivity"), 5));
                ttls.add(new NedTTL(new ConfPath(epath+"/status"), 5));
            }
        }
    }

    public NedCliBase newConnection(String device_id,
                                InetAddress ip,
                                int port,
                                String proto,  // ssh or telnet
                                String ruser,
                                String pass,
                                String secpass,
                                String publicKeyDir,
                                boolean trace,
                                int connectTimeout, // msec
                                int readTimeout,    // msec
                                int writeTimeout,   // msecs
                                NedMux mux,
                                NedWorker worker) {
        return new ASANedCli(device_id,
                               ip, port, proto, ruser, pass, secpass, trace,
                               connectTimeout, readTimeout, writeTimeout,
                               mux, worker);
    }

    private String commandWash(String cmd) {
        byte[] bytes = cmd.getBytes();
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < cmd.length(); ++i) {
            if (bytes[i] == 9)
                continue;
            if (bytes[i] == -61)
                continue;
            result.append(cmd.charAt(i));
        }
        return result.toString();
    }

    @Override
    public void command(NedWorker worker, String cmdName, ConfXMLParam[] p)
        throws Exception {
        String cmd  = cmdName;
        String reply = "";
        NedExpectResult res;
        boolean configMode = false;
        boolean rebooting = false;
        boolean noprompts = false;
        String context = null;
        String inputString = null;
        String promptv[] = null;
        int promptc = 0;
        int i;

        if (trace)
            session.setTracer(worker);

        // Add arguments, save optional context
        for (i = 0; i < p.length; ++i) {
            ConfObject val = p[i].getValue();
            if (val != null) {
                if (context == null && p[i].getTag().equals("context"))
                    context = val.toString();
                else if (p[i].getTag().equals("input-string"))
                    inputString = stringDequote(val.toString());
                else {
                    cmd = cmd + " " + val.toString();
                }
            }
        }

        // Output to debug trace
        traceVerbose(worker, "command="+cmd);
        if (context != null)
            traceVerbose(worker, "context="+context);
        if (inputString != null)
            traceVerbose(worker, "input-string:\n"+inputString);

        // Strip special character
        cmd = commandWash(cmd);

        // Enable noprompts or extract answer(s) to prompting questions
        if (cmd.matches("^.*\\s*\\|\\s*noprompts\\s*$")) {
            noprompts = true;
            cmd = cmd.substring(0,cmd.lastIndexOf("|")).trim();
        } else {
            Pattern pattern = Pattern.compile("(.*)\\|\\s*prompts\\s+(.*)");
            Matcher matcher = pattern.matcher(cmd);
            if (matcher.find()) {
                cmd = matcher.group(1).trim();
                traceVerbose(worker, "command = '"+cmd+"'");
                promptv = matcher.group(2).trim().split(" +");
                for (i = 0; i < promptv.length; i++)
                    traceVerbose(worker, "promptv["+i+"] = '"+promptv[i]+"'");
            }
        }

        // Check mode + optional context by sending new line
        boolean wasInConfig = true;
        Pattern[] cprompt = new Pattern[] {
            Pattern.compile("\\A\\S*\\(config\\)#"),
            Pattern.compile("\\A.*\\(.*\\)#"),
            Pattern.compile("\\A\\S*#")
        };
        session.print("\n");
        res = session.expect(cprompt, worker);
        if (res.getHit() == 2)
            wasInConfig = false;

        // Multi-context admin and specified context -> change to it
        if (haveContext && contextName == null && context != null)  {
            changeto_context(worker, context);
        }

        // Config mode exec command
        if (cmd.startsWith("exec ")) {
            configMode = true;
            if (isNetsim()) {
                worker.error(NedCmd.CMD, "'"+cmd+"' not supported on NETSIM, use a real device");
                return;
            }
            if (cmd.startsWith("exec "))
                cmd = cmd.substring(5);
            if (!wasInConfig)
                enterConfig(worker, NedCmd.CMD, false);
        }
        // live-status exec command
        else if (cmd.startsWith("any ")) {
            cmd = cmd.substring(4);
        }

        // patch for service node bug, quoting command
        if (cmd.charAt(cmd.length() - 1) == '"') {
            traceInfo(worker, "PATCH: removing quotes inserted by bug in NCS");
            cmd = cmd.substring(0, cmd.length() -1 );
            cmd = cmd.replaceFirst("\"", "");
        }

        // show fixed-config [internal command]
        if (cmd.equals("show fixed-config")) {
            reply = "\nNext sync-from will show fixed config.\n";
            traceInfo(worker, reply);
            showFixed = true;
        }

        // show outformat raw [internal command]
        else if (cmd.equals("show outformat raw")) {
            reply = "\nNext dry-run will show raw (unmodified) format.\n";
            traceInfo(worker, reply);
            showRaw = true;
        }

        // DEVICE command
        else {
            // Send command or help (ending with ?) to device
            boolean help = cmd.charAt(cmd.length() - 1) == '?';
            String helpPrompt;
            if (help) {
                traceVerbose(worker, "Sending"+(configMode ? " config" : "" )+" help: '"+cmd+"'");
                session.print(cmd);
                helpPrompt = "\\A[^\\# ]+#[ ]*" + cmd.substring(0, cmd.length()-1) + "[ ]*";
                traceVerbose(worker, "help-prompt = '" + helpPrompt + "'");
                noprompts = true;
            }
            else {
                traceVerbose(worker, "Sending"+(configMode ? " config" : "" )+" command: '"+cmd+"'");
                session.print(cmd+"\n");
                helpPrompt = privexec_prompt;
            }

            // Wait for command echo
            traceVerbose(worker, "Waiting for command echo");
            session.expect(new String[] { Pattern.quote(cmd) }, worker);

            // Prompt patterns
            Pattern[] cmdPrompt = new Pattern[] {
                // 0 - Prompt patterns:
                Pattern.compile("\\A.*\\(.*\\)#"),
                Pattern.compile("\\A\\S*#"),
                Pattern.compile("\\A\\S.*#"),
                Pattern.compile(helpPrompt),
                // 4 Ignore patterns:
                Pattern.compile("\\[OK\\]"),
                Pattern.compile("\\[Done\\]"),
                Pattern.compile("timeout is \\d+ seconds:"),  // ping
                Pattern.compile("Key data:"), // crypto key export rsa
                Pattern.compile("Cryptochecksum:"), // any context FOO write memory
                Pattern.compile("Trustpool import:"), // crypto ca trustpool import
                // 10 Question patterns:
                Pattern.compile("\\S+:\\s*$"),
                Pattern.compile("\\S+\\][\\?]?\\s*$")
            };

            // Wait for prompt, answer prompting questions with | prompts info
            while (true) {
                traceVerbose(worker, "Waiting for command prompt");
                res = session.expect(cmdPrompt, true, connectTimeout, worker);
                String output = res.getText();
                String answer = null;
                reply += output;

                // Prompt patterns:
                if (res.getHit() < 4) {
                    traceVerbose(worker, "Got prompt '"+output+"'");
                    if (help) {
                        sendBackspaces(worker, cmd);
                    }
                    if (promptv != null && promptc < promptv.length) {
                        reply += "\n(unused prompts:";
                        for (i = promptc; i < promptv.length; i++)
                            reply += " "+promptv[i];
                        reply += ")";
                    }
                    break;
                }

                // Ignore patterns:
                else if (res.getHit() < 10 // Ignore patterns
                           || noprompts // '| noprompts' option
                           || cmd.indexOf(" /noconfirm") > 0
                           || cmd.startsWith("show ")
                           || cmd.startsWith("packet-tracer ")) {
                    traceVerbose(worker, "Ignoring output '"+output+"'");
                    continue;
                }

                traceVerbose(worker, "Got question '"+output+"'");

                // Send input-string
                if (findString("End with the word .* on a line by itself", output) >= 0) {
                    if (inputString == null) {
                        reply = "\nMissing input-string option in:\n+++" + reply;
                        session.print("quit\n");
                        break;
                    }
                    traceInfo(worker, "Sending input-string:");
                    session.print(inputString+"\nquit\n");
                    continue;
                }

                // Get answer from command line, i.e. '| prompts <val>'
                if (promptv != null && promptc < promptv.length) {
                    answer = promptv[promptc++];
                }

                // Look for answer in auto-prompts ned-settings
                else {
                    for (int n = autoPrompts.size()-1; n >= 0; n--) {
                        String entry[] = autoPrompts.get(n);
                        if (findString(entry[1], output) >= 0) {
                            traceInfo(worker, "Matched auto-prompt["+entry[0]+"]");
                            answer = entry[2];
                            reply += "(auto-prompt "+answer+") -> ";
                            break;
                        }
                    }
                }

                // Send answer to device. Check if rebooting
                if (answer != null) {
                    traceInfo(worker, "Sending: "+answer);
                    if (answer.equals("ENTER"))
                        session.print("\n");
                    else if (answer.equals("IGNORE"))
                        continue; // used to avoid blocked on bad prompts
                    else if (answer.length() == 1)
                        session.print(answer);
                    else
                        session.print(answer+"\n");
                    if (cmd.startsWith("reload")
                        && output.indexOf("Proceed with reload") >= 0
                        && answer.charAt(0) != 'n') {
                        rebooting = true;
                        break;
                    }
                    continue;
                }

                // Missing answer to a question prompt:
                reply = "\nMissing answer to a device question:\n+++" + reply;
                reply +="\n+++\nSet auto-prompts ned-setting or add '| prompts <answer>', e.g.:\n";
                if (configMode)
                    reply += "exec \"crypto key zeroize rsa label MYKEY | prompts yes\"";
                else
                    reply += "devices device <devname> live-status exec any \"reload | prompts y\"";
                reply += "\nNote: Single letter is sent without LF. Use 'ENTER' for LF only.";
                reply += "\n      Add '| noprompts' in order to ignore all prompts.";
                exitPrompting(worker);
                if (configMode && !wasInConfig)
                    exitConfig(worker);
                worker.error(NedCmd.CMD, reply);
                return;
            }
        }

        // Report device output 'reply'
        if (configMode) {
            if (!wasInConfig)
                exitConfig(worker);
            worker.commandResponse(new ConfXMLParam[] {
                    new ConfXMLParamValue("asa", "result",
                                          new ConfBuf(reply))});
        } else {
            worker.commandResponse(new ConfXMLParam[] {
                    new ConfXMLParamValue("asa-stats", "result",
                                          new ConfBuf(reply))});
        }

        // Rebooting
        if (rebooting) {
            logInfo(worker, "Rebooting device...");
            logInfo(worker, "Sleeping 30 seconds to avoid premature reconnect");
            worker.setTimeout(10*60*1000);
            try {
                Thread.sleep(30*1000);
                logInfo(worker, "Woke up from 30 seconds sleep");
            } catch (InterruptedException e) {
                logInfo(worker, "reboot sleep interrupted");
            }
        }
    }

}
