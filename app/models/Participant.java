package models;

import java.util.UUID;
import javax.persistence.*;
import java.sql.Timestamp;
import io.ebean.Model;
import io.ebean.Finder;

@Entity
public class Participant extends Model {
    public static final Finder<UUID, Participant> finder =
        new Finder<>(Participant.class);
    
    @Id public UUID id;
    @Version Long version;
    final Long created = System.currentTimeMillis();
    
    @Column(nullable=false,unique=true)
    public String email;
    public String firstname;
    public String lastname;

    public Integer stage;

    public Participant () {
    }
}
