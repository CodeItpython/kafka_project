import hudson.plugins.git.BranchSpec
import hudson.plugins.git.GitSCM
import hudson.plugins.git.UserRemoteConfig
import hudson.triggers.SCMTrigger
import jenkins.model.Jenkins
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jenkinsci.plugins.workflow.job.properties.PipelineTriggersJobProperty

def jenkins = Jenkins.get()
def repoUrl = System.getenv('KAFKA_PROJECT_GIT_URL') ?: 'https://github.com/CodeItpython/kafka_project'
def branchSpec = System.getenv('KAFKA_PROJECT_GIT_BRANCH') ?: '*/codex/gradle-k8s-setup'
def pollSpec = System.getenv('KAFKA_PROJECT_POLL_SCM') ?: 'H/1 * * * *'

def configurePipeline = { String jobName, String scriptPath ->
    def job = jenkins.getItemByFullName(jobName)
    if (job == null) {
        job = jenkins.createProject(WorkflowJob, jobName)
    }

    def scm = new GitSCM(
        [new UserRemoteConfig(repoUrl, null, null, null)],
        [new BranchSpec(branchSpec)],
        false,
        Collections.emptyList(),
        null,
        null,
        Collections.emptyList()
    )

    def definition = new CpsScmFlowDefinition(scm, scriptPath)
    definition.setLightweight(false)
    job.setDefinition(definition)

    job.removeProperty(PipelineTriggersJobProperty)
    def triggerProperty = new PipelineTriggersJobProperty([new SCMTrigger(pollSpec)])
    job.addProperty(triggerProperty)
    if (triggerProperty.metaClass.respondsTo(triggerProperty, 'startTriggers', Boolean.TYPE)) {
        triggerProperty.startTriggers(true)
    }

    job.save()
    println("Configured ${jobName} from ${scriptPath} with Poll SCM ${pollSpec}")
}

configurePipeline('kafka-chat-ci', 'Jenkinsfile')
configurePipeline('kafka-chat-local-deploy', 'Jenkinsfile.local-deploy')
jenkins.save()
