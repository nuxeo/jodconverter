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

import java.net.ConnectException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.star.frame.XDesktop;
import com.sun.star.lang.DisposedException;

class ManagedOfficeProcess
{
    private static final Integer EXIT_CODE_NEW_INSTALLATION = Integer.valueOf(81);

    private final ManagedOfficeProcessSettings settings;

    private final OfficeProcess process;

    private final OfficeConnection connection;

    private ExecutorService executor = Executors.newSingleThreadExecutor(new NamedThreadFactory("OfficeProcessThread"));

    private final Logger logger = Logger.getLogger(getClass().getName());

    public ManagedOfficeProcess(ManagedOfficeProcessSettings settings) throws OfficeException
    {
        this.settings = settings;
        this.process =
            new OfficeProcess(settings.getOfficeHome(), settings.getUnoUrl(), settings.getRunAsArgs(), settings
                .getTemplateProfileDir(), settings.getWorkDir(), settings
                .getProcessManager());
        this.connection = new OfficeConnection(settings.getUnoUrl());
    }

    public OfficeConnection getConnection()
    {
        return this.connection;
    }

    public void startAndWait() throws OfficeException
    {
        Future< ? > future = this.executor.submit(new Runnable()
        {
            public void run()
            {
                doStartProcessAndConnect();
            }
        });
        try {
            future.get();
        } catch (Exception exception) {
            throw new OfficeException("failed to start and connect", exception);
        }
    }

    public void stopAndWait() throws OfficeException
    {
        Future< ? > future = this.executor.submit(new Runnable()
        {
            public void run()
            {
                doStopProcess();
            }
        });
        try {
            future.get();
        } catch (Exception exception) {
            throw new OfficeException("failed to start and connect", exception);
        }
    }

    public void restartAndWait()
    {
        Future< ? > future = this.executor.submit(new Runnable()
        {
            public void run()
            {
                doStopProcess();
                doStartProcessAndConnect();
            }
        });
        try {
            future.get();
        } catch (Exception exception) {
            throw new OfficeException("failed to restart", exception);
        }
    }

    public void restartDueToTaskTimeout()
    {
        this.executor.execute(new Runnable()
        {
            public void run()
            {
                doTerminateProcess();
                // will cause unexpected disconnection and subsequent restart
            }
        });
    }

    public void restartDueToLostConnection()
    {
        this.executor.execute(new Runnable()
        {
            public void run()
            {
                try {
                    doEnsureProcessExited();
                    doStartProcessAndConnect();
                } catch (OfficeException officeException) {
                    ManagedOfficeProcess.this.logger.log(Level.SEVERE, "could not restart process", officeException);
                }
            }
        });
    }

    private void doStartProcessAndConnect() throws OfficeException
    {
        try {
            this.process.start();
            new Retryable()
            {
                @Override
                protected void attempt() throws TemporaryException, Exception
                {
                    try {
                        ManagedOfficeProcess.this.connection.connect();
                    } catch (ConnectException connectException) {
                        Integer exitCode = ManagedOfficeProcess.this.process.getExitCode();
                        if (exitCode == null) {
                            // process is running; retry later
                            throw new TemporaryException(connectException);
                        } else if (exitCode.equals(EXIT_CODE_NEW_INSTALLATION)) {
                            // restart and retry later
                            // see http://code.google.com/p/jodconverter/issues/detail?id=84
                            ManagedOfficeProcess.this.logger.log(Level.WARNING,
                                "office process died with exit code 81; restarting it");
                            ManagedOfficeProcess.this.process.start(true);
                            throw new TemporaryException(connectException);
                        } else {
                            throw new OfficeException("office process died with exit code " + exitCode,
                                connectException);
                        }
                    }
                }
            }.execute(this.settings.getRetryInterval(), this.settings.getRetryTimeout());
        } catch (Exception exception) {
            throw new OfficeException("could not establish connection", exception);
        }
    }

    private void doStopProcess()
    {
        try {
            XDesktop desktop =
                OfficeUtils.cast(XDesktop.class, this.connection.getService(OfficeUtils.SERVICE_DESKTOP));
            desktop.terminate();
        } catch (DisposedException disposedException) {
            // expected
        } catch (Exception exception) {
            // in case we can't get hold of the desktop
            doTerminateProcess();
        }
        doEnsureProcessExited();
    }

    private void doEnsureProcessExited() throws OfficeException
    {
        try {
            int exitCode = this.process.getExitCode(this.settings.getRetryInterval(), this.settings.getRetryTimeout());
            this.logger.info("process exited with code " + exitCode);
        } catch (RetryTimeoutException retryTimeoutException) {
            doTerminateProcess();
        }
        this.process.deleteProfileDir();
    }

    private void doTerminateProcess() throws OfficeException
    {
        try {
            int exitCode =
                this.process.forciblyTerminate(this.settings.getRetryInterval(), this.settings.getRetryTimeout());
            this.logger.info("process forcibly terminated with code " + exitCode);
        } catch (Exception exception) {
            throw new OfficeException("could not terminate process", exception);
        }
    }

    boolean isConnected()
    {
        return this.connection.isConnected();
    }
}
