# Sony SemcPowerSaveModule Crash Fixer

### What does this module do?

Fix this issue:
```bash
I vendor.qti.hardware.servicetrackeraidl-service: total connections for client : com.sonyericsson.psm.sysmonserviceare :1
E AndroidRuntime: Process: com.sonyericsson.psm.sysmonservice, PID: 24018
E AndroidRuntime:   at com.sonyericsson.psm.sysmonservice.ProcessMonitor.getPkgName(ProcessMonitor.java:156)
E AndroidRuntime:   at com.sonyericsson.psm.sysmonservice.ProcessMonitor.getProcInfo(ProcessMonitor.java:132)
E AndroidRuntime:   at com.sonyericsson.psm.sysmonservice.ProcessMonitor.updateThread(ProcessMonitor.java:84)
E AndroidRuntime:   at com.sonyericsson.psm.sysmonservice.ProcessMonitor.-$$Nest$mupdateThread(ProcessMonitor.java:0)
E AndroidRuntime:   at com.sonyericsson.psm.sysmonservice.ProcessMonitor$1.run(ProcessMonitor.java:51)
W ActivityManager: Process com.sonyericsson.psm.sysmonservice has crashed too many times, killing! Reason: crashed quickly
I ActivityManager: Process com.sonyericsson.psm.sysmonservice (pid 24018) has died: pers PER
```