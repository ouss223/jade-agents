package myagents;

import jade.core.Agent;
import jade.core.behaviours.TickerBehaviour;
import jade.core.AID;
import jade.lang.acl.ACLMessage;

import java.lang.management.ManagementFactory;
import com.sun.management.OperatingSystemMXBean;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.File;
import java.io.IOException;
import java.net.NetworkInterface;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class MonitoringAgent extends Agent {

    // Thresholds
    private final double CPU_THRESHOLD = 85.0; // %
    private final long RAM_THRESHOLD = 512; // MB
    private final long DISK_THRESHOLD = 500; // MB
    private final double DISK_IO_THRESHOLD = 80.0; // %
    private final long NET_THRESHOLD = 1000000; // bytes per tick

    // Reaction config
    private final int CPULIMIT_PERCENT = 40; // throttle target %
    private final String ACTION_LOG = "agent_actions.log";

    // simple system process blacklist
    private final Set<String> systemProcesses = new HashSet<>();

    // prevent overlapping actions
    private Map<String, Boolean> actionInProgressPerAgent = new HashMap<>();
    private Map<String, Long> lastActionTimePerAgent = new HashMap<>();
    private final long ACTION_COOLDOWN_MS = 10000; // 10 second cooldown after kill
    private volatile boolean agentReady = false;

    // State for accurate CPU delta calculation
    private long prevTotal = 0;
    private long prevBusy = 0;

    protected void setup() {
        systemProcesses.add("systemd");
        systemProcesses.add("tmux");
        systemProcesses.add("kthreadd");
        systemProcesses.add("ksoftirqd");
        systemProcesses.add("rsyslogd");
        systemProcesses.add("sshd");
        systemProcesses.add("java");
        systemProcesses.add("bash");
        systemProcesses.add("init");
        systemProcesses.add("cpulimit");
        systemProcesses.add("ps");
        systemProcesses.add("ss");
        systemProcesses.add("awk");
        systemProcesses.add("grep");
        systemProcesses.add("sed");

        String nameArg = "agent1";
        Object[] args = getArguments();
        if (args != null && args.length > 0)
            nameArg = (String) args[0];
        final String agentName = nameArg;

        OperatingSystemMXBean osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);

        addBehaviour(new TickerBehaviour(this, 5000) {
            private int warmupCount = 0;

            protected void onTick() {
                // Warmup period: first 1 tick just collect metrics, don't act
                if (warmupCount < 1) {
                    warmupCount++;
                    double cpuLoad = osBean.getSystemCpuLoad();
                    System.out.println("[WARMUP] Tick " + warmupCount + ", CPU raw: " + cpuLoad);
                    return;
                }
                agentReady = true;

                double cpuLoad = getSystemCpuPercent();
                long freeMem = safeDiv(osBean.getFreePhysicalMemorySize(), (1024L * 1024L));
                File disk = new File("/");
                long freeDisk = disk.getFreeSpace() / (1024 * 1024);

                String diskIO = "";
                try {
                    Process p = Runtime.getRuntime()
                            .exec(new String[] { "bash", "-c", "iostat -dx 1 2 | awk 'NR>6 {print $NF}'" });
                    BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                    String line;
                    while ((line = reader.readLine()) != null)
                        diskIO += line.trim() + "% ";
                    reader.close();
                } catch (Exception e) {
                    diskIO = "Error";
                }

                long bytesIn = 0, bytesOut = 0;
                try {
                    Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
                    while (nets.hasMoreElements()) {
                        NetworkInterface ni = nets.nextElement();
                        if (!ni.isLoopback() && ni.isUp()) {
                            try {
                                String rxPath = "/sys/class/net/" + ni.getName() + "/statistics/rx_bytes";
                                String txPath = "/sys/class/net/" + ni.getName() + "/statistics/tx_bytes";
                                if (Files.exists(Paths.get(rxPath))) {
                                    String rx = new String(Files.readAllBytes(Paths.get(rxPath))).trim();
                                    bytesIn += Long.parseLong(rx);
                                }
                                if (Files.exists(Paths.get(txPath))) {
                                    String tx = new String(Files.readAllBytes(Paths.get(txPath))).trim();
                                    bytesOut += Long.parseLong(tx);
                                }
                            } catch (Exception exInner) {
                                // ignore per-interface read errors
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                // --- Local Analysis ---
                String cpuSeverity = classifySeverity(cpuLoad, CPU_THRESHOLD);
                String ramSeverity = classifySeverity(RAM_THRESHOLD - freeMem, RAM_THRESHOLD);
                String diskSeverity = classifySeverity(DISK_THRESHOLD - freeDisk, DISK_THRESHOLD);

                double diskIOVal = 0.0;
                try {
                    String[] parts = diskIO.trim().split(" ");
                    if (parts.length > 0) {
                        diskIOVal = Double.parseDouble(parts[parts.length - 1].replace("%", ""));
                    }
                } catch (Exception e) {
                    diskIOVal = 0.0;
                }
                String diskIOSeverity = classifySeverity(diskIOVal, DISK_IO_THRESHOLD);

                long netTotal = bytesIn + bytesOut;
                String netSeverity = classifySeverity(netTotal, NET_THRESHOLD);

                System.out.println(agentName + " CPU: " + cpuLoad + "%, RAM: " + freeMem +
                        "MB, Disk: " + freeDisk + "MB, Disk I/O: " + diskIO +
                        ", Net In: " + bytesIn + ", Net Out: " + bytesOut);

                if (!isActionInProgress(agentName)) {
                    long timeSinceLastAction = System.currentTimeMillis() -
                            lastActionTimePerAgent.getOrDefault(agentName, 0L);

                    if (timeSinceLastAction >= ACTION_COOLDOWN_MS) {
                        try {
                            if ("High".equals(cpuSeverity)) {
                                setActionInProgress(agentName, true);
                                takeLocalAction("cpu", agentName, cpuLoad, freeMem);
                                lastActionTimePerAgent.put(agentName, System.currentTimeMillis());
                            } else if ("High".equals(ramSeverity)) {
                                setActionInProgress(agentName, true);
                                takeLocalAction("ram", agentName, cpuLoad, freeMem);
                                lastActionTimePerAgent.put(agentName, System.currentTimeMillis());
                            } else if ("High".equals(diskIOSeverity)) {
                                setActionInProgress(agentName, true);
                                takeLocalAction("disk", agentName, cpuLoad, freeMem);
                                lastActionTimePerAgent.put(agentName, System.currentTimeMillis());
                            } else if ("High".equals(netSeverity)) {
                                setActionInProgress(agentName, true);
                                takeLocalAction("net", agentName, cpuLoad, freeMem);
                                lastActionTimePerAgent.put(agentName, System.currentTimeMillis());
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            setActionInProgress(agentName, false);
                        }
                    }
                }

                // --- Send to central agent ---
                try {
                    ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
                    msg.addReceiver(new AID("CentralAgent", AID.ISLOCALNAME));
                    msg.setContent(cpuLoad + "," + freeMem + "," + freeDisk + "," + diskIO + "," +
                            bytesIn + "," + bytesOut + "," +
                            cpuSeverity + "," + ramSeverity + "," + diskSeverity + "," +
                            diskIOSeverity + "," + netSeverity);
                    send(msg);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private boolean isActionInProgress(String agent) {
        return actionInProgressPerAgent.getOrDefault(agent, false);
    }

    private void setActionInProgress(String agent, boolean inProgress) {
        actionInProgressPerAgent.put(agent, inProgress);
    }

    private double safeMultiply(double a, double b) {
        if (a < 0)
            return 0.0;
        return a * b;
    }

    private long safeDiv(long a, long b) {
        if (b == 0)
            return a;
        return a / b;
    }

    private String classifySeverity(double value, double threshold) {
        if (value < threshold * 0.7)
            return "Low";
        else if (value < threshold)
            return "Medium";
        else
            return "High";
    }

    private static class ThrottleSession {
        Set<String> throttledPids = new HashSet<>();
        int iterations = 0;
        long startTime = 0;
        static final int MAX_ITERATIONS = 5;
        static final long MAX_TIME_MS = 2000;

        boolean canContinue() {
            return iterations < MAX_ITERATIONS && (System.currentTimeMillis() - startTime) < MAX_TIME_MS;
        }
    }

    private void takeLocalAction(String type, String agentName, double cpuLoad, long freeMem) {
        try {
            switch (type) {
                case "cpu":
                    handleCpuIteratively(agentName, cpuLoad, freeMem);
                    break;
                case "ram":
                    handleRamIteratively(agentName, cpuLoad, freeMem);
                    break;
                case "disk":
                    handleDiskIteratively(agentName, cpuLoad, freeMem);
                    break;
                case "net":
                    handleNetIteratively(agentName, cpuLoad, freeMem);
                    break;
                default:
                    logAction(agentName, type, "none", "unknown type", cpuLoad, freeMem);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        // Remove finally block — let handlers manage actionInProgress reset
    }

    private void handleCpuIteratively(String agentName, double cpuLoad, long freeMem) {
        try {
            ThrottleSession session = new ThrottleSession();
            session.startTime = System.currentTimeMillis();

            while (session.canContinue()) {
                Candidate proc = findTopOffender(session.throttledPids);
                if (proc == null) {
                    logAction(agentName, "cpu", "none", "No offender found", cpuLoad, freeMem);
                    break;
                }

                session.throttledPids.add(proc.pid);
                String actionTaken = "none";
                String reason = "";

                if (cpuLoad > 95.0) {
                    // System CPU > 95%: kill immediately
                    try {
                        executeShell("kill -9 " + proc.pid);
                        actionTaken = "kill-9";
                        reason = "system cpu > 95% (aggressive kill)";

                        String details = String.format("pid=%s,name=%s,cpu=%.2f,mem=%s",
                                proc.pid, proc.comm, proc.cpuPercent, proc.memPercent);
                        logAction(agentName, "cpu", actionTaken, reason + " | " + details, cpuLoad, freeMem);
                        System.out.println("[CPU ACTION] " + actionTaken + " on PID " + proc.pid + " (" + proc.comm
                                + ") - CPU: " + proc.cpuPercent + "% (system: " + cpuLoad + "%)");
                        sendActionToCentral(agentName, proc, "cpu", actionTaken, reason);

                        // Sleep 3 seconds after killing
                        sleepMillis(3000);
                        break;
                    } catch (Exception e) {
                        actionTaken = "kill-failed";
                        reason = "kill execution failed: " + e.getMessage();
                    }
                } else if (cpuLoad >= 65.0 && cpuLoad <= 95.0) {
                    // System CPU between 65-95%: throttle to half of process's current CPU + renice
                    int limit = Math.max(5, (int) (proc.cpuPercent / 2.0));
                    try {
                        executeShell("cpulimit -p " + proc.pid + " -l " + limit + " > /dev/null 2>&1 &");
                        executeShell("renice +10 -p " + proc.pid + " > /dev/null 2>&1");
                        actionTaken = "throttle+renice";
                        reason = "system cpu 65-95%, limited to " + limit + "% + reniced +10";
                    } catch (Exception e) {
                        actionTaken = "throttle-failed";
                        reason = "throttle execution failed: " + e.getMessage();
                    }
                } else {
                    // System CPU < 65%: do nothing
                    actionTaken = "none";
                    reason = "system cpu < 65%, no action needed";
                }

                String details = String.format("pid=%s,name=%s,cpu=%.2f,mem=%s,iteration=%d",
                        proc.pid, proc.comm, proc.cpuPercent, proc.memPercent, session.iterations + 1);
                logAction(agentName, "cpu", actionTaken, reason + " | " + details, cpuLoad, freeMem);
                System.out.println("[CPU ACTION] " + actionTaken + " on PID " + proc.pid + " (" + proc.comm
                        + ") - CPU: " + proc.cpuPercent + "% (system: " + cpuLoad + "%)");
                sendActionToCentral(agentName, proc, "cpu", actionTaken, reason);

                session.iterations++;
                sleepMillis(200);
            }

            if (session.iterations > 0) {
                logAction(agentName, "cpu", "complete", "Processed " + session.iterations + " offenders", cpuLoad,
                        freeMem);
            }
        } finally {
            setActionInProgress(agentName, false); // ← Reset per-agent flag
        }
    }

    private void handleRamIteratively(String agentName, double cpuLoad, long freeMem) {
        try {
            ThrottleSession session = new ThrottleSession();
            session.startTime = System.currentTimeMillis();
            while (session.canContinue()) {
                Candidate proc = findTopOffender(session.throttledPids);
                if (proc == null) {
                    logAction(agentName, "ram", "none", "No offender found", cpuLoad, freeMem);
                    break;
                }
                session.throttledPids.add(proc.pid);
                String actionTaken = "none";
                String reason = "";
                try {
                    executeShell("kill -15 " + proc.pid);
                    sleepMillis(500);
                    if (isProcessAlive(proc.pid)) {
                        executeShell("kill -9 " + proc.pid);
                        actionTaken = "kill -9";
                        reason = "memory hog (force)";
                    } else {
                        actionTaken = "kill -15";
                        reason = "memory hog (graceful)";
                    }
                } catch (Exception e) {
                    actionTaken = "kill-failed";
                    reason = "kill execution failed";
                }
                String details = String.format("pid=%s,name=%s,cpu=%.2f,mem=%s,iteration=%d",
                        proc.pid, proc.comm, proc.cpuPercent, proc.memPercent, session.iterations + 1);
                logAction(agentName, "ram", actionTaken, reason + " | " + details, cpuLoad, freeMem);
                sendActionToCentral(agentName, proc, "ram", actionTaken, reason);
                session.iterations++;
                sleepMillis(100);
            }
            if (session.iterations > 0) {
                logAction(agentName, "ram", "complete", "Killed " + session.iterations + " processes", cpuLoad,
                        freeMem);
            }
        } finally {
            setActionInProgress(agentName, false);
        }
    }

    private void handleDiskIteratively(String agentName, double cpuLoad, long freeMem) {
        try {
            ThrottleSession session = new ThrottleSession();
            session.startTime = System.currentTimeMillis();

            while (session.canContinue()) {
                Candidate proc = findTopOffender(session.throttledPids);
                if (proc == null) {
                    logAction(agentName, "disk", "none", "No offender found", cpuLoad, freeMem);
                    break;
                }

                session.throttledPids.add(proc.pid);
                String actionTaken = "none";
                String reason = "";

                try {
                    if (tryRestartService(proc)) {
                        actionTaken = "restart-service";
                        reason = "attempted service restart for disk I/O";
                    } else {
                        executeShell("kill -15 " + proc.pid);
                        sleepMillis(300);
                        if (isProcessAlive(proc.pid)) {
                            executeShell("kill -9 " + proc.pid);
                        }
                        actionTaken = "kill-9";
                        reason = "killed disk I/O hog";
                    }
                } catch (Exception e) {
                    actionTaken = "disk-action-failed";
                    reason = "action execution failed";
                }

                String details = String.format("pid=%s,name=%s,cpu=%.2f,mem=%s,iteration=%d",
                        proc.pid, proc.comm, proc.cpuPercent, proc.memPercent, session.iterations + 1);
                logAction(agentName, "disk", actionTaken, reason + " | " + details, cpuLoad, freeMem);
                sendActionToCentral(agentName, proc, "disk", actionTaken, reason);

                session.iterations++;
                sleepMillis(100);
            }

            if (session.iterations > 0) {
                logAction(agentName, "disk", "complete", "Handled " + session.iterations + " processes", cpuLoad,
                        freeMem);
            }
        } finally {
            setActionInProgress(agentName, false);
        }
    }

    private void handleNetIteratively(String agentName, double cpuLoad, long freeMem) {
        try {
            ThrottleSession session = new ThrottleSession();
            session.startTime = System.currentTimeMillis();
            String iface = getPrimaryInterface();

            while (session.canContinue()) {
                Candidate netProc = findTopNetworkOffender(session.throttledPids);
                if (netProc == null) {
                    logAction(agentName, "net", "none", "No network offender found", cpuLoad, freeMem);
                    break;
                }

                session.throttledPids.add(netProc.pid);
                String actionTaken = "none";
                String reason = "";

                try {
                    if (isCpulimitAvailable()) {
                        executeShell("cpulimit -p " + netProc.pid + " -l " + CPULIMIT_PERCENT + " &");
                        actionTaken = "throttle-cpu";
                        reason = "network hog - throttled CPU (best-effort)";
                    } else if (iface != null && applyInterfaceThrottle(iface, "1mbit")) {
                        actionTaken = "throttle-interface";
                        reason = "network hog - interface throttle applied";
                    } else {
                        executeShell("kill -15 " + netProc.pid);
                        sleepMillis(300);
                        if (isProcessAlive(netProc.pid)) {
                            executeShell("kill -9 " + netProc.pid);
                        }
                        actionTaken = "kill-9";
                        reason = "network hog - killed (fallback)";
                    }
                } catch (Exception e) {
                    actionTaken = "net-action-failed";
                    reason = "action execution failed";
                }

                String details = String.format("pid=%s,name=%s,cpu=%.2f,mem=%s,iteration=%d",
                        netProc.pid, netProc.comm, netProc.cpuPercent, netProc.memPercent, session.iterations + 1);
                logAction(agentName, "net", actionTaken, reason + " | " + details, cpuLoad, freeMem);
                sendActionToCentral(agentName, netProc, "net", actionTaken, reason);

                session.iterations++;
                sleepMillis(100);
            }

            if (session.iterations > 0) {
                logAction(agentName, "net", "complete", "Handled " + session.iterations + " network offenders", cpuLoad,
                        freeMem);
            }
        } finally {
            setActionInProgress(agentName, false);
        }
    }

    // Small helper class to hold proc info
    private static class Candidate {
        String pid;
        String user;
        String comm;
        double cpuPercent;
        double memPercent;

        Candidate(String pid, String user, String comm, double cpuPercent, double memPercent) {
            this.pid = pid;
            this.user = user;
            this.comm = comm;
            this.cpuPercent = cpuPercent;
            this.memPercent = memPercent;
        }
    }

    private Candidate findTopOffender(Set<String> excludedPids) {
        try {
            long selfPid = ProcessHandle.current().pid();
            Process p = Runtime.getRuntime().exec(new String[] { "bash", "-c",
                    "ps -eo pid,user,comm,%cpu,%mem --sort=-%cpu | sed -n '2,20p'" });
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = r.readLine()) != null) {
                if (line.trim().isEmpty())
                    continue;
                String[] parts = line.trim().split("\\s+", 5);
                if (parts.length < 5)
                    continue;
                String pid = parts[0];
                String user = parts[1];
                String comm = parts[2];
                double cpu = safeParseDouble(parts[3]);
                double mem = safeParseDouble(parts[4]);

                // Skip excluded PIDs (already handled in this session)
                if (excludedPids.contains(pid))
                    continue;

                // skip root-owned and blacklisted and self pid
                if ("root".equals(user))
                    continue;
                if (systemProcesses.contains(comm))
                    continue;
                try {
                    long pidNum = Long.parseLong(pid);
                    if (pidNum == selfPid)
                        continue;
                } catch (NumberFormatException nfe) {
                    /* ignore */ }

                // also skip kernel threads (in brackets)
                if (comm.startsWith("["))
                    continue;

                // Pick first matching candidate (highest cpu)
                r.close();
                return new Candidate(pid, user, comm, cpu, mem);
            }
            r.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private Candidate findTopOffender() {
        return findTopOffender(new HashSet<>());
    }

    private Candidate findTopNetworkOffender(Set<String> excludedPids) {
        try {
            String cmd = "ss -tanp 2>/dev/null | grep -o 'pid=[0-9]\\+' | sed 's/pid=//' | sort | uniq -c | sort -nr | head -n1 | awk '{print $2}'";
            Process p = Runtime.getRuntime().exec(new String[] { "bash", "-c", cmd });
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String pid = r.readLine();
            r.close();
            if (pid == null || pid.trim().isEmpty())
                return null;

            pid = pid.trim();
            if (excludedPids.contains(pid))
                return null;

            String comm = "unknown";
            try {
                String commPath = new String(Files.readAllBytes(Paths.get("/proc/" + pid + "/comm"))).trim();
                if (!commPath.isEmpty())
                    comm = commPath;
            } catch (Exception ignored) {
            }

            String infoCmd = "ps -p " + pid + " -o %cpu,%mem --no-headers || true";
            Process p2 = Runtime.getRuntime().exec(new String[] { "bash", "-c", infoCmd });
            BufferedReader r2 = new BufferedReader(new InputStreamReader(p2.getInputStream()));
            String info = r2.readLine();
            r2.close();
            double cpu = 0.0, mem = 0.0;
            if (info != null && !info.trim().isEmpty()) {
                String[] parts = info.trim().split("\\s+");
                if (parts.length >= 2) {
                    cpu = safeParseDouble(parts[0]);
                    mem = safeParseDouble(parts[1]);
                }
            }

            if (systemProcesses.contains(comm))
                return null;

            return new Candidate(pid, "unknown", comm, cpu, mem);

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private Candidate findTopNetworkOffender() {
        return findTopNetworkOffender(new HashSet<>());
    }

    private double safeParseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (Exception e) {
            return 0.0;
        }
    }

    private boolean isCpulimitAvailable() {
        return new File("/usr/bin/cpulimit").canExecute() || new File("/bin/cpulimit").canExecute();
    }

    private void executeShell(String command) throws IOException, InterruptedException {
        Process p = Runtime.getRuntime().exec(new String[] { "bash", "-c", command });
        p.waitFor();
    }

    private boolean isProcessAlive(String pid) {
        return Paths.get("/proc", pid).toFile().exists();
    }

    private boolean tryRestartService(Candidate proc) throws IOException, InterruptedException {
        return executeOptional("systemctl restart " + proc.comm);
    }

    private boolean executeOptional(String cmd) throws IOException, InterruptedException {
        Process p = Runtime.getRuntime().exec(new String[] { "bash", "-c", cmd });
        return p.waitFor() == 0;
    }

    private void sleepMillis(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }

    private void logAction(String agent, String type, String action, String reason, double cpu, long freeMem) {
        String entry = String.format("%s | %s | %s | %s | %s | cpu=%.2f memFree=%d%n",
                Instant.now(), agent, type, action, reason, cpu, freeMem);
        try {
            Files.writeString(Paths.get(ACTION_LOG), entry, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
        }
    }

    private void sendActionToCentral(String agent, Candidate proc, String type, String action, String reason) {
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.addReceiver(new AID("CentralAgent", AID.ISLOCALNAME));
        // Send as JSON so CentralAgent recognizes it
        String json = String.format(
                "{\"node\":\"%s\",\"metric\":\"%s\",\"action\":\"%s\",\"pid\":\"%s\",\"proc\":\"%s\",\"reason\":\"%s\"}",
                agent, type, action, proc.pid, proc.comm, reason);
        msg.setContent(json);
        send(msg);
    }

    private String getPrimaryInterface() {
        try {
            return Collections.list(NetworkInterface.getNetworkInterfaces()).stream().filter(ni -> {
                try {
                    return ni.isUp() && !ni.isLoopback();
                } catch (Exception e) {
                    return false;
                }
            }).map(NetworkInterface::getName).findFirst().orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean applyInterfaceThrottle(String iface, String rate) throws IOException, InterruptedException {
        return executeOptional(
                String.format("tc qdisc replace dev %s root tbf rate %s burst 32kbit latency 400ms", iface, rate));
    }

    private double getSystemCpuPercent() {
        try {
            // Read /proc/stat directly
            List<String> lines = Files.readAllLines(Paths.get("/proc/stat"));
            String[] parts = lines.get(0).trim().split("\\s+");

            // Parse all columns
            long user = Long.parseLong(parts[1]);
            long nice = Long.parseLong(parts[2]);
            long system = Long.parseLong(parts[3]);
            long idle = Long.parseLong(parts[4]);
            long iowait = (parts.length > 5) ? Long.parseLong(parts[5]) : 0;
            long irq = (parts.length > 6) ? Long.parseLong(parts[6]) : 0;
            long softirq = (parts.length > 7) ? Long.parseLong(parts[7]) : 0;
            long steal = (parts.length > 8) ? Long.parseLong(parts[8]) : 0;

            long currentTotal = user + nice + system + idle + iowait + irq + softirq + steal;
            long currentBusy = user + nice + system + irq + softirq + steal;

            if (prevTotal == 0) {
                prevTotal = currentTotal;
                prevBusy = currentBusy;
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                }
                return getSystemCpuPercent();
            }

            long diffTotal = currentTotal - prevTotal;
            long diffBusy = currentBusy - prevBusy;

            prevTotal = currentTotal;
            prevBusy = currentBusy;

            if (diffTotal == 0)
                /* Line 756 omitted */

            return Math.min(100.0, Math.max(0.0, (100.0 * diffBusy) / diffTotal));
        } catch (Exception e) {
            e.printStackTrace();
        }

        double raw = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class).getSystemCpuLoad();
        return (raw >= 0) ? raw * 100.0 : 0.0;
    }
}
