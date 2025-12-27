# GNS3 Network Architecture

## Global Topology

The network architecture simulated in GNS3 is built around six Debian virtual machines connected through a central switch, a Cisco IOSv router managing traffic, and a NAT node providing Internet access.

![Network Topology Diagram](../../media/map%20topology.png)

## Topology Characteristics

### Virtual Machines (VMs)

- **5 monitoring agents**: Debian hosts dedicated to local metric collection and autonomous reaction
- **1 central server**: Debian host running PostgreSQL, Grafana, and the coordinating agents (CentralAgent and AuditAgent)

Each VM is configured with:
- A static IP address (192.168.1.100 for the central server, 192.168.1.101 to 192.168.1.105 for the agents)
- An Ethernet interface connected to the GNS3 switch
- Java and JADE for agent execution

### Network Equipment

- **GNS3 switch**: central interconnection point linking all Debian machines
- **Cisco IOSv 15.9M6 router**: handles internal routing and traffic management to the outside
- **NAT node**: provides address translation and Internet access for software updates

## Observed Performance

QEMU routers deliver significant improvement compared to Dynamips routers:
- Dynamips: 1-2 KB/s
- QEMU: 100-150 KB/s

Connectivity tests show latency under one millisecond and 0% packet loss.

![Ping Test Results](../../media/ping%20test.png)

## Connectivity Modes

After several iterations, **NAT** mode was selected for superior stability despite two layers of virtualization:
- VMware (hardware virtualization)
- GNS3VM (GNS3 environment virtualization)
- QEMU (virtualization of the Debian VMs and the router)

**Bridged** mode initially offered better performance but caused random system shutdowns, making it unsuitable for production use.

## Storage and Resources

Debian VM images were moved to an external hard drive to avoid saturating the internal disk and to preserve host performance. VM partition resizing was performed with the command:

```bash
qemu-img resize /path/to/image.qcow2 +10G
```

This increased the default capacity from 1.9 GB to 12 GB, accommodating installation of all required tools.

## Conclusion

The GNS3 topology provides a robust, high-performing simulation environment, supplying the infrastructure foundation needed to deploy and test the JADE multi-agent system.
