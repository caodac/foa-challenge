package models;

import java.util.UUID;
import io.ebean.Finder;
import io.ebean.Model;

import javax.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

@Entity
public class Submission extends Model {
    public static final Finder<UUID, Submission> finder =
        new Finder<>(Submission.class);

    @Id public UUID id;
    public Long created;
    
    @Column(nullable=false)
    public Integer stage;
    
    @ManyToOne(cascade=CascadeType.ALL)
    @JsonIgnore
    public Participant participant;

    @Lob
    @Basic(fetch=FetchType.EAGER)
    @JsonIgnore
    public byte[] payload;
    public Integer psize;
    
    public Submission (Participant participant, Integer stage) {
        if (participant == null)
            throw new IllegalArgumentException ("Participant can't be null!");
        
        this.participant = participant;
        this.stage = stage;
        this.created = System.currentTimeMillis();
    }

    public Submission (Participant participant) {
        this (participant, participant.stage);
    }

    @JsonProperty("payload")
    public String getPayload () {
        if (payload != null && payload.length < 1024) {
            return new String (payload);
        }
        return null;
    }

    @JsonProperty("participant")
    public String getParticipant () {
        return participant.id.toString();
    }
}
