package p2p.common.util;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

/**
 * Utility class for network-related operations.
 */
public class NetworkUtil {

    /**
     * Gets the local IP address of the machine.
     * It prefers site-local addresses (192.168.x.x, 10.x.x.x, etc.) over loopback.
     * Skips virtual network interfaces (e.g., VMware, VirtualBox, Docker, WSL vEthernet).
     *
     * @return The local IP address string.
     */
    public static String getLocalIpAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();

                // Skip loopback and disabled interfaces
                if (iface.isLoopback() || !iface.isUp()) {
                    continue;
                }

                // Skip virtual network interfaces
                if (isVirtualInterface(iface)) {
                    continue;
                }

                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();

                    // We only want IPv4 site-local addresses
                    if (addr instanceof Inet4Address && addr.isSiteLocalAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }

            // Fallback: If no site-local address is found, try to get the local host
            // address
            // This might return 127.0.0.1 or a public IP depending on configuration
            return InetAddress.getLocalHost().getHostAddress();

        } catch (Exception e) {
            System.err
                    .println("[NetworkUtil] Failed to resolve local IP, falling back to localhost: " + e.getMessage());
            return "127.0.0.1";
        }
    }

    /**
     * Checks if a network interface is a virtual interface.
     * Identifies common virtual adapters like VMware, VirtualBox, Hyper-V, Docker, and WSL.
     *
     * @param iface The network interface to check.
     * @return true if the interface is virtual, false otherwise.
     */
    private static boolean isVirtualInterface(NetworkInterface iface) {
        try {
            if (iface.isVirtual()) {
                return true;
            }
        } catch (Exception ignored) {
        }

        String name = iface.getName().toLowerCase();
        String displayName = iface.getDisplayName().toLowerCase();

        // Common virtual interface patterns
        String[] virtualPatterns = {
            "vmware", "virtualbox", "vbox", "hyper-v", "vethernet",
            "docker", "virbr", "vnic", "vmnet", "veth", "wsl"
        };

        for (String pattern : virtualPatterns) {
            if (name.contains(pattern) || displayName.contains(pattern)) {
                return true;
            }
        }

        return false;
    }
}
