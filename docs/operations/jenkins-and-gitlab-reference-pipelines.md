# Jenkins and GitLab Reference Pipelines

`Jenkinsfile` and `.gitlab-ci.yml` are platform-neutral references for the same required verification
lanes as the GitHub authoritative matrix. They invoke repository Maven profiles and the approved
MOB-003 scripts; they do not reimplement build or test behavior in CI-specific helpers.

## Runner requirements

All workers are Linux amd64 and provide Git, a Java 25 JDK, and POSIX shell tools. Jenkins nodes use
the labels shown in `Jenkinsfile`; GitLab runners use the corresponding tags in `.gitlab-ci.yml`.

| Lane | Additional requirements |
|---|---|
| Runtime, browser, MCP | Java 25; browser lane permits Playwright to install OS packages |
| Containers | Docker Engine with Compose v2; non-production daemon; at least 32 GB RAM recommended |
| Android | Docker, Compose v2, PowerShell 7 (`pwsh`), readable/writable `/dev/kvm`, and the approved MOB-003 image-build prerequisites |

The examples require no proprietary runner. Operators provide and secure compatible agents/runners;
shared untrusted runners must not expose a Docker socket, KVM device, Maven settings, or credentials.
Container and Android work is bounded with Jenkins non-concurrent builds and GitLab resource groups.

## Cache and artifacts

Jenkins uses a workspace-local `.m2/repository`; GitLab caches the same path per project. Treat caches
as disposable performance data, never as release evidence. Configure Maven mirrors, proxies, and
repository credentials in an administrator-managed `settings.xml` or platform credential store.
Never put credential values in repository variables, job arguments, cache keys, logs, or artifacts.

Both references retain test reports for failed and successful attempts. GitLab retains ordinary
verification evidence for 30 days and Android evidence for 7 days. Jenkins retention is controlled
by the job's administrator policy; configure it to retain complete attempts for at least 30 days.

## Configuration and triggering

Jenkins should configure a Multibranch Pipeline pointed at the root `Jenkinsfile`. GitLab discovers
the root `.gitlab-ci.yml`. The GitLab reference runs for merge requests, schedules, and manually
started web pipelines. Configure Jenkins SCM triggers and schedules in Jenkins rather than embedding
server-specific webhook tokens in the repository.

Required GitLab tags are `linux`, `java25`, `docker`, `kvm`, and `pwsh`. Required Jenkins labels use
the same names. Do not silently remove a lane when infrastructure is unavailable; provision the
runner or treat the pipeline as not accepted. Optional Maven profiles remain explicit in every
command (`runtime-gate`, compatibility/architecture profiles, `containers`, and MCP profiles).

The runtime/architecture lane first runs the read-only documentation-version check. The final
GitLab stage runs only after every required job succeeds. Jenkins Declarative Pipeline
fails the run on any failed stage or cleanup assertion. Neither reference publishes a release,
modifies production, or binds a credential.

## Validation

Run the repository contract locally:

```text
javac -d target/devops-006-ci build-support/ci/ReferencePipelineContractTest.java
java -cp target/devops-006-ci ReferencePipelineContractTest
```

Use Jenkins' Declarative Pipeline linter against the controller that will execute the job and the
GitLab CI Lint API/UI in the destination project before enabling required checks. Those service-side
linters depend on installed Jenkins plugins and GitLab runner/project configuration and cannot be
fully replaced by repository parsing.
