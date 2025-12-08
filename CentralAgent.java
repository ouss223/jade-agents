package myagents;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.core.AID;
import jade.lang.acl.ACLMessage;

import java.sql.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Stack;

public class CentralAgent extends Agent {

    private Connection conn;
    private Map<String, List<Double>> nodeMetrics = new HashMap<>();

    private Map<String, Long> lastAuditTime = new HashMap<>();
    private final long AUDIT_COOLDOWN_MS = 30000;

    protected void setup() {
        System.out.println("Central Agent started.");

        try {
            Class.forName("org.postgresql.Driver");
            conn = DriverManager.getConnection(
                    "jdbc:postgresql://localhost:5432/network_monitor",
                    "agent_user",
                    "0000");

            String createMetrics = "CREATE TABLE IF NOT EXISTS metrics ("
                    + "id SERIAL PRIMARY KEY, "
                    + "agent_name VARCHAR(50), "
                    + "cpu DOUBLE PRECISION, "
                    + "ram BIGINT, "
                    + "disk BIGINT, "
                    + "disk_io VARCHAR(255), "
                    + "net_in BIGINT, "
                    + "net_out BIGINT, "
                    + "cpu_severity VARCHAR(20), "
                    + "ram_severity VARCHAR(20), "
                    + "disk_severity VARCHAR(20), "
                    + "disk_io_severity VARCHAR(20), "
                    + "net_severity VARCHAR(20), "
                    + "timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
                    + ")";
            conn.createStatement().execute(createMetrics);

            String createActions = "CREATE TABLE IF NOT EXISTS actions ("
                    + "id SERIAL PRIMARY KEY, "
                    + "node VARCHAR(50), "
                    + "metric VARCHAR(20), "
                    + "action VARCHAR(100), "
                    + "pid VARCHAR(20), "
                    + "process VARCHAR(200), "
                    + "reason TEXT, "
                    + "timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
                    + ")";
            conn.createStatement().execute(createActions);

            String createAudit = "CREATE TABLE IF NOT EXISTS audit_reports ("
                    + "id SERIAL PRIMARY KEY, "
                    + "node VARCHAR(50), "
                    + "cpu DOUBLE PRECISION, "
                    + "mem BIGINT, "
                    + "processes TEXT, "
                    + "timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
                    + ")";
            conn.createStatement().execute(createAudit);

        } catch (Exception e) {
            e.printStackTrace();
        }

        addBehaviour(new CyclicBehaviour() {
            public void action() {
                ACLMessage msg = receive();
                if (msg == null) {
                    block();
                    return;
                }

                String c = msg.getContent();
                try {
                    if (c.startsWith("{")) {
                        // Check if it's an audit report or action event
                        if (c.contains("\"type\":\"AUDIT\"")) {
                            saveAuditReport(c);
                        } else {
                            // JSON-like string → action event
                            saveAction(c);
                        }
                    } else {
                        // CSV metrics → standard metrics
                        saveMetrics(msg.getSender().getLocalName(), c);
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });

        addBehaviour(new TickerBehaviour(this, 10000) {
            protected void onTick() {
                try {
                    analyzeCorrelations();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void analyzeCorrelations() throws Exception {
        System.out.println("\n[CORRELATION] Analyzing metrics from last 60 seconds...");

        Map<String, List<Double>> cpuByAgent = getMetricsLast60Seconds();

        if (cpuByAgent.isEmpty()) {
            System.out.println("[CORRELATION] No agents with data in last 60 seconds");
            return;
        }

        System.out.println("[CORRELATION] Found " + cpuByAgent.size() + " agents with data: " + cpuByAgent.keySet());

        Map<String, List<Double>> validAgents = new HashMap<>();
        int MIN_SAMPLES = 5;

        for (Map.Entry<String, List<Double>> entry : cpuByAgent.entrySet()) {
            String agent = entry.getKey();
            List<Double> cpuList = entry.getValue();

            if (cpuList.size() < MIN_SAMPLES) {
                System.out.println(
                        "[CORRELATION] Skipping " + agent + " - insufficient data (" + cpuList.size() + " samples)");
                continue;
            }
            validAgents.put(agent, cpuList);
        }

        System.out.println("[CORRELATION] Valid agents after filtering: " + validAgents.size() + " agents");

        if (validAgents.size() == 0) {
            System.out.println(
                    "[CORRELATION] No agents have sufficient data (minimum " + MIN_SAMPLES + " samples required)");
            return;
        } else if (validAgents.size() == 1) {
            String agent = validAgents.keySet().iterator().next();
            List<Double> cpuList = validAgents.get(agent);
            double avgCpu = cpuList.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double maxCpu = cpuList.stream().mapToDouble(Double::doubleValue).max().orElse(0);

            System.out.println("[CORRELATION] Agent " + agent + " - Avg CPU: " + String.format("%.2f", avgCpu) +
                    "%, Max CPU: " + String.format("%.2f", maxCpu) + "%");

            if (avgCpu > 80.0) {
                System.out.println("[CORRELATION] Agent " + agent + " qualifies for audit (standalone high CPU > 80%)");
                dispatchAuditAgent(agent);
            } else if (maxCpu > 90.0) {
                System.out.println("[CORRELATION] Agent " + agent + " qualifies for audit (standalone peak CPU > 90%)");
                dispatchAuditAgent(agent);
            }
            return;
        }

        // Multiple agents: correlation analysis with CLUSTERING
        System.out.println("[CORRELATION] Analyzing correlations between " + validAgents.size() + " agents");

        Map<String, Double> correlations = calculateCorrelations(validAgents);

        Map<String, Set<String>> correlationGraph = new HashMap<>();
        double CORRELATION_THRESHOLD = 0.75;

        for (Map.Entry<String, Double> entry : correlations.entrySet()) {
            String pair = entry.getKey();
            double corr = entry.getValue();

            if (corr > CORRELATION_THRESHOLD) {
                String[] agents = pair.split(" <-> ");
                String agent1 = agents[0].trim();
                String agent2 = agents[1].trim();

                correlationGraph.computeIfAbsent(agent1, k -> new HashSet<>()).add(agent2);
                correlationGraph.computeIfAbsent(agent2, k -> new HashSet<>()).add(agent1);

                System.out.println("[CORRELATION] High correlation (" + String.format("%.3f", corr) + "): " + pair);
            }
        }

        Set<String> visited = new HashSet<>();
        for (String agent : validAgents.keySet()) {
            if (!visited.contains(agent)) {
                Set<String> cluster = findCorrelationCluster(agent, correlationGraph, visited);

                if (!cluster.isEmpty()) {
                    auditCluster(cluster, validAgents);
                }
            }
        }
    }

    // Find all agents in a cluster (connected component) via DFS
    private Set<String> findCorrelationCluster(String startAgent, Map<String, Set<String>> graph, Set<String> visited) {
        Set<String> cluster = new HashSet<>();
        Stack<String> stack = new Stack<>();
        stack.push(startAgent);

        while (!stack.isEmpty()) {
            String agent = stack.pop();
            if (visited.contains(agent))
                continue;

            visited.add(agent);
            cluster.add(agent);

            if (graph.containsKey(agent)) {
                for (String neighbor : graph.get(agent)) {
                    if (!visited.contains(neighbor)) {
                        stack.push(neighbor);
                    }
                }
            }
        }

        return cluster;
    }

    private void auditCluster(Set<String> cluster, Map<String, List<Double>> validAgents) {
        System.out.println("[CORRELATION] Cluster detected with " + cluster.size() + " agents: " + cluster);

        boolean auditNeeded = false;
        int highCpuCount = 0;
        StringBuilder clusterStats = new StringBuilder();

        for (String agent : cluster) {
            List<Double> cpuList = validAgents.get(agent);
            if (cpuList != null) {
                double avgCpu = cpuList.stream().mapToDouble(Double::doubleValue).average().orElse(0);
                double maxCpu = cpuList.stream().mapToDouble(Double::doubleValue).max().orElse(0);

                clusterStats.append(agent).append("(avg:").append(String.format("%.1f", avgCpu))
                        .append("% max:").append(String.format("%.1f", maxCpu)).append("%) ");

                if (avgCpu > 70.0 || maxCpu > 85.0) {
                    highCpuCount++;
                }
            }
        }

        System.out.println("[CORRELATION] Cluster stats: " + clusterStats.toString());

        if (highCpuCount >= 2 || cluster.size() == 1) {
            auditNeeded = true;
        }

        if (auditNeeded) {
            System.out.println("[CORRELATION] Cluster qualifies for audit - checking cooldown");
            for (String agent : cluster) {
                long now = System.currentTimeMillis();
                long lastAudit = lastAuditTime.getOrDefault(agent, 0L);
                long timeSinceLastAudit = now - lastAudit;

                if (timeSinceLastAudit >= AUDIT_COOLDOWN_MS) {
                    System.out.println("[DISPATCH] >> Auditing " + agent + " (cooldown passed)");
                    dispatchAuditAgent(agent);
                    lastAuditTime.put(agent, now);
                } else {
                    System.out.println("[CORRELATION] Skipping audit for " + agent + " (cooldown active: "
                            + (AUDIT_COOLDOWN_MS - timeSinceLastAudit) + "ms remaining)");
                }
            }
        } else {
            System.out.println("[CORRELATION] Cluster does not meet threshold (" + highCpuCount + "/" + cluster.size()
                    + " high CPU)");
        }
    }

    private Map<String, List<Double>> getMetricsLast60Seconds() throws Exception {
        Map<String, List<Double>> result = new HashMap<>();

        String query = "SELECT agent_name, cpu FROM metrics WHERE timestamp > NOW() - INTERVAL '60 seconds' ORDER BY agent_name, timestamp";

        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                String agent = rs.getString("agent_name");
                double cpu = rs.getDouble("cpu");

                // Validate CPU value (should be 0-100)
                if (cpu < 0 || cpu > 100) {
                    System.out.println("[CORRELATION] Invalid CPU value for " + agent + ": " + cpu + "%, skipping");
                    continue;
                }

                result.computeIfAbsent(agent, k -> new ArrayList<>()).add(cpu);
            }
        } catch (SQLException e) {
            System.out.println("[CORRELATION] Database error fetching metrics: " + e.getMessage());
            e.printStackTrace();
        }

        return result;
    }

    private Map<String, Double> calculateCorrelations(Map<String, List<Double>> cpuByAgent) {
        Map<String, Double> correlations = new HashMap<>();

        List<String> agents = new ArrayList<>(cpuByAgent.keySet());

        for (int i = 0; i < agents.size(); i++) {
            for (int j = i + 1; j < agents.size(); j++) {
                String agent1 = agents.get(i);
                String agent2 = agents.get(j);

                List<Double> cpu1 = cpuByAgent.get(agent1);
                List<Double> cpu2 = cpuByAgent.get(agent2);

                // Ensure both lists have data
                if (cpu1 == null || cpu2 == null || cpu1.isEmpty() || cpu2.isEmpty()) {
                    System.out.println(
                            "[CORRELATION] Skipping pair " + agent1 + " <-> " + agent2 + " (null or empty data)");
                    continue;
                }

                // Pad to same length
                int minLen = Math.min(cpu1.size(), cpu2.size());
                if (minLen < 2) {
                    System.out.println("[CORRELATION] Skipping pair " + agent1 + " <-> " + agent2
                            + " (less than 2 common samples)");
                    continue;
                }

                List<Double> cpu1_trim = cpu1.subList(0, minLen);
                List<Double> cpu2_trim = cpu2.subList(0, minLen);

                double corr = calculatePearson(cpu1_trim, cpu2_trim);
                String key = agent1 + " <-> " + agent2;
                correlations.put(key, corr);

                System.out.println(
                        "[CORRELATION] " + key + " = " + String.format("%.3f", corr) + " (samples: " + minLen + ")");
            }
        }

        return correlations;
    }

    private double calculatePearson(List<Double> x, List<Double> y) {
        if (x.size() != y.size() || x.size() < 2)
            return 0.0;

        double meanX = x.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double meanY = y.stream().mapToDouble(Double::doubleValue).average().orElse(0);

        double sumXY = 0, sumX2 = 0, sumY2 = 0;
        for (int i = 0; i < x.size(); i++) {
            double dx = x.get(i) - meanX;
            double dy = y.get(i) - meanY;
            sumXY += dx * dy;
            sumX2 += dx * dx;
            sumY2 += dy * dy;
        }

        double denom = Math.sqrt(sumX2 * sumY2);

        if (denom == 0) {
            return Math.abs(meanX - meanY) < 0.1 ? 1.0 : 0.0;
        }

        return sumXY / denom;
    }

    private void dispatchAuditAgent(String targetNode) {
        try {
            System.out.println("[DISPATCH] Sending AuditAgent to " + targetNode);

            jade.wrapper.AgentController ac = getContainerControllerByName(targetNode)
                    .createNewAgent("AuditAgent-" + System.nanoTime(), "myagents.AuditAgent",
                            new Object[] { targetNode });
            ac.start();

        } catch (Exception e) {
            System.out.println("[DISPATCH] Failed to dispatch AuditAgent to " + targetNode + ": " + e.getMessage());
        }
    }

    private jade.wrapper.ContainerController getContainerControllerByName(String containerName) throws Exception {
        return getContainerController();
    }

    private void saveMetrics(String agent, String csv) throws Exception {
        String[] p = csv.split(",");
        if (p.length < 11) {
            System.out.println("[WARN] Metrics from " + agent + " has only " + p.length + " fields, skipping");
            return;
        }

        try {
            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO metrics(agent_name,cpu,ram,disk,disk_io,net_in,net_out,"
                            + "cpu_severity,ram_severity,disk_severity,disk_io_severity,net_severity)"
                            + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)");

            ps.setString(1, agent);
            ps.setDouble(2, Double.parseDouble(p[0].trim()));
            ps.setLong(3, Long.parseLong(p[1].trim()));
            ps.setLong(4, Long.parseLong(p[2].trim()));
            ps.setString(5, p[3].trim());
            ps.setLong(6, Long.parseLong(p[4].trim()));
            ps.setLong(7, Long.parseLong(p[5].trim()));
            ps.setString(8, p[6].trim());
            ps.setString(9, p[7].trim());
            ps.setString(10, p[8].trim());
            ps.setString(11, p[9].trim());
            ps.setString(12, p[10].trim());

            ps.executeUpdate();
            System.out.println("Saved metrics from: " + agent);
        } catch (NumberFormatException e) {
            System.out.println("[ERROR] Invalid metric format from " + agent + ": " + e.getMessage());
        }
    }

    private void saveAction(String json) throws Exception {
        Pattern p = Pattern.compile("\"(\\w+)\":\"(.*?)\"");
        Matcher m = p.matcher(json);

        String node = null, metric = null, action = null, pid = null, proc = null, reason = null;

        while (m.find()) {
            String k = m.group(1);
            String v = m.group(2);

            switch (k) {
                case "node":
                    node = v;
                    break;
                case "metric":
                    metric = v;
                    break;
                case "action":
                    action = v;
                    break;
                case "pid":
                    pid = v;
                    break;
                case "proc":
                    proc = v;
                    break;
                case "reason":
                    reason = v;
                    break;
            }
        }

        PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO actions(node,metric,action,pid,process,reason) "
                        + "VALUES (?,?,?,?,?,?)");
        ps.setString(1, node);
        ps.setString(2, metric);
        ps.setString(3, action);
        ps.setString(4, pid);
        ps.setString(5, proc);
        ps.setString(6, reason);

        ps.executeUpdate();

        System.out.println("Saved action from " + node + " → " + action);
    }

    private void saveAuditReport(String json) throws Exception {
        try {
            String node = extractJsonValue(json, "node");
            String target = extractJsonValue(json, "target");
            double cpu = Double.parseDouble(extractJsonValue(json, "cpu"));
            long mem = Long.parseLong(extractJsonValue(json, "mem"));
            String processes = extractJsonValue(json, "processes");

            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO audit_reports(node,cpu,mem,processes) "
                            + "VALUES (?,?,?,?)");
            ps.setString(1, target != null ? target : node);
            ps.setDouble(2, cpu);
            ps.setLong(3, mem);
            ps.setString(4, processes);

            ps.executeUpdate();

            System.out.println("Saved audit report from " + (target != null ? target : node) + " | CPU: " + cpu
                    + "% | MEM: " + mem + "MB");
        } catch (Exception e) {
            System.out.println("[ERROR] Failed to save audit report: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String extractJsonValue(String json, String key) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*\"?([^,}]*)\"?");
        Matcher m = p.matcher(json);
        if (m.find()) {
            return m.group(1).replace("\"", "").trim();
        }
        return null;
    }
}
