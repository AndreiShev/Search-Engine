package searchengine.model;


import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Entity
@Setter
@Getter
@Table(name = "word_index")
public class Index {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @ManyToOne(cascade = CascadeType.MERGE, fetch = FetchType.LAZY)
    @JoinColumn(name = "page_id")
    private Page pageId;

    @ManyToOne(cascade = CascadeType.MERGE, fetch = FetchType.LAZY)
    @JoinColumn(name = "lemma_id")
    private Lemma lemmaId;

    @Column(name = "word_rank")
    private float wordRank;

    public Index() {}

    public Index(Page pageId, Lemma lemmaId, float wordRank) {
        this.pageId = pageId;
        this.lemmaId = lemmaId;
        this.wordRank = wordRank;
    }



    @Override
    public boolean equals(Object o) {
        if (o == this)
            return true;
        if (!(o instanceof Index))
            return false;

        Index other = (Index) o;

        boolean id = this.id == other.id;

        boolean pageId = (this.pageId == null && other.pageId == null)
                || (this.pageId != null && this.pageId.equals(other.pageId));

        boolean lemmaId = (this.lemmaId == null && other.lemmaId == null)
                || (this.lemmaId != null && this.lemmaId.equals(other.lemmaId));

        boolean rank = this.wordRank == other.wordRank;

        return id && pageId && lemmaId && rank;
    }
}
