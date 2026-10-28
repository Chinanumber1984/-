package vip.xiaonuo.srm.sourcing;
import java.util.concurrent.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.event.*;
import jakarta.annotation.PreDestroy;
@Component @RequiredArgsConstructor
public class SourcingRelay {
 private final SourcingStore x;private final SourcingAuctionService auctions;private final SourcingAuctionCache cache;private final SourcingSocketHub hub;
 private final ExecutorService executor=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"srm-sourcing-events");t.setDaemon(true);return t;});
 @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT) public void changed(SourcingStore.Changed e){executor.execute(()->dispatch(e.rfqId()));}
 private synchronized void dispatch(String rid){try{var r=x.trusted(rid,false);if(SourcingStore.s(r,"STRATEGY").equals("AUCTION")){var state=auctions.snapshotTrusted(rid);cache.sync(rid,state.version(),state.checkpoint(),state.minCents());hub.broadcast(auctions.snapshotTrusted(rid));}x.db.update("UPDATE SRM_SRC_EVENT SET STATUS='SENT' WHERE RFQ_ID=? AND VERSION<=?",rid,r.get("ROW_VERSION"));}catch(Exception ignored){x.db.update("UPDATE SRM_SRC_EVENT SET ATTEMPTS=ATTEMPTS+1 WHERE RFQ_ID=? AND STATUS='PENDING'",rid);}}
 @Scheduled(fixedDelay=1000) public void retry(){for(String rid:x.db.queryForList("SELECT DISTINCT RFQ_ID FROM SRM_SRC_EVENT WHERE STATUS='PENDING' ORDER BY RFQ_ID LIMIT 100",String.class))executor.execute(()->dispatch(rid));}
 @Scheduled(fixedDelay=1000) public void closeDue(){for(String rid:x.db.queryForList("SELECT ID FROM SRM_SRC_RFQ WHERE STATUS='PUBLISHED' AND DEADLINE_MS<=? ORDER BY DEADLINE_MS LIMIT 100",String.class,x.now()))try{auctions.close(rid);}catch(Exception ignored){/* Transaction rolls back; next cycle retries the persisted due aggregate. */}}
 @PreDestroy public void stop(){executor.shutdownNow();}
}
