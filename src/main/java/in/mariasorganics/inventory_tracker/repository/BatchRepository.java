package in.mariasorganics.inventory_tracker.repository;

import in.mariasorganics.inventory_tracker.model.Batch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BatchRepository extends JpaRepository<Batch, Long> {
    
    Optional<Batch> findByBatchId(String batchId);
    
    List<Batch> findAllByOrderByInoculationDateDesc();
    
    List<Batch> findByStatusOrderByInoculationDateDesc(Batch.BatchStatus status);

    @Query("SELECT SUM(b.bagCount) FROM Batch b WHERE b.status = 'ACTIVE'")
    Long countActiveBags();
}
