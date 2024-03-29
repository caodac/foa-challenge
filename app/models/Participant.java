package models;

import io.ebean.Finder;
import io.ebean.Model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Version;
import java.util.UUID;

@Entity
public class Participant extends Model {
    public static final Finder<UUID, Participant> finder =
        new Finder<>(Participant.class);
    
    @Id public UUID id;
    @Version Long version;
    public Long created;
    public Long updated;
    
    @Column(nullable=false,unique=true,length=128)
    public String email;
    @Column(length=128)
    public String name;

    @Column(nullable=false)
    public Integer stage;

    public Participant () {
    }
}
