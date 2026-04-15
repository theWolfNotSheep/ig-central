package co.uk.wolfnotsheep.hub.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

/**
 * A piece of legislation or regulation stored in the Hub.
 * Distributed within governance packs as LEGISLATION components.
 */
@Document(collection = "hub_legislation")
public class HubLegislation {

    @Id
    private String id;

    @Indexed(unique = true)
    private String key;

    private String name;
    private String shortName;
    private String jurisdiction;
    private String url;
    private String description;
    private boolean active;

    private List<String> relevantArticles = new ArrayList<>();

    public HubLegislation() {}

    // --- getters and setters ---

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getShortName() { return shortName; }
    public void setShortName(String shortName) { this.shortName = shortName; }

    public String getJurisdiction() { return jurisdiction; }
    public void setJurisdiction(String jurisdiction) { this.jurisdiction = jurisdiction; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public List<String> getRelevantArticles() { return relevantArticles; }
    public void setRelevantArticles(List<String> relevantArticles) { this.relevantArticles = relevantArticles; }
}
