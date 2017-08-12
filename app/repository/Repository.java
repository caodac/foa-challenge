package repository;

import javax.persistence.*;
import io.ebean.Ebean;
import io.ebean.EbeanServer;
import io.ebean.Transaction;
import io.ebean.Expr;

import models.Participant;
import models.Submission;

import play.db.ebean.EbeanConfig;
import play.Logger;
import play.libs.Json;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.nio.file.Files;
import java.io.File;

import com.fasterxml.jackson.databind.JsonNode;
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
                try (Transaction tx = ebeanServer.beginTransaction()) {
                    part.created = System.currentTimeMillis();
                    part.save();
                    tx.commit();
                    return part.id;
                }
            }, executionContext);
    }

    public CompletionStage<UUID> insert (Submission submission) {
        return supplyAsync(() -> {
                try (Transaction tx = ebeanServer.beginTransaction()) {
                    submission.save();
                    tx.commit();
                    return submission.id;
                }
            }, executionContext);
    }

    public Participant _insertIfAbsent (Participant part) {
        try (Transaction tx = part.db().beginTransaction()) {
            Participant p = Participant.finder.query()
                .where().eq("email", part.email).findUnique();
            if (p == null) {
                part.created = System.currentTimeMillis();
                p = part;
                part.save();
                tx.commit();
            }
            else if (part.name != null
                     && !part.name.equals(p.name)) {
                p.updated = System.currentTimeMillis();
                p.name = part.name;
                p.update();
                tx.commit();
            }
            return p;
        }
    }
    
    public CompletionStage<Participant> insertIfAbsent (Participant part) {
        return supplyAsync(() -> {
                int tries = 0;
                do {
                    try {
                        return _insertIfAbsent (part);
                    }
                    catch (RollbackException ex) {
                        Logger.warn(ex.getMessage()+": retry "+tries);
                    }
                }
                while (++tries < 5);
                
                return null;
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

    public Participant incrementStage (Participant part) {
        try (Transaction tx = part.db().beginTransaction()) {
            tx.setBatchMode(false);
            part.stage = part.stage+1;
            part.updated = System.currentTimeMillis();
            part.update();
            tx.commit();
            return part;
        }
    }

    public CompletionStage<Participant> nextStage (Participant part) {
        return supplyAsync (() -> {
                int tries = 0;
                do {
                    try {
                        return incrementStage(part);
                    }
                    catch (RollbackException ex) {
                        Logger.warn(ex.getMessage()+": retry "+tries);
                    }
                }
                while (++tries < 5);
                
                return null;
            }, executionContext);
    }

    public CompletionStage<List<Participant>> participants () {
        return supplyAsync (() -> {
                return Participant.finder.all();
            }, executionContext);
    }

    public CompletionStage<Submission> submission
        (Participant part, Object data) {
        return submission (part, Json.toJson(data));
    }
    
    public CompletionStage<Submission> submission
        (Participant part, JsonNode json) {
        return submission (part, Json.stringify(json));
    }

    Submission createSubmission (Participant part, String payload) {
        Submission sub = new Submission (part);
        try {
            sub.payload = payload.getBytes("utf8");
            sub.psize = sub.payload.length;
        }
        catch (Exception ex) {
            Logger.error("Can't convert byte payload: "+payload, ex);
        }
        return sub;
    }
    
    public int _submissionCount (Participant part) {
        try (Transaction tx = part.db().beginTransaction()) {
            tx.setReadOnly(true);
            return Submission.finder.query().where()
                .eq("participant", part).findCount();
        }
    }

    public CompletionStage<Integer> submissionCount (Participant part) {
        return supplyAsync (() -> {
                return _submissionCount (part);
            }, executionContext);
    }
    
    public CompletionStage<Submission> submission
        (Participant part, String payload) {
        return supplyAsync (() -> {
                int tries = 0;
                Submission sub = createSubmission (part, payload);
                do {
                    try (Transaction tx = part.db().beginTransaction()) {
                        tx.setBatchMode(false);
                        sub.save();
                        tx.commit();
                        return sub;
                    }
                    catch (RollbackException ex) {
                        Logger.warn(ex.getMessage()+": retry "+tries);
                    }
                }
                while (++tries < 5);
                
                return null;
            }, executionContext);
    }

    Submission createSubmission (Participant part, File payload) {
        Submission sub = new Submission (part);
        try {
            sub.payload = Files.readAllBytes(payload.toPath());
            sub.psize = sub.payload.length;
        }
        catch (Exception ex) {
            Logger.error("Can't ready payload: "+payload, ex);
        }
        return sub;
    }   

    public CompletionStage<Submission> submission
        (Participant part, File payload) {
        return supplyAsync (() -> {
                int tries = 0;
                Submission sub = createSubmission (part, payload);
                do {
                    try (Transaction tx = part.db().beginTransaction()) {
                        tx.setBatchMode(false);
                        sub.save();
                        tx.commit();
                        return sub;
                    }
                    catch (RollbackException ex) {
                        Logger.warn(ex.getMessage()+": retry "+tries);
                    }
                }
                while (++tries < 5);
                
                return null;
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
