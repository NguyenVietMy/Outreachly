package com.pulse.pulse.integrations.infrastructure.persistence;

import com.pulse.pulse.integrations.domain.WebhookDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID> {

    Optional<WebhookDelivery> findByProviderAndDeliveryId(String provider, String deliveryId);
}
