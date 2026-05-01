package co.uk.wolfnotsheep.document.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

/**
 * Maps an external directory group (Google Workspace, Entra ID)
 * to an internal IG Central role + clearance + taxonomy grants.
 * When a user logs in via an external provider, their group memberships
 * are checked against these mappings.
 */
@Document(collection = "directory_role_mappings")
public class DirectoryRoleMapping {

    @Id
    private String id;

    @Indexed
    private String directorySource; // GOOGLE, ENTRA_ID

    private String externalGroupName;
    private String externalGroupEmail; // e.g. compliance@company.com
    private String internalRoleKey;
    private int sensitivityClearanceLevel;
    private List<String> taxonomyGrantCategoryIds;
    private boolean active;

    public DirectoryRoleMapping() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDirectorySource() { return directorySource; }
    public void setDirectorySource(String directorySource) { this.directorySource = directorySource; }

    public String getExternalGroupName() { return externalGroupName; }
    public void setExternalGroupName(String externalGroupName) { this.externalGroupName = externalGroupName; }

    public String getExternalGroupEmail() { return externalGroupEmail; }
    public void setExternalGroupEmail(String externalGroupEmail) { this.externalGroupEmail = externalGroupEmail; }

    public String getInternalRoleKey() { return internalRoleKey; }
    public void setInternalRoleKey(String internalRoleKey) { this.internalRoleKey = internalRoleKey; }

    public int getSensitivityClearanceLevel() { return sensitivityClearanceLevel; }
    public void setSensitivityClearanceLevel(int sensitivityClearanceLevel) { this.sensitivityClearanceLevel = sensitivityClearanceLevel; }

    public List<String> getTaxonomyGrantCategoryIds() { return taxonomyGrantCategoryIds; }
    public void setTaxonomyGrantCategoryIds(List<String> taxonomyGrantCategoryIds) { this.taxonomyGrantCategoryIds = taxonomyGrantCategoryIds; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
