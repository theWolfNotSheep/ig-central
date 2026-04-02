package co.uk.wolfnotsheep.platform.products.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("app_subscriptions")
public class Subscription {

    @Id
    private String id;

    @Indexed
    private String userId;

    @Indexed
    private String companyId;

    @Indexed
    private String productId;

    private String billingInterval;

    private String status = "ACTIVE";

    private Instant startDate;

    private Instant endDate;

    private Instant createdAt;

    public Subscription() {}

    // GETTERS
    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getCompanyId() { return companyId; }
    public String getProductId() { return productId; }
    public String getBillingInterval() { return billingInterval; }
    public String getStatus() { return status; }
    public Instant getStartDate() { return startDate; }
    public Instant getEndDate() { return endDate; }
    public Instant getCreatedAt() { return createdAt; }

    // SETTERS
    public void setId(String id) { this.id = id; }
    public void setUserId(String userId) { this.userId = userId; }
    public void setCompanyId(String companyId) { this.companyId = companyId; }
    public void setProductId(String productId) { this.productId = productId; }
    public void setBillingInterval(String billingInterval) { this.billingInterval = billingInterval; }
    public void setStatus(String status) { this.status = status; }
    public void setStartDate(Instant startDate) { this.startDate = startDate; }
    public void setEndDate(Instant endDate) { this.endDate = endDate; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
