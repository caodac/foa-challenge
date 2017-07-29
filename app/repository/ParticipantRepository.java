package repository;
import models.Participant;

import io.ebean.Ebean;
import io.ebean.EbeanServer;
import io.ebean.PagedList;
import io.ebean.Transaction;
import play.db.ebean.EbeanConfig;

import javax.inject.Inject;
import java.util.UUID;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

import static java.util.concurrent.CompletableFuture.supplyAsync;

/**
 * A repository that executes database operations in a different
 * execution context.
 */
public class ParticipantRepository {

    private final EbeanServer ebeanServer;
    private final DatabaseExecutionContext executionContext;

    @Inject
    public ParticipantRepository (EbeanConfig ebeanConfig,
                                  DatabaseExecutionContext executionContext) {
        this.ebeanServer = Ebean.getServer(ebeanConfig.defaultServer());
        this.executionContext = executionContext;
    }

    public CompletionStage<UUID> insert (Participant participant) {
        return supplyAsync(() -> {
             ebeanServer.insert(participant);
             return participant.id;
        }, executionContext);
    }
}
