# Installation and Configuration of the GNS3 Infrastructure

## Phase 1: Starting GNS3 VM and VMware

Download the GNS3 VM image from the official repository and import it into VMware Workstation. Once launched, the GNS3 VM exposes a Web interface accessible from a browser.

![GNS3 VM on VMware](../../media/gns3vm%20on%20vmware.png)

After startup, the GNS3 Web interface is available, enabling centralized management of the simulated network topology.

![GNS3 Web UI](../../media/gns3vm%20ui.png)

## Phase 2: Importing Device Templates

Import two categories of templates: the Cisco IOSv 15.9M6 router and Debian images for monitoring virtual machines.

### Cisco Router Import

![Cisco Router Template](../../media/searching%20for%20a%20template(ciscoIOS%20router).png)

![Template Import](../../media/downloading,importing%20and%20then%20creating%20a%20%20template.png)

### Debian Image Import

![Debian Template Search](../../media/searching%20for%20a%20template(debian).png)

![Running Debian Instance](../../media/example%20of%20running%20a%20debian%20instance.png)

## Phase 3: Building the Network Topology

The final topology contains six Debian machines linked through a GNS3 switch, a Cisco IOSv router, and a NAT node for Internet access.

![Network Topology](../../media/map%20topology.png)

### Network Configurations

Each Debian machine gets a static IP address through `/etc/network/interfaces`:

```
auto ens5
iface ens5 inet static
    address 192.168.1.x
    netmask 255.255.255.0
    gateway 192.168.1.1
    dns-nameservers 8.8.8.8 1.1.1.1
```

![Agent Interface Config](../../media/agent1%20interface%20config.png)

### Cisco Router Configuration

The Cisco IOSv router is configured using `/configs/router/router.config`. After configuration, router interfaces are visible and operational.

![Router Interfaces](../../media/router%20interface%20brief.png)

## Phase 4: Connectivity Verification

A ping test between Debian machines validates the GNS3 network consistency and the static IP configuration.

![Ping Test](../../media/ping%20test.png)

## Phase 5: NAT Mode Validation

NAT mode is validated by downloading from the Internet to a Debian machine. With QEMU routers (preferred to Dynamips), observed throughput reaches about 103 KB/s.

![GNS3 Network Settings](../../media/gns3VM%20network%20settings.png)

## Applied Optimization Parameters

Several optimizations were applied based on encountered issues:

- **QEMU routers** replace Dynamips, improving throughput from 1-2 KB/s to 100-150 KB/s
- **NAT mode** stabilizes connectivity despite two virtualization layers (VMware → GNS3VM → QEMU)
- **Debian images** stored on external drive to avoid internal disk saturation
- **Partition resizing** via `qemu-img resize /path/to/image.qcow2 +10G` to increase available capacity

## Conclusion

This GNS3 infrastructure provides a solid foundation for deploying the JADE multi-agent system, ensuring reliable distributed network simulation with minimal latency and strong stability.
