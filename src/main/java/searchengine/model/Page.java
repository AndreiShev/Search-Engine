package searchengine.model;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Cascade;

import javax.persistence.*;
import javax.persistence.Index;

@Entity
@Table(name = "page",
        indexes = {@Index(name = "Page_table_path_ind",  columnList="path", unique = true)})
@Setter
@Getter
public class Page {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @ManyToOne(cascade = CascadeType.MERGE, fetch = FetchType.EAGER)
    @JoinColumn(name = "site_id")
    private Site siteId;

    @Column(name = "path", nullable = false, columnDefinition = "TEXT")
    private String path;

    @Column(name = "code", nullable = false)
    private int code;

    @Column(name = "content", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String content;

    public Page() {}

    public Page(Site siteId, String path, int code, String content) {
        this.siteId = siteId;
        this.path = path;
        this.code = code;
        this.content = content;
    }

    public Page(String path, int code, String content) {
        this.path = path;
        this.code = code;
        this.content = content;
    }

    public Page(Integer id, int code, String path) {
        this.id = id;
        this.path = path;
        this.code = code;
    }


    @Override
    public boolean equals(Object o) {
        if (o == this)
            return true;
        if (!(o instanceof Page))
            return false;
        Page other = (Page) o;

        boolean id = this.id == other.id;

        boolean siteId = (this.siteId == null && other.siteId == null)
                || (this.siteId != null && this.siteId.equals(other.siteId));

        boolean path = (this.path == null && other.path == null)
                || (this.path != null && this.path.equals(other.path));

        boolean code = this.code == other.code;

        boolean content = (this.content == null && other.content == null)
                || (this.content != null && this.content.equals(other.content));

        return id && siteId && path && code && content;
    }
}