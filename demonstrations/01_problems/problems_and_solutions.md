# Problems Encountered and Solutions Applied

## 1. GNS3 Desktop Instability

**Problem**: GNS3 Desktop interface froze in an infinite loading loop, making network topology management impossible.

**Solution**: Switch to GNS3 Web UI, much more stable and better adapted to the work environment.

## 2. Insufficient Dynamips Router Performance

**Problem**: Routers based on Dynamips offered very limited throughput (1-2 KB/s), incompatible with simulation needs.

**Solution**: Transition to QEMU routers, multiplying throughput by 50-100x (100-150 KB/s).

## 3. Bridged Mode Instability

**Problem**: Bridged mode presented superior throughput (230 KB/s) but caused frequent random VM shutdowns.

**Solution**: Definitive adoption of NAT mode, offering optimal balance between stability and performance (120-150 KB/s) adapted to double-virtualized environment (VMware → GNS3VM).

## 4. Disk Space Saturation

**Problem**: Multiplicity of VM images quickly saturated internal disk.

**Solution**: Transfer images to dedicated external hard drive, freeing up system space.

## 5. Insufficient Debian Partition Capacity

**Problem**: Default Debian image had only 1.9 GB, insufficient for installing all tools (Java, PostgreSQL, Grafana, JADE).

**Solution**: Resize virtual partitions via `qemu-img resize /path/to/image.qcow2 +10G`, increasing capacity to 12 GB.

## 6. Network Incompatibilities with Alpine Linux

**Problem**: Alpine image, though lightweight, presented repeated errors during JADE installation and agent execution, resulting from network support limitations.

**Solution**: Complete migration to Debian, offering superior stability and compatibility with Java/JADE ecosystem.

## 7. JADE Agent Corruption

**Problem**: During initial deployments, several JADE agents became corrupted due to repeated manipulations and incomplete configurations, rendering VMs unusable.

**Solution**: Complete reconstruction of affected VMs from clean images, adoption of strict deployment procedures and versioning.

## 8. Grafana Inaccessibility in Bridge Mode

**Problem**: External access to Grafana was not possible in bridge mode due to network constraints imposed by GNS3 and double virtualization.

**Solution**: Configuration of Cloudflare tunnel to Grafana, providing secure and performant access without requiring direct service exposure.

## 9. Disk Blockages and I/O Errors

**Problem**: During disk stress tests, some machines encountered repeated I/O errors with filesystem entering read-only mode.

**Solution**: Filesystem repair via `fsck`, ensuring I/O stability at hypervisor level, reconstruction of severely damaged VMs.

## 10. SSL Errors and PostgreSQL Issues

**Problem**: PostgreSQL post-installation script failed with segmentation faults during default SSL certificate generation.

**Solution**: 
1. Ran `fsck` on primary partition
2. Ensured I/O stability at host storage level
3. Ran `sudo dpkg --configure -a` to complete pending configurations
4. Installation correction via `sudo apt install -f`

Once SSL certificate was generated correctly, PostgreSQL and dependencies could complete installation.

## Global Optimizations

- **QEMU Routers**: performance multiplied by 50-100x
- **NAT Mode**: optimal stability for double virtualization
- **External Disk**: freed system space
- **Resized Partitions**: 1.9 GB → 12 GB
- **Debian**: superior stability over Alpine
- **Cloudflare Tunnel**: secure and performant access

These progressive resolutions transformed unstable infrastructure into reliable and performant system capable of supporting complete distributed multi-agent system simulation.
