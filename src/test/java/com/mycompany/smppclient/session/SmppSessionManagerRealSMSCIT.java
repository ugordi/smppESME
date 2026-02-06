package com.mycompany.smppclient.session;

import com.mycompany.smppclient.config.SmppProperties;
import com.mycompany.smppclient.pdu.BindTransceiverReq;
import com.mycompany.smppclient.socket.SmppSocketClient;
import com.mycompany.smppclient.socket.SmppSocketConfig;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("real")
public class SmppSessionManagerRealSMSCIT {

    @Test
    void realSMSC_bind_enquireLink_unbind_ok() throws Exception {
        SmppProperties p = SmppProperties.loadFromTestResources();

        SmppSocketConfig sockCfg = new SmppSocketConfig(
                5000, // connect timeout
                5000, // read timeout
                1,    // reconnect attempts
                500   // backoff
        );

        try (SmppSocketClient socket = new SmppSocketClient(sockCfg, null)) {
            SmppSessionConfig cfg = new SmppSessionConfig(
                    5000, // response timeout
                    2000  // enquire_link interval (2sn)
            );

            SmppSessionManager sm = new SmppSessionManager(socket, cfg);

            BindTransceiverReq bindReq = new BindTransceiverReq();
            bindReq.setSystemId(p.systemId);
            bindReq.setPassword(p.password);
            bindReq.setSystemType(p.systemType);
            bindReq.setInterfaceVersion(p.interfaceVersion);
            bindReq.setAddrTon(p.addrTon);
            bindReq.setAddrNpi(p.addrNpi);
            bindReq.setAddressRange(p.addressRange);

            System.out.println("[REAL] Connecting to " + p.host + ":" + p.port);
            boolean bound = sm.bind(p.host, p.port, bindReq);
            System.out.println("[REAL] bound=" + bound);

            assertTrue(bound);
            assertTrue(sm.isBound());

            sm.startEnquireLinkTask();

            // 1-2 kere enquire_link dönsün diye
            Thread.sleep(4500);

            int ok = sm.getEnquireOkCount();
            System.out.println("[REAL] enquireOkCount=" + ok);
            assertTrue(ok >= 1, "en az 1 enquire_link_resp gelmeli");

            boolean unbound = sm.unbind();
            System.out.println("[REAL] unbound=" + unbound);

            assertTrue(unbound);
            assertFalse(sm.isBound());

            sm.close();
        }
    }
}
