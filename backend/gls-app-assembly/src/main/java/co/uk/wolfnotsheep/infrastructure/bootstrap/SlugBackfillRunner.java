package co.uk.wolfnotsheep.infrastructure.bootstrap;

import co.uk.wolfnotsheep.document.models.DocumentModel;
import co.uk.wolfnotsheep.document.repositories.DocumentRepository;
import co.uk.wolfnotsheep.document.util.SlugGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Order(10)
public class SlugBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SlugBackfillRunner.class);

    private final DocumentRepository documentRepository;

    public SlugBackfillRunner(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<DocumentModel> docs = documentRepository.findAll().stream()
                .filter(d -> d.getSlug() == null && d.getId() != null)
                .toList();

        if (docs.isEmpty()) return;

        int backfilled = 0;
        for (DocumentModel doc : docs) {
            doc.setSlug(SlugGenerator.generate(doc.getOriginalFileName(), doc.getId()));
            documentRepository.save(doc);
            backfilled++;
        }

        log.info("Backfilled slugs for {} existing documents", backfilled);
    }
}
