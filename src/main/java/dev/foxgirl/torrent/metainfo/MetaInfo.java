package dev.foxgirl.torrent.metainfo;

import dev.foxgirl.torrent.bencode.BencodeElement;
import dev.foxgirl.torrent.bencode.BencodeList;
import dev.foxgirl.torrent.bencode.BencodeMap;
import dev.foxgirl.torrent.util.Hash;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

public final class MetaInfo extends InfoContainer {

    public static @NotNull MetaInfo fromBencode(BencodeElement source) throws InvalidBencodeException {
        if (source == null) {
            throw new InvalidBencodeException("Metainfo is null");
        }
        if (!source.getType().isMap()) {
            throw new InvalidBencodeException("Metainfo is not a map");
        }

        var map = new BencodeMap(source.asMap());

        var infoValue = map.remove("info");
        if (infoValue == null) {
            throw new InvalidBencodeException("Metainfo info is missing");
        }

        var info = Info.fromBencode(infoValue);

        String announce = null;
        List<List<String>> announceList = null;
        List<String> urlList = null;
        List<InetSocketAddress> nodeList = null;
        String encoding = null;
        String comment = null;
        String createdBy = null;
        Instant creationDate = null;

        var announceValue = map.remove("announce");
        if (announceValue != null) {
            if (!announceValue.getType().isString()) {
                throw new InvalidBencodeException("Metainfo announce is not a string");
            }
            announce = announceValue.asString().getValue();
        }

        var announceListValue = map.remove("announce-list");
        if (announceListValue != null) {
            if (!announceListValue.getType().isList()) {
                throw new InvalidBencodeException("Metainfo announce-list is not a list");
            }
            announceList = new ArrayList<>();
            var innerList = new ArrayList<String>();
            for (var innerListValue : announceListValue.asList()) {
                if (!innerListValue.getType().isList()) {
                    throw new InvalidBencodeException("Metainfo announce-list element is not a list");
                }
                innerList.clear();
                for (var innerValue : innerListValue.asList()) {
                    if (!innerValue.getType().isString()) {
                        throw new InvalidBencodeException("Metainfo announce-list element element is not a string");
                    }
                    innerList.add(innerValue.asString().getValue());
                }
                announceList.add(List.copyOf(innerList));
            }
        }

        var urlListValue = map.remove("url-list");
        if (urlListValue != null) {
            if (!urlListValue.getType().isList()) {
                throw new InvalidBencodeException("Metainfo url-list is not a list");
            }
            urlList = new ArrayList<>();
            for (var urlValue : urlListValue.asList()) {
                if (!urlValue.getType().isString()) {
                    throw new InvalidBencodeException("Metainfo url-list element is not a string");
                }
                urlList.add(urlValue.asString().getValue());
            }
        }

        var nodeListValue = map.remove("nodes");
        if (nodeListValue != null) {
            if (!nodeListValue.getType().isList()) {
                throw new InvalidBencodeException("Metainfo nodes is not a list");
            }
            nodeList = new ArrayList<>();
            for (var nodeValue : nodeListValue.asList()) {
                if (!nodeValue.getType().isList()) {
                    throw new InvalidBencodeException("Metainfo nodes element is not a list");
                }
                if (nodeValue.asList().size() != 2) {
                    throw new InvalidBencodeException("Metainfo nodes element is not a list of size 2");
                }
                var addressValue = nodeValue.asList().get(0);
                if (!addressValue.getType().isString()) {
                    throw new InvalidBencodeException("Metainfo nodes element address is not a string");
                }
                var portValue = nodeValue.asList().get(1);
                if (!portValue.getType().isInteger()) {
                    throw new InvalidBencodeException("Metainfo nodes element port is not an integer");
                }
                var address = addressValue.asString().getValue();
                if (address.isEmpty() || address.isBlank()) {
                    throw new InvalidBencodeException("Metainfo nodes element address is empty");
                }
                var port = portValue.asInteger().getValue();
                if (port < 0 || port > 65535) {
                    throw new InvalidBencodeException("Metainfo nodes element port is out of range");
                }
                nodeList.add(new InetSocketAddress(address, (short) port));
            }
        }

        var encodingValue = map.remove("encoding");
        if (encodingValue != null) {
            if (!encodingValue.getType().isString()) {
                throw new InvalidBencodeException("Metainfo encoding is not a string");
            }
            encoding = encodingValue.asString().getValue();
        }

        var commentValue = map.remove("comment");
        if (commentValue != null) {
            if (!commentValue.getType().isString()) {
                throw new InvalidBencodeException("Metainfo comment is not a string");
            }
            comment = commentValue.asString().getValue();
        }

        var createdByValue = map.remove("created by");
        if (createdByValue != null) {
            if (!createdByValue.getType().isString()) {
                throw new InvalidBencodeException("Metainfo created by is not a string");
            }
            createdBy = createdByValue.asString().getValue();
        }

        var creationDateValue = map.remove("creation date");
        if (creationDateValue != null) {
            if (!creationDateValue.getType().isInteger()) {
                throw new InvalidBencodeException("Metainfo creation date is not an integer");
            }
            creationDate = Instant.ofEpochSecond(creationDateValue.asInteger().getValue());
        }

        return new MetaInfo(info, announce, announceList, urlList, nodeList, encoding, comment, createdBy, creationDate, map);
    }

    private final @NotNull Info info;

    private final @Nullable String announce;

    private final @NotNull List<@NotNull List<@NotNull String>> announceList;
    private final @NotNull List<@NotNull String> announceListFlat;

    private final @NotNull List<@NotNull String> urlList;

    private final @NotNull List<@NotNull InetSocketAddress> nodeList;

    private final @Nullable String encoding;

    private final @Nullable String comment;
    private final @Nullable String createdBy;
    private final @Nullable Instant creationDate;

    public MetaInfo(
            @NotNull Info info,
            @Nullable String announce,
            @Nullable List<@NotNull List<@NotNull String>> announceList,
            @Nullable List<@NotNull String> urlList,
            @Nullable List<@NotNull InetSocketAddress> nodeList,
            @Nullable String encoding,
            @Nullable String comment,
            @Nullable String createdBy,
            @Nullable Instant creationDate,
            @Nullable BencodeMap extraFields
    ) {
        super(extraFields);

        Objects.requireNonNull(info, "Argument 'info'");

        this.info = info;

        if (announce != null) {
            if (announce.isEmpty()) {
                throw new IllegalArgumentException("Announce is empty");
            }
            this.announce = announce;
        } else {
            this.announce = null;
        }

        if (announceList != null) {
            announceList = List.copyOf(announceList);
            this.announceList = announceList.stream().map(List::copyOf).toList();
            this.announceListFlat = announceList.stream().flatMap(List::stream).toList();
        } else {
            this.announceList = List.of();
            this.announceListFlat = List.of();
        }

        if (urlList != null) {
            this.urlList = List.copyOf(urlList);
        } else {
            this.urlList = List.of();
        }

        if (nodeList != null) {
            this.nodeList = List.copyOf(nodeList);
        } else {
            this.nodeList = List.of();
        }

        this.encoding = encoding;
        this.comment = comment;
        this.createdBy = createdBy;
        this.creationDate = creationDate;
    }

    @Override
    public @NotNull BencodeMap toBencode() {
        var map = getExtraFields();

        map.put("info", info.toBencode());

        if (announce != null) {
            map.putString("announce", announce);
        }

        if (!announceList.isEmpty()) {
            var list = new BencodeList();
            for (var announceListElement : announceList) {
                var innerList = new BencodeList();
                for (var innerListElement : announceListElement) {
                    innerList.addString(innerListElement);
                }
                list.add(innerList);
            }
            map.put("announce-list", list);
        }

        if (!urlList.isEmpty()) {
            var list = new BencodeList();
            for (var urlListElement : urlList) {
                list.addString(urlListElement);
            }
            map.put("url-list", list);
        }

        if (!nodeList.isEmpty()) {
            var list = new BencodeList();
            for (var nodeListElement : nodeList) {
                var node = new BencodeList();
                node.addString(nodeListElement.getAddress().getHostAddress());
                node.addInteger(nodeListElement.getPort());
                list.add(node);
            }
            map.put("nodes", list);
        }

        if (encoding != null) {
            map.putString("encoding", encoding);
        }
        if (comment != null) {
            map.putString("comment", comment);
        }
        if (createdBy != null) {
            map.putString("created by", createdBy);
        }
        if (creationDate != null) {
            map.putInteger("creation date", creationDate.getEpochSecond());
        }

        return map;
    }

    public @NotNull Info getInfo() {
        return info;
    }
    public @NotNull Hash getInfoHash() {
        return info.getInfoHash();
    }

    public @Nullable String getAnnounce() {
        return announce;
    }

    public @NotNull List<@NotNull List<@NotNull String>> getAnnounceList() {
        return announceList;
    }
    public @NotNull List<@NotNull String> getAnnounceListFlat() {
        return announceListFlat;
    }

    public @NotNull List<@NotNull String> getUrlList() {
        return urlList;
    }

    public @NotNull List<@NotNull InetSocketAddress> getNodeList() {
        return nodeList;
    }

    public @Nullable String getEncoding() {
        return encoding;
    }

    public @Nullable String getComment() {
        return comment;
    }
    public @Nullable String getCreatedBy() {
        return createdBy;
    }
    public @Nullable Instant getCreationDate() {
        return creationDate;
    }

    @Override
    public @NotNull String toString() {
        return new StringJoiner(", ", "MetaInfo{", "}")
                .add("info=" + info)
                .add(announce == null ? "announce=null" : "announce='" + announce + "'")
                .add("announceList=" + announceList)
                .add("urlList=" + urlList)
                .add("nodeList=" + nodeList)
                .add(encoding == null ? "encoding=null" : "encoding='" + encoding + "'")
                .add(comment == null ? "comment=null" : "comment='" + comment + "'")
                .add(createdBy == null ? "createdBy=null" : "createdBy='" + createdBy + "'")
                .add("creationDate=" + creationDate)
                .toString();
    }

}
