# Simulation and Load Tests

## Network Connectivity Test

Initial tests validate the stability and consistency of the GNS3 infrastructure.

![Ping Test](../../media/ping%20test.png)

This confirms the network topology works correctly and all machines communicate reliably.

## CPU Load Test

A CPU stress test is triggered on all agents to evaluate anomaly detection and system reaction:

```bash
python3 - <<EOF &
while True:
    pass
EOF
```

![CPU Stress Test Video](../../media/cpu%20stress%20test%20on%20agent%201%20and%202%20scenario.mp4)

![CPU Graphs Stressed](../../media/cpu%20graphs(stressed%20agent%201%20and%202).png)

![CPU Graphs Normal](../../media/cpu%20graphs(normal).png)

### Observed Results

- Immediate threshold breach detection
- Activation of local reactions (throttle or kill process)
- Event recording in PostgreSQL
- Instant visualization in Grafana

## RAM Load Test

A script allocates 400MB of memory and holds it to test sensitivity to memory anomalies:

```bash
python3 - <<EOF &

for _ in range(50_000_000):
    data.append(0)
while True:
    pass
EOF
```

![RAM Graphs Stressed](../../media/ram%20graphs(stress%20ed%20agent%205).png)

### Observed Results

- Progressive, visible increase in RAM usage
- Correct severity classification
- Recording of actions taken by the local agent

## Disk Load Test

A disk stress test writes continuously to a file to evaluate reaction to intensive I/O operations:

```bash
python3 - <<EOF &
import os, time

file_path = "/tmp/disk_test.bin"
chunk = b"0" * 5_000_000
max_size = 100_000_000

with open(file_path, "wb") as f:
    while True:
        f.write(chunk)
        f.flush()
        if f.tell() >= max_size:
            f.seek(0)
EOF
```

This test highlights virtualization limits; some machines show slowdowns or temporary stalls under heavy writes.

## Network Test

A bandwidth test uses network transfer commands to validate detection of traffic spikes.

all that was done here to test it out was to download any file .
![network graphs](../../media/network%20graphs.png)


This capture demonstrates real-time visualization of network activity by the MonitoringAgents.

## Distributed Correlation and Audit

During simultaneous load tests on several agents, the system triggers the correlation module:

![Correlation and Dispatch](../../media/correlation%20and%20dispatch.png)

This shows how the system identifies clusters of correlated agents and decides when to launch audits.

## Audit Reports

![Mobile Reports](../../media/mobile%20reports%20and%20actions%20taken.png)

These reports include:
- Independent measurements performed
- Validation or refutation of initial anomalies
- Recommended actions
- Precise event timestamps

## Resilience and Limitations

Tests reveal several key points:

- **Strengths**: fast detection, autonomous reaction, effective correlation
- **Limitations**: some virtualized machines cannot sustain prolonged extreme disk writes
- **Resolutions**: threshold tuning, rebuilding corrupted VMs, increasing disk capacity

## Conclusion

The load tests show the system can efficiently identify anomalies, apply appropriate corrective reactions, and provide clear visualization of network behavior under stress.
