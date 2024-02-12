package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;

import java.util.List;
import java.util.Set;

@Repository
public interface IndexRepository extends JpaRepository<Index, Integer> {

    List<Index> findByPageId(Page page);

    List<Index> findByLemmaId(Lemma lemma);

    Index findByPageIdAndLemmaId(Page page, Lemma lemma);

    @Modifying
    @Transactional
    void deleteByPageId(Page page);

    @Modifying
    @Transactional
    void deleteAllByLemmaId(Lemma lemma);

    @Query(value = "SELECT CONCAT(i.page_id, '_', i.lemma_id) FROM word_index i WHERE i.page_id IN ?1", nativeQuery = true)
    Set<String> getIndexesByListPage(List<Integer> listId);
}
