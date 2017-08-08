package repository;

import io.ebean.Ebean;
import io.ebean.EbeanServer;
import io.ebean.Transaction;
import io.ebean.Expr;

import models.Participant;
import models.Submission;

import play.db.ebean.EbeanConfig;
import play.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.nio.file.Files;
import java.io.File;

import static java.util.concurrent.CompletableFuture.supplyAsync;

/**
 * A repository that executes database operations in a different
 * execution context.
 */
@Singleton
public class Repository {

    private final EbeanServer ebeanServer;
    private final DatabaseExecutionContext executionContext;

    @Inject
    public Repository (EbeanConfig ebeanConfig,
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

    public CompletionStage<Long> insert (Submission submission) {
        return supplyAsync(() -> {
                submission.save();
                return submission.id;
            }, executionContext);
    }
    
    public CompletionStage<Participant> fetchParticipant (UUID id) {
        return supplyAsync (() -> {
                return Participant.finder.byId(id);
            }, executionContext);
    }

    public CompletionStage<Participant> fetchParticipant (String email) {
        return supplyAsync (() -> {
                return Participant.finder.query()
                    .where().eq("email", email).findUnique();
            }, executionContext);
    }

    public Optional<Participant> incrementStage (Participant part) {
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

    public CompletionStage<List<Participant>> participants () {
        return supplyAsync (() -> {
                return Participant.finder.all();
            }, executionContext);
    }

    public CompletionStage<Submission> submission
        (Participant part, String payload) {
        return supplyAsync (() -> {
                try {
                    Submission sub = new Submission (part);
                    sub.payload = payload.getBytes("utf8");
                    sub.psize = sub.payload.length;
                    sub.save();
                    return sub;
                }
                catch (Exception ex) {
                    Logger.error("Failed to persist submission", ex);
                    return null;
                }
            }, executionContext);
    }

    public CompletionStage<Submission> submission
        (Participant part, File payload) {
        return supplyAsync (() -> {
                try {
                    Submission sub = new Submission (part);
                    sub.payload = Files.readAllBytes(payload.toPath());
                    sub.psize = sub.payload.length;
                    sub.save();
                    return sub;
                }
                catch (Exception ex) {
                    Logger.error("Failed to read file "+payload, ex);
                    return null;
                }
            }, executionContext);
    }

    public CompletionStage<List<Submission>> submissions (Participant part) {
        return submissions (part, null);
    }
    
    public CompletionStage<List<Submission>> submissions
        (Participant part, Integer stage) {
        return supplyAsync (() -> {
                if (stage != null) {
                    return Submission.finder.query()
                        .where(Expr.and(Expr.eq("participant", part),
                                        Expr.eq("stage", stage)))
                        .findList();
                }
                else {
                    return Submission.finder.query().where()
                        .eq("participant", part).findList();
                }
            }, executionContext);
    }
}
