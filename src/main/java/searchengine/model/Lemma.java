package searchengine.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Cascade;

import javax.persistence.*;
import java.util.List;

@Entity
@Table(name = "lemma")
@Setter
@Getter
public class Lemma {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @ManyToOne(cascade = CascadeType.MERGE, fetch = FetchType.EAGER)
    @JoinColumn(name = "site_id")
    private Site siteId;

    @Column(columnDefinition = "VARCHAR(255)", nullable = false)
    private String lemma;

    @Column(nullable = false)
    private int frequency;

    public Lemma() {}

    public Lemma(Site siteId, String lemma, int frequency) {
        this.siteId = siteId;
        this.lemma = lemma;
        this.frequency = frequency;
    }
/*
    @Override
    public boolean equals(Object o) {
        if (o == this)
            return true;
        if (!(o instanceof Lemma))
            return false;
        Lemma other = (Lemma) o;

        boolean id = this.id == other.id;

        boolean siteId = (this.siteId == null && other.siteId == null)
                || (this.siteId != null && this.siteId.equals(other.siteId));

        boolean lemma = (this.lemma == null && other.lemma == null)
                || (this.lemma != null && this.lemma.equals(other.lemma));

        boolean frequency = this.frequency == other.frequency;

        return siteId && id && lemma && frequency;
    }*/
}
