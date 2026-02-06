package com.mycompany.smppclient.config;

import java.io.InputStream;
import java.util.Properties;

public class SmppProperties {

    public final String host;
    public final int port;
    public final String systemId;
    public final String password;
    public final String systemType;
    public final byte interfaceVersion;
    public final byte addrTon;
    public final byte addrNpi;
    public final String addressRange;

    public final String dbUrl;
    public final String dbUser;
    public final String dbPassword;

    private SmppProperties(Properties p) {
        this.host = req(p, "smpp.host");
        this.port = Integer.parseInt(req(p, "smpp.port"));
        this.systemId = req(p, "smpp.systemId");
        this.password = req(p, "smpp.password");
        this.systemType = req(p, "smpp.systemType");
        this.interfaceVersion = parseByte(p, "smpp.interfaceVersion");
        this.addrTon = (byte) Integer.parseInt(req(p, "smpp.addrTon"));
        this.addrNpi = (byte) Integer.parseInt(req(p, "smpp.addrNpi"));
        this.addressRange = opt(p, "smpp.addressRange", "");

        this.dbUrl = req(p, "db.url");
        this.dbUser = req(p, "db.user");
        this.dbPassword = req(p, "db.password");
    }

    public static SmppProperties loadFromTestResources() {
        try (InputStream in = SmppProperties.class.getClassLoader().getResourceAsStream("smpp.properties")) {
            if (in == null) throw new RuntimeException("smpp.properties not found in classpath (put it under src/test/resources)");
            Properties p = new Properties();
            p.load(in);
            return new SmppProperties(p);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load smpp.properties: " + e.getMessage(), e);
        }
    }

    private static String req(Properties p, String key) {
        String v = p.getProperty(key);
        if (v == null || v.trim().isEmpty()) throw new IllegalArgumentException("Missing property: " + key);
        return v.trim();
    }

    private static String opt(Properties p, String key, String def) {
        String v = p.getProperty(key);
        return (v == null) ? def : v.trim();
    }

    private static byte parseByte(Properties p, String key) {
        String v = req(p, key).toLowerCase();
        if (v.startsWith("0x")) {
            return (byte) Integer.parseInt(v.substring(2), 16);
        }
        return (byte) Integer.parseInt(v);
    }
}
