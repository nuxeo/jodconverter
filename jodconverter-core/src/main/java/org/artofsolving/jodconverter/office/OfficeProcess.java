//
// JODConverter - Java OpenDocument Converter
// Copyright 2004-2012 Mirko Nasato and contributors
//
// JODConverter is Open Source software, you can redistribute it and/or
// modify it under either (at your option) of the following licenses
//
// 1. The GNU Lesser General Public License v3 (or later)
//    -> http://www.gnu.org/licenses/lgpl-3.0.txt
// 2. The Apache License, Version 2.0
//    -> http://www.apache.org/licenses/LICENSE-2.0.txt
//
package org.artofsolving.jodconverter.office;

import static org.artofsolving.jodconverter.process.ProcessManager.PID_NOT_FOUND;
import static org.artofsolving.jodconverter.process.ProcessManager.PID_UNKNOWN;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.artofsolving.jodconverter.process.ProcessManager;
import org.artofsolving.jodconverter.process.ProcessQuery;
import org.artofsolving.jodconverter.util.PlatformUtils;

class OfficeProcess
{
    private final File officeHome;

    private final UnoUrl unoUrl;

    private final String[] runAsArgs;

    private final File templateProfileDir;

    private final File instanceProfileDir;

    private final ProcessManager processManager;

    private Process process;

    private long pid = PID_UNKNOWN;

    private final Logger logger = Logger.getLogger(getClass().getName());

    public OfficeProcess(File officeHome, UnoUrl unoUrl, String[] runAsArgs, File templateProfileDir, File workDir,
        ProcessManager processManager)
    {
        this.officeHome = officeHome;
        this.unoUrl = unoUrl;
        this.runAsArgs = runAsArgs;
        this.templateProfileDir = templateProfileDir;
        this.instanceProfileDir = getInstanceProfileDir(workDir, unoUrl);
        this.processManager = processManager;
    }

    public void start() throws IOException
    {
        start(false);
    }

    public void start(boolean restart) throws IOException
    {
        ProcessQuery processQuery = new ProcessQuery("soffice.bin", this.unoUrl.getAcceptString());
        long existingPid = this.processManager.findPid(processQuery);
        if (!(existingPid == PID_NOT_FOUND || existingPid == PID_UNKNOWN)) {
            throw new IllegalStateException(String.format(
                "a process with acceptString '%s' is already running; pid %d",
                this.unoUrl.getAcceptString(), existingPid));
        }
        if (!restart) {
            prepareInstanceProfileDir();
        }
        List<String> command = new ArrayList<String>();
        File executable = OfficeUtils.getOfficeExecutable(this.officeHome);
        if (this.runAsArgs != null) {
            command.addAll(Arrays.asList(this.runAsArgs));
        }
        command.add(executable.getAbsolutePath());
        command.add("-accept=" + this.unoUrl.getAcceptString() + ";urp;");
        command.add("-env:UserInstallation=" + OfficeUtils.toUrl(this.instanceProfileDir));
        command.add("-headless");
        command.add("-nocrashreport");
        command.add("-nodefault");
        command.add("-nofirststartwizard");
        command.add("-nolockcheck");
        command.add("-nologo");
        command.add("-norestore");
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        if (PlatformUtils.isWindows()) {
            addBasisAndUrePaths(processBuilder);
        }
        this.logger.info(String.format("starting process with acceptString '%s' and profileDir '%s'", this.unoUrl,
            this.instanceProfileDir));
        this.process = processBuilder.start();
        this.pid = this.processManager.findPid(processQuery);
        if (this.pid == PID_NOT_FOUND) {
            throw new IllegalStateException(String.format(
                "process with acceptString '%s' started but its pid could not be found",
                this.unoUrl.getAcceptString()));
        }
        this.logger.info("started process" + (this.pid != PID_UNKNOWN ? "; pid = " + this.pid : ""));
    }

    private File getInstanceProfileDir(File workDir, UnoUrl unoUrl)
    {
        String dirName = ".jodconverter_" + unoUrl.getAcceptString().replace(',', '_').replace('=', '-');
        return new File(workDir, dirName);
    }

    private void prepareInstanceProfileDir() throws OfficeException
    {
        if (this.instanceProfileDir.exists()) {
            this.logger.warning(String.format("profile dir '%s' already exists; deleting", this.instanceProfileDir));
            deleteProfileDir();
        }
        if (this.templateProfileDir != null) {
            try {
                FileUtils.copyDirectory(this.templateProfileDir, this.instanceProfileDir);
            } catch (IOException ioException) {
                throw new OfficeException("failed to create profileDir", ioException);
            }
        }
    }

    public void deleteProfileDir()
    {
        if (this.instanceProfileDir != null) {
            try {
                FileUtils.deleteDirectory(this.instanceProfileDir);
            } catch (IOException ioException) {
                File oldProfileDir =
                    new File(this.instanceProfileDir.getParentFile(), this.instanceProfileDir.getName() + ".old."
                    + System.currentTimeMillis());
                if (this.instanceProfileDir.renameTo(oldProfileDir)) {
                    this.logger.warning("could not delete profileDir: " + ioException.getMessage() + "; renamed it to "
                        + oldProfileDir);
                } else {
                    this.logger.severe("could not delete profileDir: " + ioException.getMessage());
                }
            }
        }
    }

    private void addBasisAndUrePaths(ProcessBuilder processBuilder) throws IOException
    {
        // see http://wiki.services.openoffice.org/wiki/ODF_Toolkit/Efforts/Three-Layer_OOo
        File basisLink = new File(this.officeHome, "basis-link");
        if (!basisLink.isFile()) {
            this.logger
                .fine("no %OFFICE_HOME%/basis-link found; assuming it's OOo 2.x and we don't need to append URE and Basic paths");
            return;
        }
        String basisLinkText = FileUtils.readFileToString(basisLink).trim();
        File basisHome = new File(this.officeHome, basisLinkText);
        File basisProgram = new File(basisHome, "program");
        File ureLink = new File(basisHome, "ure-link");
        String ureLinkText = FileUtils.readFileToString(ureLink).trim();
        File ureHome = new File(basisHome, ureLinkText);
        File ureBin = new File(ureHome, "bin");
        Map<String, String> environment = processBuilder.environment();
        // Windows environment variables are case insensitive but Java maps are not :-/
        // so let's make sure we modify the existing key
        String pathKey = "PATH";
        for (String key : environment.keySet()) {
            if ("PATH".equalsIgnoreCase(key)) {
                pathKey = key;
            }
        }
        String path = environment.get(pathKey) + ";" + ureBin.getAbsolutePath() + ";" + basisProgram.getAbsolutePath();
        this.logger.fine(String.format("setting %s to \"%s\"", pathKey, path));
        environment.put(pathKey, path);
    }

    public boolean isRunning()
    {
        if (this.process == null) {
            return false;
        }
        return getExitCode() == null;
    }

    private class ExitCodeRetryable extends Retryable
    {

        private int exitCode;

        @Override
        protected void attempt() throws TemporaryException, Exception
        {
            try {
                this.exitCode = OfficeProcess.this.process.exitValue();
            } catch (IllegalThreadStateException illegalThreadStateException) {
                throw new TemporaryException(illegalThreadStateException);
            }
        }

        public int getExitCode()
        {
            return this.exitCode;
        }

    }

    public Integer getExitCode()
    {
        try {
            return this.process.exitValue();
        } catch (IllegalThreadStateException exception) {
            return null;
        }
    }

    public int getExitCode(long retryInterval, long retryTimeout) throws RetryTimeoutException
    {
        try {
            ExitCodeRetryable retryable = new ExitCodeRetryable();
            retryable.execute(retryInterval, retryTimeout);
            return retryable.getExitCode();
        } catch (RetryTimeoutException retryTimeoutException) {
            throw retryTimeoutException;
        } catch (Exception exception) {
            throw new OfficeException("could not get process exit code", exception);
        }
    }

    public int forciblyTerminate(long retryInterval, long retryTimeout) throws IOException, RetryTimeoutException
    {
        this.logger.info("trying to forcibly terminate process: '" + this.unoUrl + "'"
            + (this.pid != PID_UNKNOWN ? " (pid " + this.pid + ")" : ""));
        if (this.pid == PID_UNKNOWN) {
            long foundPid = this.processManager.findPid(new ProcessQuery("soffice.*", this.unoUrl.getAcceptString()));
            try {
                this.processManager.kill(this.process, foundPid);
            } catch (IllegalArgumentException ex) {
                this.logger.severe("Failed to forcibly terminate process: " + ex.getMessage());
            }
        } else {
            this.processManager.kill(this.process, this.pid);
        }
        return getExitCode(retryInterval, retryTimeout);
    }
}
