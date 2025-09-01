package com.outreachly.outreachly;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    // @Autowired
    // private TestEntityRepository testEntityRepository;

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }

    @GetMapping("/")
    public ResponseEntity<String> root() {
        return ResponseEntity.ok("Outreachly API is running");
    }

    // @GetMapping("/db-test")
    // public ResponseEntity<String> testDatabase() {
    // try {
    // long count = testEntityRepository.count();
    // return ResponseEntity.ok("Database connection successful. Test entities
    // count: " + count);
    // } catch (Exception e) {
    // return ResponseEntity.status(500).body("Database connection failed: " +
    // e.getMessage());
    // }
    // }

    // @PostMapping("/db-test")
    // public ResponseEntity<String> createTestEntity(@RequestParam String name) {
    // try {
    // TestEntity entity = new TestEntity(name);
    // TestEntity saved = testEntityRepository.save(entity);
    // return ResponseEntity.ok("Created test entity with ID: " + saved.getId());
    // } catch (Exception e) {
    // return ResponseEntity.status(500).body("Failed to create test entity: " +
    // e.getMessage());
    // }
    // }
}
