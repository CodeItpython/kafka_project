import hudson.plugins.git.BranchSpec
import hudson.plugins.git.GitSCM
import hudson.plugins.git.UserRemoteConfig
import hudson.model.Result
import hudson.triggers.SCMTrigger
import jenkins.model.Jenkins
import jenkins.triggers.ReverseBuildTrigger
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jenkinsci.plugins.workflow.job.properties.PipelineTriggersJobProperty

def jenkins = Jenkins.get()
def repoUrl = System.getenv('KAFKA_PROJECT_GIT_URL') ?: 'https://github.com/CodeItpython/kafka_project'
def ciBranchSpec = System.getenv('KAFKA_PROJECT_CI_BRANCH') ?: '*/main'
def deployBranchSpec = System.getenv('KAFKA_PROJECT_DEPLOY_BRANCH') ?: '*/main'
def mainBranchSpec = System.getenv('KAFKA_PROJECT_MAIN_BRANCH') ?: '*/main'
def pollSpec = System.getenv('KAFKA_PROJECT_POLL_SCM') ?: '* * * * *'

def configurePipeline = { String jobName, String scriptPath, String branchSpec, String upstreamJobName = null ->
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
    def triggers = [new SCMTrigger(pollSpec)]
    if (upstreamJobName != null) {
        triggers.add(new ReverseBuildTrigger(upstreamJobName, Result.SUCCESS))
    }

    def triggerProperty = new PipelineTriggersJobProperty(triggers)
    job.addProperty(triggerProperty)
    if (triggerProperty.metaClass.respondsTo(triggerProperty, 'startTriggers', Boolean.TYPE)) {
        triggerProperty.startTriggers(true)
    }

    job.save()
    println("Configured ${jobName} from ${scriptPath}, branch ${branchSpec}, Poll SCM ${pollSpec}")
}

configurePipeline('kafka-chat-ci', 'Jenkinsfile', ciBranchSpec)
configurePipeline('kafka-chat-main-ci', 'Jenkinsfile', mainBranchSpec)
configurePipeline('kafka-chat-local-deploy', 'Jenkinsfile.local-deploy', deployBranchSpec, 'kafka-chat-main-ci')
jenkins.save()
