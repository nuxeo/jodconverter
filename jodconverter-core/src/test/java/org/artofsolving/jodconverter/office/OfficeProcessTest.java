package org.artofsolving.jodconverter.office;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.fail;

import java.util.regex.Pattern;

import org.artofsolving.jodconverter.ReflectionUtils;
import org.artofsolving.jodconverter.process.*;
import org.artofsolving.jodconverter.util.PlatformUtils;
import org.testng.SkipException;
import org.testng.annotations.Test;

@Test
public class OfficeProcessTest {

    public void foundExistingProcessAndKill_Linux() throws Exception {
        if (!PlatformUtils.isLinux()) {
            throw new SkipException("LinuxProcessManager can only be tested on Linux");
        }
        this.foundExistingProcessAndKill(new UnixProcessManager());
    }
    
    public void foundExistingProcessAndKill_MacOS() throws Exception {
        if (!PlatformUtils.isMac()) {
            throw new SkipException("MacProcessManager can only be tested on MacOS");
        }
        this.foundExistingProcessAndKill(new MacProcessManager());
    }
    
    public void foundExistingProcessAndKill_Windows() throws Exception {
        if (!PlatformUtils.isWindows()) {
            throw new SkipException("WindowsProcessManager can only be tested on Windows");
        }
        this.foundExistingProcessAndKill(new WindowsProcessManager());
    }

    private void foundExistingProcessAndKill(ProcessManager processManager) throws Exception {
        UnoUrl unoUrl = UnoUrl.socket(2022);
        String processRegex = "soffice.*" + Pattern.quote(unoUrl.getAcceptString());
        
        ManagedOfficeProcessSettings settings = new ManagedOfficeProcessSettings(unoUrl);
        settings.setProcessManager(processManager);
        settings.setKillExistingProcess(true);
        ManagedOfficeProcess proc = new ManagedOfficeProcess(settings);
        try {
            proc.startAndWait();

            String procPid = settings.getProcessManager().findPid(processRegex);
            System.out.println(procPid);
            assertNotNull(procPid);

            ManagedOfficeProcess proc2 = new ManagedOfficeProcess(settings);
            try {
                proc2.startAndWait();
                String proc2Pid = settings.getProcessManager().findPid(processRegex);
                System.out.println(proc2Pid);
                assertNotNull(proc2Pid);
                assertFalse(((OfficeProcess) ReflectionUtils.getPrivateField(proc, "process")).isRunning());
            } finally {
                proc2.stopAndWait();
            }

        } finally {
            proc.stopAndWait();
        }
    }
    
    public void foundExistingProcessAndError_Linux() throws Exception {
        if (!PlatformUtils.isLinux()) {
            throw new SkipException("LinuxProcessManager can only be tested on Linux");
        }
        this.foundExistingProcessAndError(new UnixProcessManager());
    }
    
    public void foundExistingProcessAndError_MacOS() throws Exception {
        if (!PlatformUtils.isMac()) {
            throw new SkipException("MacProcessManager can only be tested on MacOS");
        }
        this.foundExistingProcessAndError(new MacProcessManager());
    }
    
    public void foundExistingProcessAndError_Windows() throws Exception {
        if (!PlatformUtils.isWindows()) {
            throw new SkipException("WindowsProcessManager can only be tested on Windows");
        }
        this.foundExistingProcessAndError(new WindowsProcessManager());
    }
    
    private void foundExistingProcessAndError(ProcessManager processManager) throws Exception {
        UnoUrl unoUrl = UnoUrl.socket(2022);
        String processRegex = "soffice.*" + Pattern.quote(unoUrl.getAcceptString());
        ManagedOfficeProcessSettings settings = new ManagedOfficeProcessSettings(unoUrl);
        settings.setProcessManager(processManager);
        settings.setKillExistingProcess(false);
        ManagedOfficeProcess proc = new ManagedOfficeProcess(settings);
        try {
            proc.startAndWait();

            String procPid = settings.getProcessManager().findPid(processRegex);
            System.out.println(procPid);
            assertNotNull(procPid);

            ManagedOfficeProcess proc2 = new ManagedOfficeProcess(settings);
            try {
                proc2.startAndWait();
                fail();
            } catch (OfficeException e) {
                // ok
            } finally {
                try {
                    proc2.stopAndWait();
                } catch (Exception e) {}
            }

        } finally {
            proc.stopAndWait();
        }
    }
}
