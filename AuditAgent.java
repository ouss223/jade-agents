package myagents;

import jade.core.Agent;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.AID;
import jade.lang.acl.ACLMessage;

import java.lang.management.ManagementFactory;
import com.sun.management.OperatingSystemMXBean;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class AuditAgent extends Agent {

    protected void setup() {
        System.out.println("[AUDIT] " + getLocalName() + " started on " + here().getName());

        addBehaviour(new OneShotBehaviour() {
            public void action() {
                try {
                    double cpu = getSystemCpuPercent();
                    long freeMem = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class)
                            .getFreePhysicalMemorySize() / (1024 * 1024);

                    String topProcesses = getTopProcesses(3);

                    ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
                    msg.addReceiver(new AID("CentralAgent", AID.ISLOCALNAME));
                    String report = String.format(
                            "{\"type\":\"AUDIT\",\"node\":\"%s\",\"cpu\":%.2f,\"mem\":%d,\"processes\":\"%s\",\"timestamp\":%d}",
                            getLocalName(), cpu, freeMem, topProcesses.replace("\"", "'"), System.currentTimeMillis());
                    msg.setContent(report);
                    send(msg);

                    System.out.println("[AUDIT] " + getLocalName() + " completed audit, sending report");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private double getSystemCpuPercent() {
        try {
            List<String> lines = Files.readAllLines(Paths.get("/proc/stat"));
            String[] parts = lines.get(0).trim().split("\\s+");

            long user = Long.parseLong(parts[1]);
            long nice = Long.parseLong(parts[2]);
            long system = Long.parseLong(parts[3]);
            long idle = Long.parseLong(parts[4]);

            long total = user + nice + system + idle;
            long busy = user + nice + system;

            return (total == 0) ? 0.0 : Math.min(100.0, (100.0 * busy) / total);
        } catch (Exception e) {
            return 0.0;
        }
    }

    private String getTopProcesses(int count) {
        try {
            Process p = Runtime.getRuntime().exec(new String[] { "bash", "-c",
                    "ps -eo comm,%cpu --sort=-%cpu | head -" + (count + 1) + " | tail -" + count });
            java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line).append(";");
            }
            r.close();
            return sb.toString();
        } catch (Exception e) {
            return "error";
        }
    }

    protected void takeDown() {
        System.out.println("[AUDIT] " + getLocalName() + " terminating");
    }
}