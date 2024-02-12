package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;

import java.util.List;

@Repository
public interface LemmaRepository extends JpaRepository<Lemma, Integer> {
    Lemma findByLemma(String lemma);

    Lemma findByLemmaAndSiteId(String lemma, Site site);

    int countAllBySiteId(Site site);

    @Modifying
    @Transactional
    void deleteBySiteId(Site site);

    @Query(value = "SELECT l FROM Lemma l WHERE l.siteId =?1")
    List<Lemma> getListLemmaBySite(Site id);
}
