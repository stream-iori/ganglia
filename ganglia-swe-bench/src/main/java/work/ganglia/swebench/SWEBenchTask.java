package work.ganglia.swebench;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SWEBenchTask {
    @JsonProperty("instance_id")
    private String instanceId;

    @JsonProperty("repo")
    private String repo;

    @JsonProperty("base_commit")
    private String baseCommit;

    @JsonProperty("problem_statement")
    private String problemStatement;

    @JsonProperty("test_patch")
    private String testPatch;

    @JsonProperty("patch")
    private String patch;

    @JsonProperty("version")
    private String version;

    @JsonProperty("FAIL_TO_PASS")
    private String failToPass;

    @JsonProperty("PASS_TO_PASS")
    private String passToPass;

    public String getInstanceId() { return instanceId; }
    public void setInstanceId(String instanceId) { this.instanceId = instanceId; }

    public String getRepo() { return repo; }
    public void setRepo(String repo) { this.repo = repo; }

    public String getBaseCommit() { return baseCommit; }
    public void setBaseCommit(String baseCommit) { this.baseCommit = baseCommit; }

    public String getProblemStatement() { return problemStatement; }
    public void setProblemStatement(String problemStatement) { this.problemStatement = problemStatement; }

    public String getTestPatch() { return testPatch; }
    public void setTestPatch(String testPatch) { this.testPatch = testPatch; }

    public String getPatch() { return patch; }
    public void setPatch(String patch) { this.patch = patch; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getFailToPass() { return failToPass; }
    public void setFailToPass(String failToPass) { this.failToPass = failToPass; }

    public String getPassToPass() { return passToPass; }
    public void setPassToPass(String passToPass) { this.passToPass = passToPass; }
}
