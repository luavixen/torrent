package dev.foxgirl.torrent.client;

import dev.foxgirl.torrent.bencode.BencodeElement;
import dev.foxgirl.torrent.bencode.BencodeMap;
import dev.foxgirl.torrent.bencode.BencodeString;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class Extensions {

    private static final Extensions SUPPORTED_EXTENSIONS;
    static {
        Extensions extensions = new Extensions();
        extensions.setFastPeers(true);
        extensions.setExtensionProtocol(true);
        extensions.setExtensionMessages(Map.of(
                "ut_metadata", 10,
                "lt_donthave", 20
        ));
        extensions.setExtensionClientVersion("https://github.com/luavixen/torrent");
        extensions.setExtensionMaxOutstandingRequests(100);
        SUPPORTED_EXTENSIONS = extensions.copyImmutable();
    }

    public static @NotNull Extensions getSupportedExtensions() {
        return new Extensions(SUPPORTED_EXTENSIONS);
    }

    private final BitSet bitset = new BitSet(64);

    private @Nullable Map<@NotNull String, @NotNull Integer> extensionMessages;
    private @Nullable Integer extensionTcpListenPort;
    private @Nullable String extensionClientVersion;
    private byte @Nullable [] extensionYourIP;
    private byte @Nullable [] extensionIPv6;
    private byte @Nullable [] extensionIPv4;
    private @Nullable Integer extensionMaxOutstandingRequests;

    private final boolean immutable;

    public Extensions() {
        this.immutable = false;
    }

    public Extensions(byte @NotNull [] bits) {
        this(bits, false);
    }
    public Extensions(byte @NotNull [] bits, boolean immutable) {
        Objects.requireNonNull(bits, "Argument 'bits'");
        readBitsFromArray(bits);
        this.immutable = immutable;
    }

    public Extensions(@NotNull Extensions extensions) {
        this(extensions, false);
    }
    public Extensions(@NotNull Extensions extensions, boolean immutable) {
        Objects.requireNonNull(extensions, "Argument 'extensions'");
        synchronized (extensions) {
            bitset.or(extensions.bitset);
            extensionMessages = extensions.extensionMessages == null ? null : extensions.extensionMessages;
            extensionTcpListenPort = extensions.extensionTcpListenPort;
            extensionClientVersion = extensions.extensionClientVersion;
            extensionYourIP = extensions.extensionYourIP;
            extensionIPv6 = extensions.extensionIPv6;
            extensionIPv4 = extensions.extensionIPv4;
            extensionMaxOutstandingRequests = extensions.extensionMaxOutstandingRequests;
        }
        this.immutable = immutable;
    }

    public @NotNull Extensions copyMutable() {
        return new Extensions(this, false);
    }
    public @NotNull Extensions copyImmutable() {
        return immutable ? this : new Extensions(this, true);
    }

    private static void assertBitsArrayLength(byte[] bits) {
        if (bits.length != 8) {
            throw new IllegalArgumentException("Length of bits array is not 8");
        }
    }

    private void readBitsFromArray(byte[] bits) {
        assertBitsArrayLength(bits);
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                if ((bits[i] & (1 << j)) != 0) {
                    bitset.set(i * 8 + j);
                }
            }
        }
    }
    private void writeBitsToArray(byte[] bits) {
        assertBitsArrayLength(bits);
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                if (bitset.get(i * 8 + j)) {
                    bits[i] |= (byte) (1 << j);
                }
            }
        }
    }

    public synchronized byte @NotNull [] getBits() {
        var bits = new byte[8];
        writeBitsToArray(bits);
        return bits;
    }

    private static int assertIndex(int index) {
        if (index < 0 || index >= 64) {
            throw new IndexOutOfBoundsException("Index out of bounds: " + index);
        }
        return index;
    }

    private void assertMutable() {
        if (immutable) {
            throw new UnsupportedOperationException("Extensions instance is immutable");
        }
    }

    public synchronized boolean getBit(int index) {
        return bitset.get(assertIndex(index));
    }
    public synchronized void setBit(int index, boolean value) {
        assertMutable();
        bitset.set(assertIndex(index), value);
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

    public synchronized void fromHandshake(@NotNull BencodeElement source) {
        assertMutable();

        if (!source.getType().isMap()) {
            throw new IllegalArgumentException("Extension handshake is not a map");
        }
        var handshake = source.asMap();

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
            extensionMessages = Collections.unmodifiableMap(map);
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

    public synchronized @NotNull BencodeMap toHandshake() {
        var handshake = new BencodeMap();
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
        return handshake;
    }

    public synchronized @Nullable Map<@NotNull String, @NotNull Integer> getExtensionMessages() {
        return extensionMessages == null ? null : Collections.unmodifiableMap(extensionMessages);
    }
    public synchronized void setExtensionMessages(@Nullable Map<@NotNull String, @NotNull Integer> extensionMessages) {
        assertMutable();
        this.extensionMessages = extensionMessages == null ? null : Collections.unmodifiableMap(new TreeMap<>(extensionMessages));
    }

    public synchronized @Nullable Integer getExtensionTcpListenPort() {
        return extensionTcpListenPort;
    }
    public synchronized void setExtensionTcpListenPort(@Nullable Integer extensionTcpListenPort) {
        assertMutable();
        this.extensionTcpListenPort = extensionTcpListenPort;
    }

    public synchronized @Nullable String getExtensionClientVersion() {
        return extensionClientVersion;
    }
    public synchronized void setExtensionClientVersion(@Nullable String extensionClientVersion) {
        assertMutable();
        this.extensionClientVersion = extensionClientVersion;
    }

    public synchronized byte @Nullable [] getExtensionYourIP() {
        return extensionYourIP;
    }
    public synchronized void setExtensionYourIP(byte @Nullable [] extensionYourIP) {
        assertMutable();
        this.extensionYourIP = extensionYourIP;
    }

    public synchronized byte @Nullable [] getExtensionIPv6() {
        return extensionIPv6;
    }
    public synchronized void setExtensionIPv6(byte @Nullable [] extensionIPv6) {
        assertMutable();
        this.extensionIPv6 = extensionIPv6;
    }

    public synchronized byte @Nullable [] getExtensionIPv4() {
        return extensionIPv4;
    }
    public synchronized void setExtensionIPv4(byte @Nullable [] extensionIPv4) {
        assertMutable();
        this.extensionIPv4 = extensionIPv4;
    }

    public synchronized @Nullable Integer getExtensionMaxOutstandingRequests() {
        return extensionMaxOutstandingRequests;
    }
    public synchronized void setExtensionMaxOutstandingRequests(@Nullable Integer extensionMaxOutstandingRequests) {
        assertMutable();
        this.extensionMaxOutstandingRequests = extensionMaxOutstandingRequests;
    }

    @Override
    public synchronized @NotNull String toString() {
        var joiner = new StringJoiner(", ", getClass().getSimpleName() + "[", "]");
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
        return joiner.toString();
    }

}
