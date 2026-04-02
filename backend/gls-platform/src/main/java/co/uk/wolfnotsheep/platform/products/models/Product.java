package co.uk.wolfnotsheep.platform.products.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@Document("app_products")
public class Product {

    @Id
    private String id;

    private String name;

    private String description;

    private List<String> roleIds = new ArrayList<>();

    private List<String> featureIds = new ArrayList<>();

    private String billingType;

    private Long monthlyPriceInPence;

    private Long annualPriceInPence;

    private String status = "ACTIVE";

    public Product() {}

    // GETTERS
    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public List<String> getRoleIds() { return roleIds; }
    public List<String> getFeatureIds() { return featureIds; }
    public String getBillingType() { return billingType; }
    public Long getMonthlyPriceInPence() { return monthlyPriceInPence; }
    public Long getAnnualPriceInPence() { return annualPriceInPence; }
    public String getStatus() { return status; }

    // SETTERS
    public void setId(String id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }
    public void setRoleIds(List<String> roleIds) { this.roleIds = roleIds; }
    public void setFeatureIds(List<String> featureIds) { this.featureIds = featureIds; }
    public void setBillingType(String billingType) { this.billingType = billingType; }
    public void setMonthlyPriceInPence(Long monthlyPriceInPence) { this.monthlyPriceInPence = monthlyPriceInPence; }
    public void setAnnualPriceInPence(Long annualPriceInPence) { this.annualPriceInPence = annualPriceInPence; }
    public void setStatus(String status) { this.status = status; }
}
