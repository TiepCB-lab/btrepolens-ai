package com.btrepolens.chat;

import com.btrepolens.common.AppException;
import org.springframework.http.HttpStatus;

/** SQL predicate used while the session row is locked by the enclosing write. */
public final class ChatTurnFence {
    private ChatTurnFence() {}
    public static final String HELD_LOCK = """
        EXISTS (SELECT 1 FROM pg_locks l WHERE l.locktype='advisory' AND l.granted AND l.pid=?
          AND l.database=(SELECT oid FROM pg_database WHERE datname=current_database()) AND l.objsubid=2
          AND l.classid::bigint=(((s.id >> 32) # %d) & 4294967295)
          AND l.objid::bigint=(s.id & 4294967295))
        """.formatted(ChatTurnLock.NAMESPACE);

    public static AppException superseded() {
        return new AppException(HttpStatus.CONFLICT, "SESSION_SUPERSEDED",
            "This response was interrupted. Your message may already be saved; check the conversation before sending it again.");
    }
}
