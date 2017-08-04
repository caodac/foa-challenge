package repository;
import io.ebean.Ebean;
import io.ebean.EbeanServer;
import io.ebean.Transaction;
import models.Participant;
import play.db.ebean.EbeanConfig;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

import static java.util.concurrent.CompletableFuture.supplyAsync;

/**
 * A repository that executes database operations in a different
 * execution context.
 */
@Singleton
public class ParticipantRepository {

    private final EbeanServer ebeanServer;
    private final DatabaseExecutionContext executionContext;

    @Inject
    public ParticipantRepository (EbeanConfig ebeanConfig,
                                  DatabaseExecutionContext executionContext) {
        this.ebeanServer = Ebean.getServer(ebeanConfig.defaultServer());
        this.executionContext = executionContext;
    }

    public CompletionStage<UUID> insert (Participant part) {
        return supplyAsync(() -> {
                part.created = System.currentTimeMillis();
                part.save();
                return part.id;
            }, executionContext);
    }
    
    public CompletionStage<Participant> fetch (UUID id) {
        return supplyAsync (() -> {
                return Participant.finder.byId(id);
            }, executionContext);
    }

    public CompletionStage<Participant> fetch (String email) {
        return supplyAsync (() -> {
                return Participant.finder.query()
                    .where().eq("email", email).findUnique();
            }, executionContext);
    }

    public Optional<Participant> incrementStage(Participant part) {
        Optional<Participant> ret = Optional.empty();
        Transaction tx = ebeanServer.beginTransaction();
        try {
            part.stage = part.stage+1;
            part.updated = System.currentTimeMillis();
            part.update();
            tx.commit();
            ret = Optional.of(part);
        }
        finally {
            tx.end();
        }
        return ret;
    }

    public CompletionStage<Optional<Participant>> nextStage (Participant part) {
        return supplyAsync (() -> {
                return incrementStage(part);
            }, executionContext);
    }

    public CompletionStage<List<Participant>> list () {
        return supplyAsync (() -> {
                return Participant.finder.all();
            }, executionContext);
    }
}
