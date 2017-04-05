//
// JODConverter - Java OpenDocument Converter
// Copyright 2009 Art of Solving Ltd
// Copyright 2004-2009 Mirko Nasato
//
// JODConverter is free software: you can redistribute it and/or
// modify it under the terms of the GNU Lesser General Public License
// as published by the Free Software Foundation, either version 3 of
// the License, or (at your option) any later version.
//
// JODConverter is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
// Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General
// Public License along with JODConverter.  If not, see
// <http://www.gnu.org/licenses/>.
//
package org.artofsolving.jodconverter.office;

import java.io.File;
import java.util.Properties;

import org.artofsolving.jodconverter.process.ProcessManager;

public class DefaultOfficeManagerConfiguration extends AbstractOfficeManagerConfiguration {

    private File officeHome;

    private OfficeConnectionProtocol connectionProtocol;

    private int[] portNumbers;

    private String[] pipeNames;

    private File templateProfileDir = null;

    private long taskQueueTimeout = 30000L; // 30 seconds

    private long taskExecutionTimeout = 120000L; // 2 minutes

    private int maxTasksPerProcess = 200;

    private boolean useGnuStyleLongOptions;

    private ProcessManager processManager;

    private Boolean killExistingProcess;

    public DefaultOfficeManagerConfiguration() {
        super();
    }

    @Override
    public DefaultOfficeManagerConfiguration load(Properties properties, String prefix) {
        return (DefaultOfficeManagerConfiguration) super.load(properties, prefix);
    }

    public DefaultOfficeManagerConfiguration setOfficeHome(String officeHome) throws NullPointerException,
            IllegalArgumentException {
        checkArgumentNotNull("officeHome", officeHome);
        return setOfficeHome(new File(officeHome));
    }

    public DefaultOfficeManagerConfiguration setOfficeHome(File officeHome) throws NullPointerException,
            IllegalArgumentException {
        checkArgumentNotNull("officeHome", officeHome);
        checkArgument("officeHome", officeHome.isDirectory(), "must exist and be a directory");
        this.officeHome = officeHome;
        return this;
    }

    public DefaultOfficeManagerConfiguration setConnectionProtocol(OfficeConnectionProtocol connectionProtocol)
            throws NullPointerException {
        checkArgumentNotNull("connectionProtocol", connectionProtocol);
        this.connectionProtocol = connectionProtocol;
        return this;
    }

    public DefaultOfficeManagerConfiguration setKillExistingProcess(boolean killExistingProcess) {
        this.killExistingProcess = killExistingProcess;
        return this;
    }

    public DefaultOfficeManagerConfiguration setPortNumber(int portNumber) {
        portNumbers = new int[]{portNumber};
        return this;
    }

    public DefaultOfficeManagerConfiguration setPortNumbers(int... portNumbers) throws NullPointerException,
            IllegalArgumentException {
        checkArgumentNotNull("portNumbers", portNumbers);
        checkArgument("portNumbers", portNumbers.length > 0, "must not be empty");
        this.portNumbers = portNumbers;
        return this;
    }

    public DefaultOfficeManagerConfiguration setPipeName(String pipeName) throws NullPointerException {
        checkArgumentNotNull("pipeName", pipeName);
        pipeNames = new String[]{pipeName};
        return this;
    }

    public DefaultOfficeManagerConfiguration setPipeNames(String... pipeNames) throws NullPointerException,
            IllegalArgumentException {
        checkArgumentNotNull("pipeNames", pipeNames);
        checkArgument("pipeNames", pipeNames.length > 0, "must not be empty");
        this.pipeNames = pipeNames;
        return this;
    }

    public DefaultOfficeManagerConfiguration setTemplateProfileDir(File templateProfileDir)
            throws IllegalArgumentException {
        if (templateProfileDir != null) {
            checkArgument("templateProfileDir", templateProfileDir.isDirectory(), "must exist and be a directory");
        }
        this.templateProfileDir = templateProfileDir;
        return this;
    }

    public DefaultOfficeManagerConfiguration setTaskQueueTimeout(long taskQueueTimeout) {
        this.taskQueueTimeout = taskQueueTimeout;
        return this;
    }

    public DefaultOfficeManagerConfiguration setTaskExecutionTimeout(long taskExecutionTimeout) {
        this.taskExecutionTimeout = taskExecutionTimeout;
        return this;
    }

    public DefaultOfficeManagerConfiguration setMaxTasksPerProcess(int maxTasksPerProcess) {
        this.maxTasksPerProcess = maxTasksPerProcess;
        return this;
    }

    public DefaultOfficeManagerConfiguration setProcessManager(ProcessManager processManager)
            throws NullPointerException {
        checkArgumentNotNull("processManager", processManager);
        this.processManager = processManager;
        return this;
    }

    public OfficeManager buildOfficeManager() throws IllegalStateException {
        if (officeHome == null) {
            officeHome = propertiesUtils.getOfficeHome();
        }
        if (connectionProtocol == null) {
            connectionProtocol = propertiesUtils.getProtocol();
        }
        if (portNumbers == null) {
            portNumbers = propertiesUtils.getPorts();
        }
        if (pipeNames == null) {
            pipeNames = propertiesUtils.getPipeNames();
        }
        if (processManager == null) {
            processManager = propertiesUtils.getProcessManager();
        }
        if (killExistingProcess == null) {
            killExistingProcess = propertiesUtils.isProcessPreKill();
        }

        if (!officeHome.isDirectory()) {
            throw new IllegalStateException("officeHome doesn't exist or is not a directory: " + officeHome);
        } else if (!OfficeUtils.getOfficeExecutable(officeHome).isFile()) {
            throw new IllegalStateException("invalid officeHome: couldn't find "
                    + OfficeUtils.getOfficeExecutable(officeHome));
        }
        if (templateProfileDir != null && !isValidProfileDir(templateProfileDir)) {
            throw new IllegalStateException("invalid templateProfileDir: " + templateProfileDir);
        }

        String forceOptionStyle = System.getProperty("jod.office.gnustyleoptions.force");
        if (forceOptionStyle != null) {
            useGnuStyleLongOptions = Boolean.parseBoolean(forceOptionStyle);
        }

        int numInstances = connectionProtocol == OfficeConnectionProtocol.PIPE ? pipeNames.length : portNumbers.length;
        UnoUrl[] unoUrls = new UnoUrl[numInstances];
        for (int i = 0; i < numInstances; i++) {
            unoUrls[i] = (connectionProtocol == OfficeConnectionProtocol.PIPE) ? UnoUrl.pipe(pipeNames[i])
                    : UnoUrl.socket(portNumbers[i]);
        }
        return new ProcessPoolOfficeManager(officeHome, unoUrls, templateProfileDir, taskQueueTimeout,
                taskExecutionTimeout, maxTasksPerProcess, processManager, useGnuStyleLongOptions, killExistingProcess);
    }

    private void checkArgumentNotNull(String argName, Object argValue) throws NullPointerException {
        if (argValue == null) {
            throw new NullPointerException(argName + " must not be null");
        }
    }

    private void checkArgument(String argName, boolean condition, String message) throws IllegalArgumentException {
        if (!condition) {
            throw new IllegalArgumentException(argName + " " + message);
        }
    }

    private boolean isValidProfileDir(File profileDir) {
        File setupXcu = new File(profileDir, "user/registry/data/org/openoffice/Setup.xcu");
        return setupXcu.exists();
    }

    public void setUseGnuStyleLongOptions(boolean useGnuStyleLongOptions) {
        this.useGnuStyleLongOptions = useGnuStyleLongOptions;
    }

}
