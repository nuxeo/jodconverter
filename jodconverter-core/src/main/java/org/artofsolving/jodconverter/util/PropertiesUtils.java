/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.artofsolving.jodconverter.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Properties;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.artofsolving.jodconverter.office.OfficeConnectionProtocol;
import org.artofsolving.jodconverter.office.OfficeUtils;
import org.artofsolving.jodconverter.process.MacProcessManager;
import org.artofsolving.jodconverter.process.ProcessManager;
import org.artofsolving.jodconverter.process.PureJavaProcessManager;
import org.artofsolving.jodconverter.process.UnixProcessManager;
import org.artofsolving.jodconverter.process.WindowsProcessManager;

/**
 *
 * @author Administrator
 */
public class PropertiesUtils {

    private static final Logger LOGGER = Logger.getLogger(PropertiesUtils.class.getName());

    private final static String CONF_OFFICE_HOME = "office.home";
    private final static String CONF_PROCESS_MANAGER = "process.manager";
    private final static String CONF_PROCESS_PREKILL = "process.prekill";
    private final static String CONF_CONNECT_PORT = "connect.port";
    private final static String CONF_CONNECT_PORTS = "connect.ports";
    private final static String CONF_CONNECT_PROTOCOL = "connect.protocol";
    private final static String CONF_CONNECT_PIPE_NAME = "connect.pipe.name";
    private final static String CONF_CONNECT_PIPE_NAMES = "connect.pipe.names";
    private final static String CONF_CONNECT_ONSTART = "connect.onstart";

    private final Properties properties;

    public PropertiesUtils() {
        Properties defaultProperties = new Properties();
        InputStream is = getClass().getResourceAsStream("/jodconverter.properties");
        if (is != null) {
            try {
                defaultProperties.load(is);
            } catch (IOException ex) {
                LOGGER.warning("Unable to load configure file jodconverter.properties");
            }
            try {
                is.close();
            } catch (IOException ignore) {
            }
        }
        properties = new Properties(defaultProperties);
    }

    public void load(Properties properties, String prefix) {
        Enumeration<?> names = properties.propertyNames();
        while (names.hasMoreElements()) {
            Object oKey = names.nextElement();
            String key = oKey.toString();
            if (prefix == null || key.startsWith(prefix)) {
                String newkey = prefix != null ? key.substring(prefix.length()) : key;
                this.properties.setProperty(newkey, properties.getProperty(key));
            }
        }
    }

    public Properties getProperties() {
        return properties;
    }

    public File getOfficeHome() {
        String officeHome = properties.getProperty(CONF_OFFICE_HOME);
        if (officeHome != null) {
            return new File(officeHome);
        } else {
            return OfficeUtils.getDefaultOfficeHome();
        }
    }

    private int _getPort(int defaultPort) {
        String pPort = properties.getProperty(CONF_CONNECT_PORT);
        Integer portNumber = null;
        if (pPort != null) {
            try {
                portNumber = Integer.parseInt(pPort);
            } catch (NumberFormatException e) {
                LOGGER.log(Level.WARNING, "Invalid port number \"{0}\".", pPort);
                portNumber = defaultPort;
            }
        }
        if (portNumber == null) {
            portNumber = defaultPort;
        }
        return portNumber;
    }

    private int[] _getPorts(int[] defaultPorts) {
        int[] portNumbers = null;
        String pPorts = properties.getProperty(CONF_CONNECT_PORTS);
        if (pPorts != null) {
            String[] pArrPorts = pPorts.split(",");
            portNumbers = new int[pArrPorts.length];
            try {
                for (int i = 0; i < portNumbers.length; i++) {
                    portNumbers[i] = Integer.parseInt(pArrPorts[i]);
                }
            } catch (NumberFormatException e) {
                LOGGER.log(Level.WARNING, "Invalid port numbers \"{0}\".", pPorts);
                portNumbers = defaultPorts;
            }
        }
        if (portNumbers == null) {
            portNumbers = defaultPorts;
        }
        return portNumbers;
    }

    public int getPort() {
        int portNumber = _getPort(-1);
        if (portNumber == -1) {
            int[] portNumbers = _getPorts(null);
            if (portNumbers != null && portNumbers.length > 0) {
                portNumber = portNumbers[0];
            } else {
                portNumber = 2002;
            }
        }
        return portNumber;
    }

    public int[] getPorts() {
        int[] portNumbers = _getPorts(null);
        if (portNumbers == null || portNumbers.length == 0) {
            portNumbers = new int[]{_getPort(2002)};
        }
        return portNumbers;
    }

    private String _getPipeName(String defaultName) {
        return properties.getProperty(CONF_CONNECT_PIPE_NAME, defaultName);
    }

    private String[] _getPipeNames(String[] defaultNames) {
        String[] pipeNames = null;
        String pPipeNames = properties.getProperty(CONF_CONNECT_PIPE_NAMES);
        if (pPipeNames != null) {
            pipeNames = pPipeNames.split(",");
        }
        if (pipeNames == null) {
            pipeNames = defaultNames;
        }
        return pipeNames;
    }

    public String getPipeName() {
        String pipeName = _getPipeName(null);
        if (pipeName == null) {
            String[] pipeNames = _getPipeNames(null);
            if (pipeNames != null && pipeNames.length > 0) {
                pipeName = pipeNames[0];
            }
        }
        if (pipeName == null) {
            pipeName = "office";
        }
        return pipeName;
    }

    public String[] getPipeNames() {
        String[] pipeNames = _getPipeNames(null);
        if (pipeNames == null || pipeNames.length == 0) {
            pipeNames = new String[]{_getPipeName("office")};
        }
        return pipeNames;
    }

    public OfficeConnectionProtocol getProtocol() {
        OfficeConnectionProtocol connectionProtocol = null;
        String pProtocol = properties.getProperty(CONF_CONNECT_PROTOCOL);
        if (pProtocol != null) {
            OfficeConnectionProtocol[] protocols = OfficeConnectionProtocol.values();
            for (OfficeConnectionProtocol protocol : protocols) {
                if (protocol.toString().equalsIgnoreCase(pProtocol)) {
                    connectionProtocol = protocol;
                    break;
                }
            }
            if (connectionProtocol == null) {
                LOGGER.log(Level.WARNING, "Invalid protocol type \"{0}\", use socket instead.", pProtocol);
                connectionProtocol = OfficeConnectionProtocol.SOCKET;
            }
        } else {
            connectionProtocol = OfficeConnectionProtocol.SOCKET;
        }
        if (connectionProtocol == OfficeConnectionProtocol.PIPE) {
            try {
                Runtime.getRuntime().load(OfficeUtils.getJPipePath(getOfficeHome()));
            } catch (UnsatisfiedLinkError e) {
                LOGGER.log(Level.WARNING, e, new Supplier<String>() {

                    public String get() {
                        return "Pipe is not avaialbe, use socket instead.";
                    }
                });
                connectionProtocol = OfficeConnectionProtocol.SOCKET;
            }
        }
        return connectionProtocol;
    }

    public ProcessManager getProcessManager() {
        String pProcessManager = properties.getProperty(CONF_PROCESS_MANAGER);
        ProcessManager processManager = null;
        if (pProcessManager != null) {
            try {
                Class<?> managerClass = Class.forName(pProcessManager);
                Object managerInstance = managerClass.newInstance();
                if (managerInstance instanceof ProcessManager) {
                    processManager = (ProcessManager) managerInstance;
                }
            } catch (ClassNotFoundException ex) {
                LOGGER.log(Level.WARNING, "Invalid process manager impl \"{0}\".", pProcessManager);
            } catch (InstantiationException ex) {
                LOGGER.log(Level.WARNING, "Unable to create the instance of process manager impl \"{0}\".", pProcessManager);
            } catch (IllegalAccessException ex) {
                LOGGER.log(Level.WARNING, "No access to create the instance of process manager impl \"{0}\".", pProcessManager);
            }
        }
        if (processManager == null) {
            if (PlatformUtils.isWindows()) {
                WindowsProcessManager windowsProcessManager = new WindowsProcessManager();
                if (windowsProcessManager.isUsable()) {
                    processManager = windowsProcessManager;
                } else {
                    processManager = new PureJavaProcessManager();
                }
            } else if (PlatformUtils.isLinux()) {
                processManager = new UnixProcessManager();
            } else if (PlatformUtils.isMac()) {
                processManager = new MacProcessManager();
            } else {
                processManager = new PureJavaProcessManager();
            }
        }
        return processManager;
    }

    private boolean toBoolean(String value) {
        value = value.trim();
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        if ("1".equals(value)) {
            return true;
        }
        if ("0".equals(value)) {
            return false;
        }
        throw new IllegalArgumentException("Unable to convert " + value + " to a boolean type.");
    }

    public boolean isProcessPreKill() {
        String pPreKill = properties.getProperty(CONF_PROCESS_PREKILL);
        boolean preKill = true;
        if (pPreKill != null) {
            try {
                preKill = toBoolean(pPreKill);
            } catch (IllegalArgumentException e) {
                LOGGER.log(Level.WARNING, "Invalid value \"{0}\" of property {1}, use true instead.", new String[]{pPreKill, CONF_PROCESS_PREKILL});
                preKill = true;
            }
        }
        return preKill;
    }

    public boolean isConnectOnStart() {
        String pOnStart = properties.getProperty(CONF_CONNECT_ONSTART);
        boolean onStart = true;
        if (pOnStart != null) {
            try {
                onStart = toBoolean(pOnStart);
            } catch (IllegalArgumentException e) {
                LOGGER.log(Level.WARNING, "Invalid value \"{0}\" of property {1}, use true instead.", new String[]{pOnStart, CONF_CONNECT_ONSTART});
                onStart = true;
            }
        }
        return onStart;
    }
}
