# Network Configuration Guide

This folder contains the network configuration file for each VM in the GNS3 project.

## File: `interfaces`

```bash
auto ens5
iface ens5 inet static
    address 192.168.1.x
    netmask 255.255.255.0
    gateway 192.168.1.1
    dns-nameservers 8.8.8.8 8.8.4.4
```

### Explanation of `x`

- The `x` in `192.168.1.x` represents a unique host number for each VM in the network.
- For example:
  - VM1 → `192.168.1.11`
  - VM2 → `192.168.1.12`
  - VM3 → `192.168.1.13` ... etc

### Important Note

Make sure to adjust the "x" value  based on the specific network device .