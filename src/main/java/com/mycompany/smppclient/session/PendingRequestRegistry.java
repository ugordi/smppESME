package com.mycompany.smppclient.session;

import com.mycompany.smppclient.pdu.Pdu;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class PendingRequestRegistry {

    private final Map<Integer, CompletableFuture<Pdu>> pending = new ConcurrentHashMap<>();

    public CompletableFuture<Pdu> register(int seq) {
        CompletableFuture<Pdu> f = new CompletableFuture<>();
        pending.put(seq, f);
        return f;
    }

    public boolean complete(int seq, Pdu pdu) {
        CompletableFuture<Pdu> f = pending.remove(seq);
        if (f == null) return false;
        f.complete(pdu);
        return true;
    }

    public void fail(int seq, Throwable t) {
        CompletableFuture<Pdu> f = pending.remove(seq);
        if (f != null) {
            f.completeExceptionally(t);
        }
    }

    public void clearAll(Throwable t) {
        for (CompletableFuture<Pdu> f : pending.values()) {
            f.completeExceptionally(t);
        }
        pending.clear();
    }
}
