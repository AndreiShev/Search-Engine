package searchengine.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.util.Date;

@Entity
@Table(name = "site")
@Setter
@Getter
public class Site {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "ENUM('INDEXING', 'INDEXED', 'FAILED')", nullable = false)
    private SiteStatus status;

    @Column(name = "status_time", columnDefinition = "DATETIME", nullable = false)
    private Date statusTime;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(columnDefinition = "VARCHAR(255)", nullable = false)
    private String url;

    @Column(columnDefinition = "VARCHAR(255)", nullable = false)
    private String name;

    public Site() {}

    public Site(SiteStatus status, Date statusTime, String lastError, String url, String name) {
        this.status = status;
        this.statusTime = statusTime;
        this.lastError = lastError;
        this.url = url;
        this.name = name;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this)
            return true;
        if (!(o instanceof Site))
            return false;
        Site other = (Site) o;

        boolean statusEquals = (this.status == null && other.status == null)
                || (this.status != null && this.status.equals(other.status));

        boolean lastError = (this.lastError == null && other.lastError == null)
                || (this.lastError != null && this.lastError.equals(other.lastError));

        boolean url = (this.url == null && other.url == null)
                || (this.url != null && this.url.equals(other.url));

        boolean name = (this.name == null && other.name == null)
                || (this.name != null && this.name.equals(other.name));

        boolean statusTime = (this.statusTime == null && other.statusTime == null)
                || (this.statusTime != null && this.statusTime.equals(other.statusTime));

        return statusEquals && lastError && url && name && statusTime;
    }
}
