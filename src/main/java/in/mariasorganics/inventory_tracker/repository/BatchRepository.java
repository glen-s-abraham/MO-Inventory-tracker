package in.mariasorganics.inventory_tracker.repository;

import in.mariasorganics.inventory_tracker.model.Batch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    List<Batch> findByStatusOrderByInoculationDateAsc(Batch.BatchStatus status);

    Page<Batch> findByStatusOrderByInoculationDateDesc(Batch.BatchStatus status, Pageable pageable);

    Page<Batch> findByStatusOrderByCompletedAtDesc(Batch.BatchStatus status, Pageable pageable);

    @Query("SELECT SUM(b.bagCount) FROM Batch b WHERE b.status = 'ACTIVE'")
    Long countActiveBags();

    @Query("SELECT SUM(b.bagCount) FROM Batch b WHERE b.status = 'ACTIVE' AND b.targetExitDate <= :arrivalDate")
    Long sumBagCountByStatusAndTargetExitDateBefore(java.time.LocalDate arrivalDate);

    @Query("SELECT SUM(b.bagCount) FROM Batch b WHERE b.status = 'ACTIVE' AND b.targetExitDate BETWEEN :startDate AND :endDate")
    Long sumBagCountByStatusAndTargetExitDateBetween(java.time.LocalDate startDate, java.time.LocalDate endDate);
}
