package dev.foxgirl.torrent.client;

import dev.foxgirl.torrent.bencode.BencodeElement;
import dev.foxgirl.torrent.bencode.BencodeMap;
import dev.foxgirl.torrent.bencode.BencodeString;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class Extensions {

    private static final Extensions SUPPORTED_EXTENSIONS = new Extensions();
    static {
        SUPPORTED_EXTENSIONS.setFastPeers(true);
        SUPPORTED_EXTENSIONS.setExtensionProtocol(true);
        SUPPORTED_EXTENSIONS.setExtensionMessages(Map.of(
                "ut_metadata", 10,
                "lt_donthave", 20
        ));
        SUPPORTED_EXTENSIONS.setExtensionClientVersion("https://github.com/luavixen/torrent");
        SUPPORTED_EXTENSIONS.setExtensionMaxOutstandingRequests(100);
    }

    public static @NotNull Extensions getSupportedExtensions() {
        return new Extensions(SUPPORTED_EXTENSIONS);
    }

    private final Object lock = new Object();
    private final BitSet bitset = new BitSet(64);

    private @Nullable Map<@NotNull String, @NotNull Integer> extensionMessages;
    private @Nullable Integer extensionTcpListenPort;
    private @Nullable String extensionClientVersion;
    private byte @Nullable [] extensionYourIP;
    private byte @Nullable [] extensionIPv6;
    private byte @Nullable [] extensionIPv4;
    private @Nullable Integer extensionMaxOutstandingRequests;

    private void assertArrayLength(byte[] bits) {
        if (bits.length != 8) {
            throw new IllegalArgumentException("Length of bits array is not 8");
        }
    }
    private void fromArray(byte[] bits) {
        assertArrayLength(bits);
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                if ((bits[i] & (1 << j)) != 0) {
                    bitset.set(i * 8 + j);
                }
            }
        }
    }
    private void toArray(byte[] bits) {
        assertArrayLength(bits);
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                if (bitset.get(i * 8 + j)) {
                    bits[i] |= (byte) (1 << j);
                }
            }
        }
    }

    public Extensions() {
    }
    public Extensions(byte @NotNull [] bits) {
        Objects.requireNonNull(bits, "Argument 'bits'");
        fromArray(bits);
    }
    public Extensions(@NotNull Extensions extensions) {
        Objects.requireNonNull(extensions, "Argument 'extensions'");
        synchronized (extensions.lock) {
            bitset.or(extensions.bitset);
            extensionMessages = extensions.extensionMessages == null ? null : extensions.extensionMessages;
            extensionTcpListenPort = extensions.extensionTcpListenPort;
            extensionClientVersion = extensions.extensionClientVersion;
            extensionYourIP = extensions.extensionYourIP;
            extensionIPv6 = extensions.extensionIPv6;
            extensionIPv4 = extensions.extensionIPv4;
            extensionMaxOutstandingRequests = extensions.extensionMaxOutstandingRequests;
        }
    }

    public byte @NotNull [] getBits() {
        var bits = new byte[8]; toArray(bits);
        return bits;
    }

    private int assertIndex(int index) {
        if (index < 0 || index >= 64) {
            throw new IndexOutOfBoundsException("Index out of bounds: " + index);
        }
        return index;
    }

    public boolean getBit(int index) {
        synchronized (lock) {
            return bitset.get(assertIndex(index));
        }
    }
    public void setBit(int index, boolean value) {
        synchronized (lock) {
            bitset.set(assertIndex(index), value);
        }
    }

    public boolean hasExtensionProtocol() {
        return getBit(44);
    }
    public void setExtensionProtocol(boolean value) {
        setBit(44, value);
    }

    public boolean hasFastPeers() {
        return getBit(58);
    }
    public void setFastPeers(boolean value) {
        setBit(58, value);
    }

    public boolean hasDht() {
        return getBit(56);
    }
    public void setDht(boolean value) {
        setBit(56, value);
    }

    public void fromHandshake(@NotNull BencodeElement source) {
        if (!source.getType().isMap()) {
            throw new IllegalArgumentException("Extension handshake is not a map");
        }
        var handshake = source.asMap();
        synchronized (lock) {
            var messagesValue = handshake.get("m");
            {
                if (messagesValue == null) {
                    throw new IllegalArgumentException("Extension handshake 'm' is missing");
                }
                if (!messagesValue.getType().isMap()) {
                    throw new IllegalArgumentException("Extension handshake 'm' is not a map");
                }
                var messages = messagesValue.asMap();
                var map = new TreeMap<String, Integer>();
                for (var entry : messages.entrySet()) {
                    var key = entry.getKey();
                    var value = entry.getValue();
                    if (!value.getType().isInteger()) {
                        throw new IllegalArgumentException("Extension handshake 'm' value is not an integer");
                    }
                    map.put(key.getValue(), (int) value.asInteger().getValue());
                }
                extensionMessages = map;
            }

            var tcpListenPortValue = handshake.get("p");
            if (tcpListenPortValue != null) {
                if (!tcpListenPortValue.getType().isInteger()) {
                    throw new IllegalArgumentException("Extension handshake 'p' is not an integer");
                }
                extensionTcpListenPort = (int) tcpListenPortValue.asInteger().getValue();
            }

            var clientVersionValue = handshake.get("v");
            if (clientVersionValue != null) {
                if (!clientVersionValue.getType().isString()) {
                    throw new IllegalArgumentException("Extension handshake 'v' is not a string");
                }
                extensionClientVersion = clientVersionValue.asString().getValue();
            }

            var yourIPValue = handshake.get("yourip");
            if (yourIPValue != null) {
                if (!yourIPValue.getType().isString()) {
                    throw new IllegalArgumentException("Extension handshake 'yourip' is not a string");
                }
                extensionYourIP = yourIPValue.asString().getBytes();
            }

            var ipv6Value = handshake.get("ipv6");
            if (ipv6Value != null) {
                if (!ipv6Value.getType().isString()) {
                    throw new IllegalArgumentException("Extension handshake 'ipv6' is not a string");
                }
                extensionIPv6 = ipv6Value.asString().getBytes();
            }

            var ipv4Value = handshake.get("ipv4");
            if (ipv4Value != null) {
                if (!ipv4Value.getType().isString()) {
                    throw new IllegalArgumentException("Extension handshake 'ipv4' is not a string");
                }
                extensionIPv4 = ipv4Value.asString().getBytes();
            }

            var maxOutstandingRequestsValue = handshake.get("reqq");
            if (maxOutstandingRequestsValue != null) {
                if (!maxOutstandingRequestsValue.getType().isInteger()) {
                    throw new IllegalArgumentException("Extension handshake 'reqq' is not an integer");
                }
                extensionMaxOutstandingRequests = (int) maxOutstandingRequestsValue.asInteger().getValue();
            }
        }
    }
    public @NotNull BencodeMap toHandshake() {
        var handshake = new BencodeMap();
        synchronized (lock) {
            if (extensionMessages != null) {
                var map = new BencodeMap(); extensionMessages.forEach(map::putInteger);
                handshake.put("m", map);
            } else {
                handshake.put("m", new BencodeMap());
            }
            if (extensionTcpListenPort != null) {
                handshake.putInteger("p", extensionTcpListenPort);
            }
            if (extensionClientVersion != null) {
                handshake.putString("v", extensionClientVersion);
            }
            if (extensionYourIP != null) {
                handshake.put("yourip", BencodeString.of(extensionYourIP));
            }
            if (extensionIPv6 != null) {
                handshake.put("ipv6", BencodeString.of(extensionIPv6));
            }
            if (extensionIPv4 != null) {
                handshake.put("ipv4", BencodeString.of(extensionIPv4));
            }
            if (extensionMaxOutstandingRequests != null) {
                handshake.putInteger("reqq", extensionMaxOutstandingRequests);
            }
        }
        return handshake;
    }

    public @Nullable Map<@NotNull String, @NotNull Integer> getExtensionMessages() {
        synchronized (lock) {
            return extensionMessages == null ? null : Collections.unmodifiableMap(extensionMessages);
        }
    }
    public void setExtensionMessages(@Nullable Map<@NotNull String, @NotNull Integer> extensionMessages) {
        synchronized (lock) {
            this.extensionMessages = extensionMessages == null ? null : new TreeMap<>(extensionMessages);
        }
    }

    public @Nullable Integer getExtensionTcpListenPort() {
        synchronized (lock) { return extensionTcpListenPort; }
    }
    public void setExtensionTcpListenPort(@Nullable Integer extensionTcpListenPort) {
        synchronized (lock) { this.extensionTcpListenPort = extensionTcpListenPort; }
    }

    public @Nullable String getExtensionClientVersion() {
        synchronized (lock) { return extensionClientVersion; }
    }
    public void setExtensionClientVersion(@Nullable String extensionClientVersion) {
        synchronized (lock) { this.extensionClientVersion = extensionClientVersion; }
    }

    public byte @Nullable [] getExtensionYourIP() {
        synchronized (lock) { return extensionYourIP; }
    }
    public void setExtensionYourIP(byte @Nullable [] extensionYourIP) {
        synchronized (lock) { this.extensionYourIP = extensionYourIP; }
    }

    public byte @Nullable [] getExtensionIPv6() {
        synchronized (lock) { return extensionIPv6; }
    }
    public void setExtensionIPv6(byte @Nullable [] extensionIPv6) {
        synchronized (lock) { this.extensionIPv6 = extensionIPv6; }
    }

    public byte @Nullable [] getExtensionIPv4() {
        synchronized (lock) { return extensionIPv4; }
    }
    public void setExtensionIPv4(byte @Nullable [] extensionIPv4) {
        synchronized (lock) { this.extensionIPv4 = extensionIPv4; }
    }

    public @Nullable Integer getExtensionMaxOutstandingRequests() {
        synchronized (lock) { return extensionMaxOutstandingRequests; }
    }
    public void setExtensionMaxOutstandingRequests(@Nullable Integer extensionMaxOutstandingRequests) {
        synchronized (lock) { this.extensionMaxOutstandingRequests = extensionMaxOutstandingRequests; }
    }

    @Override
    public @NotNull String toString() {
        var joiner = new StringJoiner(", ", getClass().getSimpleName() + "[", "]");
        synchronized (lock) {
            joiner.add("hasExtensionProtocol()=" + hasExtensionProtocol());
            joiner.add("hasFastPeers()=" + hasFastPeers());
            joiner.add("hasDht()=" + hasDht());
            for (int i = 0; i < 64; i++) {
                if (getBit(i)) {
                    joiner.add("bits[" + i + "]=true");
                }
            }
            if (hasExtensionProtocol()) {
                joiner.add("extensionHandshake=" + toHandshake());
            }
        }
        return joiner.toString();
    }

}
