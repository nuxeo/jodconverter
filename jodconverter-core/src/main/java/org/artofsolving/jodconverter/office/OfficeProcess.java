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

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.artofsolving.jodconverter.process.ProcessManager;
import org.artofsolving.jodconverter.util.PlatformUtils;

class OfficeProcess {

    private final File officeHome;

    private final UnoUrl unoUrl;

    private final File templateProfileDir;

    private final File instanceProfileDir;

    private final File fakeBundlesDir;

    private final ProcessManager processManager;
    
    private final boolean killExistingProcess;

    private Process process;

    private String pid;

    private List<String> lastCommand;

    private final Logger logger = Logger.getLogger(getClass().getName());

    protected static String COMMAND_ARG_PREFIX = "-";

    protected static OfficeVersionDescriptor versionDescriptor = null;

    public static final String STARTUP_WATCH_TIMEOUT = "jod.startup.watch.timeout";

    public static final int DEFAULT_STARTUP_WATCH_TIMEOUT = 7;

    public OfficeProcess(File officeHome, UnoUrl unoUrl,
            File templateProfileDir, ProcessManager processManager) {
        this(officeHome, unoUrl, templateProfileDir, processManager, false, true);
    }

    public OfficeProcess(File officeHome, UnoUrl unoUrl,
            File templateProfileDir, ProcessManager processManager,
            boolean useGnuStyleLongOptions, boolean killExistingProcess) {
        this.officeHome = officeHome;
        this.unoUrl = unoUrl;
        this.templateProfileDir = templateProfileDir;
        this.instanceProfileDir = getInstanceProfileDir(unoUrl);
        this.fakeBundlesDir = getFakeBundlesDir();
        this.processManager = processManager;
        if (useGnuStyleLongOptions) {
            COMMAND_ARG_PREFIX = "--";
        } else {
            COMMAND_ARG_PREFIX = "-";
        }
        this.killExistingProcess = killExistingProcess;
    }

    protected void determineOfficeVersion() throws IOException {

        File executable = OfficeUtils.getOfficeExecutable(officeHome);
        if (PlatformUtils.isWindows()) {
            versionDescriptor = OfficeVersionDescriptor.parseFromExecutableLocation(executable.getPath());
            return;
        }

        List<String> command = new ArrayList<String>();
        command.add(executable.getAbsolutePath());
        command.add("-help");
        command.add("-headless");
        command.add("-nocrashreport");
        command.add("-nofirststartwizard");
        command.add("-nolockcheck");
        command.add("-nologo");
        command.add("-norestore");
        command.add("-env:UserInstallation="
                + OfficeUtils.toUrl(instanceProfileDir));
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);
        if (PlatformUtils.isWindows()) {
            addBasisAndUrePaths(processBuilder);
        }
        Process checkProcess = processBuilder.start();
        try {
            checkProcess.waitFor();
        } catch (InterruptedException e) {
            // NOP
        }
        InputStream in = checkProcess.getInputStream();
        String versionCheckOutput = read(in);
        versionDescriptor = new OfficeVersionDescriptor(versionCheckOutput);
    }

    private String read(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        if (in.available() > 0) {
            byte[] buffer = new byte[in.available()];
            try {
                int read;
                while ((read = in.read(buffer)) != -1) {
                    sb.append(new String(buffer, 0, read));
                }
            } finally {
                in.close();
            }
        }
        return sb.toString();
    }

    public void start() throws IOException {
        if (versionDescriptor == null) {
            determineOfficeVersion();
        }
        if (versionDescriptor != null) {
            if (versionDescriptor.useGnuStyleLongOptions()) {
                COMMAND_ARG_PREFIX = "--";
            } else {
                COMMAND_ARG_PREFIX = "-";
            }
        }
        logger.fine("OfficeProcess info:" + versionDescriptor.toString());
        doStart(false);
    }

    protected void manageProcessOutputs(Process process) {
        InputStream processOut = process.getInputStream();
        InputStream processError = process.getErrorStream();
        Thread to = new Thread(new StreamGobbler(processOut));
        to.setDaemon(true);
        to.start();
        Thread te = new Thread(new StreamGobbler(processError));
        te.setDaemon(true);
        te.start();
    }

    protected int getStartupWatchTime() {
        String timeOutStr = System.getProperty(STARTUP_WATCH_TIMEOUT, null);
        if (timeOutStr != null) {
            try {
                return new Integer(timeOutStr);
            } catch (NumberFormatException e) {
                return DEFAULT_STARTUP_WATCH_TIMEOUT;
            }
        }
        return DEFAULT_STARTUP_WATCH_TIMEOUT;
    }

    protected void doStart(boolean retry) throws IOException {
        String processRegex = "soffice.*"
                + Pattern.quote(unoUrl.getAcceptString());
        String existingPid = processManager.findPid(processRegex);
        if (existingPid != null && killExistingProcess) {
            logger.warning(String.format("a process with acceptString '%s' is already running; pid %s", unoUrl.getAcceptString(), existingPid));
            processManager.kill(null, existingPid);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            existingPid = processManager.findPid(processRegex);
            
        }
        if (existingPid != null) {
            throw new IllegalStateException(
                    String.format(
                            "a process with acceptString '%s' is already running; pid %s",
                            unoUrl.getAcceptString(), existingPid));
        }

        if (!retry) {
            prepareInstanceProfileDir();
            prepareFakeBundlesDir();
        }

        List<String> command = new ArrayList<String>();
        File executable = OfficeUtils.getOfficeExecutable(officeHome);
        command.add(executable.getAbsolutePath());
        command.add(COMMAND_ARG_PREFIX + "accept=" + unoUrl.getAcceptString()
                + ";urp;");
        command.add("-env:UserInstallation="
                + OfficeUtils.toUrl(instanceProfileDir));
        if (!PlatformUtils.isWindows()) {
            command.add("-env:BUNDLED_EXTENSIONS="
                    + OfficeUtils.toUrl(fakeBundlesDir));
        }
        command.add(COMMAND_ARG_PREFIX + "headless");
        command.add(COMMAND_ARG_PREFIX + "nocrashreport");
        command.add(COMMAND_ARG_PREFIX + "nodefault");
        command.add(COMMAND_ARG_PREFIX + "nofirststartwizard");
        command.add(COMMAND_ARG_PREFIX + "nolockcheck");
        command.add(COMMAND_ARG_PREFIX + "nologo");
        command.add(COMMAND_ARG_PREFIX + "norestore");
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);
        lastCommand = command;
        if (PlatformUtils.isWindows()) {
            addBasisAndUrePaths(processBuilder);
        }
        logger.info(String.format(
                "starting process with acceptString '%s' and profileDir '%s'",
                unoUrl, instanceProfileDir));
        process = processBuilder.start();

        int exitValue = 0;
        boolean exited = false;
        for (int i = 0; i < getStartupWatchTime() * 2; i++) {
            try {
                // wait for process to start
                Thread.sleep(500);
            } catch (Exception e) {
            }
            try {
                exitValue = process.exitValue();
                // process is already dead, no need to wait longer ...
                exited = true;
                break;
            } catch (IllegalThreadStateException e) {
                // process is still up
            }
        }

        if (exited) {
            if (exitValue == 81) {
                logger.warning("Restarting OOo after code 81 ...");
                process = processBuilder.start();
                try {
                    // wait for process to start
                    Thread.sleep(1000);
                } catch (Exception e) {
                }
            } else {
                logger.warning("Process exited with code " + exitValue);
            }
        }

        manageProcessOutputs(process);

        if (processManager.canFindPid()) {
            pid = processManager.findPid(processRegex);
            if (pid == null) {
                throw new IllegalStateException(
                        "started process, but can not find the pid, process is probably dead");
            } else {
                logger.info("started process : pid = " + pid);
            }
        } else {
            logger.info("process started with PureJavaProcessManager - cannot check for pid");
        }
    }

    private File getInstanceProfileDir(UnoUrl unoUrl) {
        String dirName = ".jodconverter_"
                + unoUrl.getAcceptString().replace(',', '_').replace('=', '-');
        dirName = dirName + "_" + Thread.currentThread().getId();
        return new File(System.getProperty("java.io.tmpdir"), dirName);
    }

    private void prepareInstanceProfileDir() throws OfficeException {
        if (instanceProfileDir.exists()) {
            logger.warning(String.format(
                    "profile dir '%s' already exists; deleting",
                    instanceProfileDir));
            deleteProfileDir();
        }
        if (templateProfileDir != null) {
            try {
                FileUtils.copyDirectory(templateProfileDir, instanceProfileDir);
            } catch (IOException ioException) {
                throw new OfficeException("failed to create profileDir",
                        ioException);
            }
        }
    }

    public void deleteProfileDir() {
        if (instanceProfileDir != null) {
            try {
                FileUtils.deleteDirectory(instanceProfileDir);
            } catch (IOException ioException) {
                logger.warning(ioException.getMessage());
            }
        }
    }

    private File getFakeBundlesDir() {
        if (PlatformUtils.isWindows()) {
            return null;
        }
        String dirName = ".jodconverter_bundlesdir";
        dirName = dirName + "_" + Thread.currentThread().getId();
        return new File(System.getProperty("java.io.tmpdir"), dirName);
    }

    private void prepareFakeBundlesDir() throws OfficeException {
        if (fakeBundlesDir != null) {
            if (fakeBundlesDir.exists()) {
                deleteFakeBundlesDir();
            }
            fakeBundlesDir.mkdirs();
        }
    }
    
    public void deleteFakeBundlesDir() {
        if (fakeBundlesDir != null) {
            try {
                FileUtils.deleteDirectory(fakeBundlesDir);
            } catch (IOException ioException) {
                logger.warning(ioException.getMessage());
            }
        }
    }

    private void addBasisAndUrePaths(ProcessBuilder processBuilder)
            throws IOException {
        File ureBin = null;
        File basisProgram = null;
        // see
        // http://wiki.services.openoffice.org/wiki/ODF_Toolkit/Efforts/Three-Layer_OOo
        File basisLink = new File(officeHome, "basis-link");
        if (!basisLink.isFile()) {
            // check the case with LibreOffice 3.5+ home
            File ureLink = new File(officeHome, "ure-link");
            if (!ureLink.isFile()) {
                logger.fine("no %OFFICE_HOME%/basis-link found; assuming it's OOo 2.x and we don't need to append URE and Basic paths");
                return;
            }
            ureBin = new File(new File(officeHome, FileUtils.readFileToString(
                    ureLink).trim()), "bin");
        } else {
            String basisLinkText = FileUtils.readFileToString(basisLink).trim();
            File basisHome = new File(officeHome, basisLinkText);
            basisProgram = new File(basisHome, "program");
            File ureLink = new File(basisHome, "ure-link");
            String ureLinkText = FileUtils.readFileToString(ureLink).trim();
            File ureHome = new File(basisHome, ureLinkText);
            ureBin = new File(ureHome, "bin");
        }
        Map<String, String> environment = processBuilder.environment();
        // Windows environment variables are case insensitive but Java maps are
        // not :-/
        // so let's make sure we modify the existing key
        String pathKey = "PATH";
        for (String key : environment.keySet()) {
            if ("PATH".equalsIgnoreCase(key)) {
                pathKey = key;
            }
        }
        String path = environment.get(pathKey) + ";" + ureBin.getAbsolutePath();
        if (basisProgram != null) {
            path += ";" + basisProgram.getAbsolutePath();
        }
        logger.fine(String.format("setting %s to \"%s\"", pathKey, path));
        environment.put(pathKey, path);
    }

    public boolean isRunning() {
        if (process == null) {
            return false;
        }
        try {
            process.exitValue();
            return false;
        } catch (IllegalThreadStateException exception) {
            return true;
        }
    }

    private class ExitCodeRetryable extends Retryable {

        private int exitCode;

        protected void attempt() throws TemporaryException, Exception {
            try {
                exitCode = process.exitValue();
            } catch (IllegalThreadStateException illegalThreadStateException) {
                throw new TemporaryException(illegalThreadStateException);
            }
        }

        public int getExitCode() {
            return exitCode;
        }

    }

    public int getExitCode(long retryInterval, long retryTimeout)
            throws RetryTimeoutException {
        try {
            ExitCodeRetryable retryable = new ExitCodeRetryable();
            retryable.execute(retryInterval, retryTimeout);
            return retryable.getExitCode();
        } catch (RetryTimeoutException retryTimeoutException) {
            throw retryTimeoutException;
        } catch (Exception exception) {
            throw new OfficeException("could not get process exit code",
                    exception);
        }
    }

    public int forciblyTerminate(long retryInterval, long retryTimeout)
            throws IOException, RetryTimeoutException {
        logger.info(String.format("trying to forcibly terminate process: '"
                + unoUrl + "'" + (pid != null ? " (pid " + pid + ")" : "")));
        processManager.kill(process, pid);
        return getExitCode(retryInterval, retryTimeout);
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("\nOfficeHome : " + officeHome);
        sb.append("\nUnoUrl : " + unoUrl);
        if (lastCommand != null) {
            sb.append("\nCommand line : \n");
            for (String part : lastCommand) {
                sb.append(part + " ");
            }
        }
        sb.append("\nPID : " + pid);
        if (templateProfileDir == null) {
            sb.append("\ntemplateProfileDir : null");
        } else {
            sb.append("\ntemplateProfileDir : "
                    + templateProfileDir.getAbsolutePath());
        }
        if (instanceProfileDir == null) {
            sb.append("\ninstanceProfileDir : null");

        } else {
            sb.append("\ninstanceProfileDir : "
                    + instanceProfileDir.getAbsolutePath());

        }
        sb.append("\nProcessManager : "
                + processManager.getClass().getSimpleName());
        if (versionDescriptor != null) {
            sb.append("\nversionDescriptor : " + versionDescriptor.toString());
        }
        return sb.toString();
    }
}

class StreamGobbler implements Runnable {

    protected final InputStream is;

    private final Logger logger = Logger.getLogger(getClass().getName());

    StreamGobbler(InputStream is) {
        this.is = is;
    }

    public void run() {
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        String line;
        try {
            while ((line = br.readLine()) != null) {
                logger.warning("StreamGobbler: " + line);
            }
        } catch (IOException e) {
            logger.warning(e.getMessage());
        }
    }
}
