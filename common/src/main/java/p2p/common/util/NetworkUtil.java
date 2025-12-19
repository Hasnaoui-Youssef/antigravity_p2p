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
}
