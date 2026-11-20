package io.github.ratmux.sdhelper.agent.server

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface

/** The agent only talks to the local network (docs/SECURITY.md, "Network"). */
object PrivateNetwork {
    /** True for loopback, RFC 1918 (site local), link-local and IPv6 unique-local (fc00::/7) addresses. */
    fun isAllowed(address: InetAddress): Boolean =
        address.isLoopbackAddress ||
            address.isSiteLocalAddress ||
            address.isLinkLocalAddress ||
            (address is Inet6Address && (address.address[0].toInt() and ULA_MASK) == ULA_PREFIX)

    /** Accepts IP address literals only; names are never resolved, so this cannot trigger DNS lookups. */
    fun isAllowed(literal: String): Boolean {
        val looksLikeLiteral = literal.isNotEmpty() && literal.all { it.isLetterOrDigit() || it in ":.%" }
        if (!looksLikeLiteral || (':' !in literal && '.' !in literal)) return false
        return runCatching { InetAddress.getByName(literal) }.getOrNull()?.let(::isAllowed) ?: false
    }

    /** Site-local IPv4 addresses of this computer, the candidates to put into the pairing QR code. */
    fun localIpv4Addresses(): List<Inet4Address> =
        NetworkInterface
            .getNetworkInterfaces()
            ?.toList()
            .orEmpty()
            .filter { runCatching { it.isUp && !it.isLoopback && !it.isVirtual }.getOrDefault(false) }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .filter { it.isSiteLocalAddress }

    private const val ULA_MASK = 0xFE
    private const val ULA_PREFIX = 0xFC
}
